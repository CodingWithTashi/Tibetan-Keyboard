package com.kharagedition.tibetankeyboard

import android.os.Bundle
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.ads.*
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.databinding.SettingsActivityBinding
import com.kharagedition.tibetankeyboard.subscription.RevenueCatManager
import com.kharagedition.tibetankeyboard.util.showConfirmationDialog
import com.kharagedition.tibetankeyboard.util.showToast

class SettingsActivity : AppCompatActivity() {
    lateinit var settingBinding: SettingsActivityBinding
    var isPremiumUser = false;
    private lateinit var authManager: AuthManager

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
        authManager = AuthManager(this)
        if (!authManager.isUserAuthenticated()) {
            settingBinding.logoutBtn.visibility = GONE
        }else{
            settingBinding.logoutBtn.visibility = VISIBLE
        }
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
        settingBinding.logoutBtn.setOnClickListener {
            showConfirmationDialog(
                title = "Sign Out",
                message = "Are you sure you want to sign out?",
                positiveText = "Sign Out",
                onPositive = { signOut() }
            )
        }
        setPremiumListener();
    }

    private fun signOut() {
        authManager.signOut {
            authManager.redirectToLogin()
            showToast("Signed out successfully")
        }
    }

    private fun setPremiumListener() {
        RevenueCatManager.getInstance().refreshCustomerInfo()
        RevenueCatManager.getInstance().isPremiumUser.observeForever{ isPremium ->
            isPremiumUser = isPremium;
            if(authManager.isUserAuthenticated() && isPremiumUser) {
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