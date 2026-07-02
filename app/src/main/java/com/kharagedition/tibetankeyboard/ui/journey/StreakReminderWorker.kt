package com.kharagedition.tibetankeyboard.ui.journey

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Evening check for the Journey loop. Runs once a day (~19:00 local, WorkManager may drift):
 *  - streak at risk (typed yesterday, not yet today) → "your streak ends at midnight" nudge;
 *  - Sundays → the weekly insight ("you typed N words this week"), once per week.
 *
 * Entirely offline: everything is read from [TypingStatsStore]; nothing is fetched or sent.
 * Both notifications respect the "Streak reminders" toggle in Settings.
 */
class StreakReminderWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(SettingsPrefs.KEY_STREAK_REMINDER, true)) return Result.success()
        if (!canNotify(context)) return Result.success()

        val stats = TypingStatsStore.getInstance(context)
        val today = stats.todayEpochDay()
        val streak = stats.streak()

        if (StreakLogic.isAtRisk(streak, today)) {
            notify(
                context,
                id = NOTIFICATION_ID_REMINDER,
                title = context.getString(R.string.journey_reminder_title, streak.lengthDays),
                body = context.getString(R.string.journey_reminder_body),
            )
        }

        val isSunday =
            Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val wordsThisWeek = stats.wordsThisWeek(today)
        if (isSunday && wordsThisWeek > 0 && stats.markWeeklyNotified(today)) {
            notify(
                context,
                id = NOTIFICATION_ID_WEEKLY,
                title = context.getString(R.string.journey_weekly_title),
                body = context.getString(R.string.journey_weekly_body, wordsThisWeek),
            )
        }
        return Result.success()
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.journey_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.journey_channel_desc) }
            )
        }

        val intent = Intent(context, JourneyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(JourneyActivity.EXTRA_SOURCE, AppAnalytics.JourneySource.NOTIFICATION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_filled)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "journey_channel"
        private const val WORK_NAME = "journey_streak_reminder"
        private const val NOTIFICATION_ID_REMINDER = 4101
        private const val NOTIFICATION_ID_WEEKLY = 4102
        private const val TARGET_HOUR = 19 // ~7pm local — evening, before the streak dies

        /**
         * Schedule the daily evening check. Idempotent (KEEP) — safe to call from
         * Application.onCreate on every process start, including the IME's.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(
                StreakReminderWorker::class.java, 1, TimeUnit.DAYS,
            )
                .setInitialDelay(millisUntilNextTargetHour(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        private fun millisUntilNextTargetHour(): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now) || equals(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
