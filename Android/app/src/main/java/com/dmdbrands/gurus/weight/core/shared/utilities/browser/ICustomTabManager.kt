package com.dmdbrands.gurus.weight.core.shared.utilities.browser

import kotlinx.coroutines.flow.Flow

interface ICustomTabManager {
    suspend fun bindService(): Boolean

    fun unbind()

    fun openChromeTab(url: String)

    fun preloadUrl(url: String)

    fun subscribeChromeState(): Flow<ChromeTabState?>
}
