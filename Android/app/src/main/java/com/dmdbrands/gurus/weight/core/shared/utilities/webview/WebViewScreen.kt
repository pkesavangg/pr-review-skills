package com.dmdbrands.gurus.weight.core.shared.utilities.webview

import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onClose: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text("Web View") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            },
            actions = {
                // Back button
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint =
                            if (canGoBack) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    )
                }

                // Forward button
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint =
                            if (canGoForward) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    )
                }
            },
        )

        // Error message
        errorMessage?.let { message ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // WebView
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        setupWebView()
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.webViewClient =
                        object : SafeWebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                errorMessage = null
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            @RequiresApi(Build.VERSION_CODES.M)
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                errorMessage = "Error loading page: ${error?.description}"
                                isLoading = false
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?,
                            ) {
                                errorMessage = "SSL Error: ${error?.primaryError}"
                                handler?.cancel()
                            }
                        }
                },
            )

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setupWebView() {
    settings.apply {
        // Enable JavaScript (if needed)
        javaScriptEnabled = true

        // Disable file access
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false

        // Disable geolocation
        setGeolocationEnabled(false)

        // Disable database storage
        databaseEnabled = false

        // Disable DOM storage
        domStorageEnabled = false

        // Disable cache
        cacheMode = WebSettings.LOAD_NO_CACHE

        // Disable zoom
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }

    // Block downloads
    setDownloadListener { url, _, _, _, _ ->
        AppLog.w("WebViewScreen", "Download blocked: $url")
    }

    // Block new windows
    webChromeClient =
        object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                resultMsg?.sendToTarget()
                return false
            }
        }
}
