package com.kharagedition.tibetankeyboard.util

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.databinding.BottomSheetLayoutBinding

class BottomSheetDialog : BottomSheetDialogFragment(),View.OnClickListener {
    lateinit var bottomSheetLayoutBinding: BottomSheetLayoutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bottomSheetLayoutBinding = BottomSheetLayoutBinding.inflate(layoutInflater,container,false);

        bottomSheetLayoutBinding.aboutToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_ios_24);
        bottomSheetLayoutBinding.aboutToolbar.setNavigationOnClickListener {
            dismiss()
        }
        initAdmobAds();
        bottomSheetLayoutBinding.share.setOnClickListener(this)
        bottomSheetLayoutBinding.rate.setOnClickListener(this)
        bottomSheetLayoutBinding.gmail.setOnClickListener(this)
        bottomSheetLayoutBinding.github.setOnClickListener(this)
        bottomSheetLayoutBinding.instagram.setOnClickListener(this)
        bottomSheetLayoutBinding.facebook.setOnClickListener(this)
        bottomSheetLayoutBinding.downloadCalendar.setOnClickListener(this)
        bottomSheetLayoutBinding.downloadPrayer.setOnClickListener(this)
        return bottomSheetLayoutBinding.root;
    }
    private fun initAdmobAds() {
        if(context!=null){
            val adRequest = AdRequest.Builder().build()
            bottomSheetLayoutBinding.adView.loadAd(adRequest)
            bottomSheetLayoutBinding.adView.adListener = object: AdListener() {
                override fun onAdLoaded() {
                    Log.e("TAG", "onAdLoaded: ")
                    bottomSheetLayoutBinding.bannerAdsLayout.visibility = VISIBLE;
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("TAG", "onAdFailedToLoad: " + adError.message)
                }

                override fun onAdOpened() {
                    Log.e("TAG", "onAdLoaded: ")
                }

                override fun onAdClicked() {
                    // Code to be executed when the user clicks on an ad.
                }

                override fun onAdClosed() {
                    Log.e("TAG", "onAdLoaded: ")
                }
            }
        }


    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.back_icon -> {
                dismiss()
            }
            R.id.share -> {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                    var shareMessage = "\nCheck out this Dictionary Application.\n\n"
                    shareMessage =
                        """
                        ${shareMessage}https://play.google.com/store/apps/details?id=com.kharagedition.tibetandictionary
                        """.trimIndent()
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
                    startActivity(Intent.createChooser(shareIntent, "choose one"))
                } catch (e: Exception) {
                    //e.toString()
                }
            }
            R.id.rate -> {
                openView(CommonUtils.PLAY_STORE_URL)
            }
            R.id.gmail -> {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.type = "text/plain"
                intent.data = Uri.parse("mailto:developer.kharag@gmail.com")
                //intent.putExtra(Intent.EXTRA_EMAIL, "developer.kharag@gmail.com")
                intent.putExtra(Intent.EXTRA_SUBJECT, "Prayer Request")
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
            R.id.github -> {
                openView(CommonUtils.GITHUB_URL)
            }
            R.id.instagram -> {
                openView(CommonUtils.INSTAGRAM_URL)
            }
            R.id.facebook -> {
                openView(CommonUtils.FACEBOOK_URL)
            }
            R.id.download_prayer -> {
                openView(CommonUtils.PLAY_STORE_DICTIONARY_URL)
            }
            R.id.download_calendar -> {
                openView(CommonUtils.PLAY_STORE_CALENDAR_URL)
            }
        }
    }
    private fun openView(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}