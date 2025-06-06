package com.greatergoods.meapp.utils.browser

import androidx.browser.customtabs.CustomTabsCallback
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.greatergoods.meapp.utils.WebViewLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustomTabManager(
    private val context: Context,
    private val eventListener: CustomTabEventListener? = null
) : CustomTabLauncher {

    private val packageResolver = CustomTabPackageResolver(context)
    private val callback = object : CustomTabsCallback() {
        override fun onNavigationEvent(event: Int, extras: Bundle?) {
            when (event) {
                NAVIGATION_STARTED -> eventListener?.onNavigationStarted()
                NAVIGATION_FINISHED -> eventListener?.onNavigationFinished()
                NAVIGATION_FAILED -> eventListener?.onNavigationFailed()
                TAB_SHOWN -> eventListener?.onTabShown()
                TAB_HIDDEN -> eventListener?.onTabHidden()
            }
        }
    }

    private var binder: CustomTabServiceBinder? = null
    private var intentBuilder: CustomTabIntentBuilder =
        CustomTabIntentBuilder(context)

    private var packageName: String? = null

    fun bind() {
        packageName = packageResolver.resolve()
        if (packageName == null) return

        binder = CustomTabServiceBinder(context, packageName!!, callback).also {
            it.bind()
        }
    }
//TODO:Need to remove
    suspend fun bindService(): Boolean = withContext(Dispatchers.IO) {
        packageName = packageResolver.resolve()
        if (packageName == null) return@withContext false

        binder = CustomTabServiceBinder(context, packageName!!, callback).also {
            it.bind()
        }
        return@withContext true
    }


    fun unbind() {
        binder?.unbind()
    }

    override fun preloadUrl(url: String) {
        binder?.session?.mayLaunchUrl(url.toUri(), null, null)
    }

    override fun openUrl(url: String, context: Context, showBack: Boolean, showShare: Boolean) {
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
            Log.e(TAG, "Failed to launch Custom Tab: ${e.message}")
        }

        // Fallback
        try {
            WebViewLauncher.launch(context, url)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CustomTabManager"
    }
}


