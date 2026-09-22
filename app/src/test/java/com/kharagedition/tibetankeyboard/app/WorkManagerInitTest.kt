package com.kharagedition.tibetankeyboard.app

import androidx.work.Configuration
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the fix for the launch crash on SDK-34 ROMs that lack `JobScheduler.forNamespace`
 * (StreakReminderWorker.schedule's KDoc explains the crash itself).
 *
 * The fix has two halves that must stay together, so both are asserted here:
 *  1. the manifest strips `WorkManagerInitializer` from `androidx.startup.InitializationProvider`;
 *  2. [TibetanKeyboardApp] implements [Configuration.Provider] so WorkManager can still come up
 *     on demand — without it, `WorkManager.getInstance()` throws IllegalStateException instead.
 *
 * Reading the manifest as text (like KeyboardLayoutTest reads res/xml) keeps this a pure JVM test.
 */
class WorkManagerInitTest {

    private fun manifest(): File =
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found (cwd=${File("").absolutePath})")

    @Test
    fun workManagerAutoInitializer_isRemovedFromStartupProvider() {
        val text = manifest().readText()
        assertTrue(
            "AndroidManifest must declare androidx.startup.InitializationProvider to override it",
            text.contains("androidx.startup.InitializationProvider"),
        )
        val initializer = text.substringAfter("androidx.work.WorkManagerInitializer", "")
        assertTrue(
            "androidx.work.WorkManagerInitializer must stay removed from androidx.startup — " +
                "re-enabling it crashes process launch on ROMs without JobScheduler.forNamespace",
            initializer.substringBefore("</provider>").contains("tools:node=\"remove\""),
        )
    }

    @Test
    fun application_providesWorkManagerConfiguration_soOnDemandInitStillWorks() {
        assertTrue(
            "TibetanKeyboardApp must implement Configuration.Provider: the manifest disables " +
                "WorkManager's auto-initializer, so on-demand init is the only way it comes up",
            Configuration.Provider::class.java.isAssignableFrom(TibetanKeyboardApp::class.java),
        )
    }
}
