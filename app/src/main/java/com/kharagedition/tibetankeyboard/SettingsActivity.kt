package com.kharagedition.tibetankeyboard

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.ads.*
import com.kharagedition.tibetankeyboard.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {
    lateinit var settingBinding: SettingsActivityBinding


    override fun onStart() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onStart()
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
        val adRequest = AdRequest.Builder().build()
        loadBannerAds(adRequest)
        initListener()
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