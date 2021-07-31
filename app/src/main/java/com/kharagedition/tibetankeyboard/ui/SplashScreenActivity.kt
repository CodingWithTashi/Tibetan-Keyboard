package com.kharagedition.tibetankeyboard.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.google.android.gms.ads.MobileAds
import com.kharagedition.tibetankeyboard.HomeActivity
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.databinding.ActivitySplashScreenBinding

class SplashScreenActivity : AppCompatActivity() {
    lateinit var activitySplashScreenBinding : ActivitySplashScreenBinding
    lateinit var topAnimation : Animation
    lateinit var bottomAnimation: Animation
    companion object {
        private var  SECOND : Long = 1500
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activitySplashScreenBinding = ActivitySplashScreenBinding.inflate(layoutInflater);
        setContentView(activitySplashScreenBinding.root)
        MobileAds.initialize(this) {}

        topAnimation = AnimationUtils.loadAnimation(this,R.anim.top_animation)
        bottomAnimation = AnimationUtils.loadAnimation(this,R.anim.bottom_animation)

        activitySplashScreenBinding.splashImage.animation =topAnimation
        activitySplashScreenBinding.splashText.animation = bottomAnimation
        activitySplashScreenBinding.authorText.animation = bottomAnimation
        setVersion()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        },SECOND)
    }

    private fun setVersion() {
        try {
            val versionName: String = this.packageManager
                .getPackageInfo(this.packageName, 0).versionName
            activitySplashScreenBinding.version.text = "Version: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
}