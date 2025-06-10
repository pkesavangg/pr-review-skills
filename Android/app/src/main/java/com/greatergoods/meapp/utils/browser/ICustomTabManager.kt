package com.greatergoods.meapp.utils.browser

import kotlinx.coroutines.flow.Flow

interface ICustomTabManager {
    suspend fun bindService(): Boolean
    fun unbind()
    fun openChromeTab(url: String)
    fun preloadUrl(url: String)
    fun subscribeChromeState() : Flow<ChromeTabState?>
}
