package com.greatergoods.meapp.core.shared.utilities.webview

import androidx.core.net.toUri
import timber.log.Timber
import android.content.Context
import android.content.Intent

/**
 * Utility class for launching the in-app WebView.
 * Provides methods to safely open URLs in the WebView.
 */
object WebViewLauncher {
    /**
     * Launches the WebView activity with the given URL.
     * Validates the URL before launching.
     *
     * @param context The context to launch the activity from
     * @param url The URL to load in the WebView
     * @return true if the WebView was launched successfully, false otherwise
     */
    fun launch(
        context: Context,
        url: String,
    ): Boolean {
        return try {
            if (!isValidUrl(url)) {
                Timber.Forest.e("Invalid URL: $url")
                return false
            }

            val intent =
                Intent(context, InAppWebViewActivity::class.java).apply {
                    putExtra(InAppWebViewActivity.EXTRA_URL, url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to launch WebView")
            false
        }
    }

    /**
     * Validates if the given URL is safe to open in the WebView.
     * Only allows http and https URLs.
     *
     * @param url The URL to validate
     * @return true if the URL is valid and safe, false otherwise
     */
    private fun isValidUrl(url: String): Boolean =
        try {
            val uri = url.toUri()
            uri.scheme == "http" || uri.scheme == "https"
        } catch (e: Exception) {
            false
        }
}
