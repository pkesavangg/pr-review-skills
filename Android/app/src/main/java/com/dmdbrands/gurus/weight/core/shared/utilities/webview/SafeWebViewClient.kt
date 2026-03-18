package com.dmdbrands.gurus.weight.core.shared.utilities.webview

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog

open class SafeWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        val scheme = url.scheme
        val host = url.host ?: ""

        if (!isUrlAllowed(scheme, host)) {
            AppLog.w(TAG, "Blocked URL: $scheme://$host")
            return true
        }

        return false
    }

    companion object {
        private const val TAG = "SafeWebViewClient"

        val ALLOWED_DOMAINS = setOf(
            "weightgurus.com",
            "greatergoods.com",
            "accounts.google.com",
            "appleid.apple.com",
        )

        fun isUrlAllowed(scheme: String?, host: String): Boolean {
            if (scheme != "https") return false
            return isAllowedDomain(host)
        }

        fun isAllowedDomain(host: String): Boolean =
            ALLOWED_DOMAINS.any { allowed ->
                host == allowed || host.endsWith(".$allowed")
            }
    }
}
