package com.kharagedition.tibetankeyboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.ads.*
import com.kharagedition.tibetankeyboard.databinding.ActivityHomeBinding
import com.kharagedition.tibetankeyboard.util.BottomSheetDialog
import com.kharagedition.tibetankeyboard.util.CommonUtils


class HomeActivity : InputMethodActivity() {
    lateinit var homeBinding: ActivityHomeBinding;
    override fun onResume() {
        checkKeyboardIsEnabledOrNot()

        super.onResume()
    }

    override fun onInputMethodPicked() {
        checkInputMethodEnableOrNot()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeBinding = ActivityHomeBinding.inflate(layoutInflater);
        setContentView(homeBinding.root)
        initAds();
        checkKeyboardIsEnabledOrNot()
        initClickListener()



    }

    private fun initClickListener() {
        homeBinding.enableKeyboardBtn.setOnClickListener {
            val enableIntent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            enableIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            this.startActivity(enableIntent)
        }
        homeBinding.inputMethodBtn.setOnClickListener {
            pickInput()
        }
        homeBinding.sharedCard.setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                var shareMessage = "\nCheck out this Keyboard Application.\n\n"
                shareMessage =
                        """
                        $shareMessage ${CommonUtils.PLAY_STORE_URL}
                        """.trimIndent()
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
                startActivity(Intent.createChooser(shareIntent, "choose one"))
            } catch (e: Exception) {
                //e.toString()
            }
        }
        homeBinding.rateCard.setOnClickListener {
            openView(CommonUtils.PLAY_STORE_URL)

        }
        homeBinding.moreCard.setOnClickListener {
            val sheet = BottomSheetDialog()
            sheet.show(this.supportFragmentManager, "ModalBottomSheet")
        }
        homeBinding.exitCard.setOnClickListener {
            finish();
        }
    }

    private fun openView(playStoreUrl: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl)))
    }
    private fun checkKeyboardIsEnabledOrNot() {
        val im = applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var list = im.enabledInputMethodList.toString()
        if(list.contains("com.kharagedition.tibetankeyboard")){
            homeBinding.messgaeLbl.text = "Vola! Now only one step left";
            homeBinding.enableKeyboardBtn.isEnabled = false
            homeBinding.inputMethodBtn.isEnabled = true;
            //check input method
            checkInputMethodEnableOrNot()

        }else{
            homeBinding.messgaeLbl.text = "Well done, Now only two step away!";
            homeBinding.enableKeyboardBtn.isEnabled = true;
            homeBinding.inputMethodBtn.isEnabled = false;
            homeBinding.gifCard.visibility = VISIBLE;
            Glide.with(this)
                    .load(R.drawable.keyboard)
                    .into(homeBinding.gitImage)
        }
        //homeBinding.enableKeyboardBtn.isEnabled = !list.contains("com.kharagedition.tibetankeyboard")
    }

    private fun checkInputMethodEnableOrNot() {
        var string = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD);
        if(string.contains("com.kharagedition.tibetankeyboard")){
            homeBinding.messgaeLbl.text = "Great, You are all setup and ready to use!";
            homeBinding.testField.visibility = VISIBLE;
            //homeBinding.inputMethodBtn.isEnabled = false;
            homeBinding.gifCard.visibility = GONE;

        }else{
            homeBinding.testField.visibility = GONE;
            homeBinding.gifCard.visibility = VISIBLE;
            Glide.with(this)
                    .load(R.drawable.input)
                    .into(homeBinding.gitImage)
            homeBinding.messgaeLbl.text = "Vola! Now only one step left";
            homeBinding.inputMethodBtn.isEnabled = true;

        }
        //homeBinding.inputMethodBtn.isEnabled = !string.contains("com.kharagedition.tibetankeyboard")
    }


    private fun initAds() {
        val adView = AdView(this)

        adView.adSize = AdSize.BANNER

        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"
        val adRequest = AdRequest.Builder().build()
        homeBinding.adView.loadAd(adRequest)
        homeBinding.adView.adListener = object: AdListener() {
            override fun onAdLoaded() {
                Log.e("TAG", "onAdLoaded: ", )
            }

            override fun onAdFailedToLoad(adError : LoadAdError) {
                Log.e("TAG", "onAdFailedToLoad: "+adError.message, )
            }

            override fun onAdOpened() {
                Log.e("TAG", "onAdLoaded: ", )
            }

            override fun onAdClicked() {
                // Code to be executed when the user clicks on an ad.
            }

            override fun onAdClosed() {
                Log.e("TAG", "onAdLoaded: ", )
            }
        }
    }
}