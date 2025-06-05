package com.greatergoods.meapp.utils.browser

import android.content.Context

interface CustomTabLauncher {
    fun openUrl(
        url: String,
        context: Context,
        showBack: Boolean = false,
        showShare: Boolean = true
    )

    fun preloadUrl(url: String)
}
