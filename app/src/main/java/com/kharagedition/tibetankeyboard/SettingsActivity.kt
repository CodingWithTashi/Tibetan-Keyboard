package com.kharagedition.tibetankeyboard

import android.os.Bundle
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.ads.*
import com.kharagedition.tibetankeyboard.databinding.SettingsActivityBinding
import com.kharagedition.tibetankeyboard.subscription.RevenueCatManager

class SettingsActivity : AppCompatActivity() {
    lateinit var settingBinding: SettingsActivityBinding
    var isPremiumUser = false;

    override fun onStart() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        setPremiumListener()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingBinding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(settingBinding.root)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initListener()
        val adRequest = AdRequest.Builder().build()
        loadBannerAds(adRequest)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initListener() {
        settingBinding.settingsToolbar.setNavigationOnClickListener{
            onBackPressed()
        }
        setPremiumListener();
    }

    private fun setPremiumListener() {
        RevenueCatManager.getInstance().refreshCustomerInfo()
        RevenueCatManager.getInstance().isPremiumUser.observeForever{ isPremium ->
            isPremiumUser = isPremium;
            if(isPremiumUser) {
                settingBinding.premiumIcon.visibility = VISIBLE
                settingBinding.bannerAd.visibility = GONE
            }else{
                settingBinding.premiumIcon.visibility = GONE
                settingBinding.bannerAd.visibility = VISIBLE
            }
        }
    }

    private fun loadBannerAds(adRequest: AdRequest) {

        settingBinding.bannerAd.loadAd(adRequest)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}