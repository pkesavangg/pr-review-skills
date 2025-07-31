package com.dmdbrands.gurus.weight.core.shared.utilities.webview

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import android.os.Bundle

class InAppWebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                WebViewScreen(url = url, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
