package com.greatergoods.meapp.core.shared.utilities.browser

import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent

class CustomTabIntentBuilder(
    private val context: Context,
) {
    fun build(
        session: CustomTabsSession?,
        packageName: String?,
        url: String,
        showBack: Boolean,
        showShare: Boolean,
    ): CustomTabsIntent {
        val builder = CustomTabsIntent.Builder(session)

        builder.setShowTitle(true)
        builder.setShareState(if (showShare) CustomTabsIntent.SHARE_STATE_ON else CustomTabsIntent.SHARE_STATE_OFF)

        val intent = builder.build()
        intent.intent.setPackage(packageName)
        intent.intent.putExtra(Intent.EXTRA_REFERRER, "android-app://${context.packageName}".toUri())
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return intent
    }
}
