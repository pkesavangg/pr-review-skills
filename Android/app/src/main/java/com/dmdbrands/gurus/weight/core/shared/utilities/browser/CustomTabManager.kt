package com.dmdbrands.gurus.weight.core.shared.utilities.browser

import androidx.browser.customtabs.CustomTabsCallback
import androidx.core.net.toUri
import com.dmdbrands.gurus.weight.core.shared.utilities.webview.WebViewLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context
import android.os.Bundle

sealed class ChromeTabState {
    object Idle : ChromeTabState()

    object TabShown : ChromeTabState()

    object TabHidden : ChromeTabState()

    object Loading : ChromeTabState()

    object Finished : ChromeTabState()

    data class Failed(
        val error: Throwable?,
    ) : ChromeTabState()
}

class CustomTabManager
    @Inject
    constructor(
        private val context: Context,
    ) : ICustomTabManager {
        private var navigationEvent = MutableStateFlow<ChromeTabState?>(null)
        private val packageResolver = CustomTabPackageResolver(context)
        private val callback =
            object : CustomTabsCallback() {
                override fun onNavigationEvent(
                    event: Int,
                    extras: Bundle?,
                ) {
                    when (event) {
                        NAVIGATION_STARTED -> {
                            navigationEvent.value = ChromeTabState.Loading
                        }

                        NAVIGATION_FINISHED -> {
                            navigationEvent.value = ChromeTabState.Finished
                        }

                        TAB_HIDDEN -> {
                            navigationEvent.value = ChromeTabState.TabHidden
                        }

                        TAB_SHOWN -> {
                            navigationEvent.value = ChromeTabState.TabShown
                        }

                        NAVIGATION_FAILED -> {
                            navigationEvent.value = ChromeTabState.Failed(Throwable("Navigation Failed"))
                        }

                        else -> super.onNavigationEvent(event, extras)
                    }
                }
            }

        private var binder: CustomTabServiceBinder? = null
        private var intentBuilder: CustomTabIntentBuilder =
            CustomTabIntentBuilder(context)

        private var packageName: String? = null

        override suspend fun bindService(): Boolean =
            withContext(Dispatchers.IO) {
                packageName = packageResolver.resolve()
                if (packageName == null) return@withContext false
                binder =
                    CustomTabServiceBinder(context, packageName!!, callback).also {
                        it.bind()
                    }
                return@withContext true
            }

        override fun unbind() {
            binder?.unbind()
        }

        override fun preloadUrl(url: String) {
            binder?.session?.mayLaunchUrl(url.toUri(), null, null)
        }

        fun launchUrl(
            url: String,
            showBack: Boolean = false,
            showShare: Boolean = false,
        ) {
            if (url.isBlank()) {
                navigationEvent.value = ChromeTabState.Failed(Throwable("URL is empty or invalid"))
                return
            }
            val uri = url.toUri()
            try {
                if (binder?.session != null && packageName != null) {
                    val intent = intentBuilder.build(binder!!.session, packageName, url, showBack, showShare)
                    context.let {
                        intent.launchUrl(it, uri)
                    }
                    return
                }
            } catch (e: Exception) {
            }

            // Fallback
            try {
                WebViewLauncher.launch(context, url)
            } catch (e: Exception) {
            }
        }

        override fun openChromeTab(url: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val isBound = bindService()
                delay(300)
                if (isBound) {
                    launchUrl(url)
                }
            }
        }

        override fun subscribeChromeState(): Flow<ChromeTabState?> = navigationEvent.asStateFlow()

        companion object {
            private const val TAG = "CustomTabManager"
        }
    }
