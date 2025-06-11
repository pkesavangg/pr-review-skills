package com.greatergoods.meapp.core.shared.utilities.browser

import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent

class CustomTabPackageResolver(
    private val context: Context,
) {
    fun resolve(): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
        val resolvedActivities = pm.queryIntentActivities(intent, 0)

        return resolvedActivities
            .firstOrNull { info ->
                val serviceIntent = Intent(ACTION_CUSTOM_TABS_CONNECTION)
                serviceIntent.setPackage(info.activityInfo.packageName)
                pm.resolveService(serviceIntent, 0) != null
            }?.activityInfo
            ?.packageName
    }
}
