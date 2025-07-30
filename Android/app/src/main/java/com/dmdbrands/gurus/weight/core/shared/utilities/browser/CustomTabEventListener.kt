package com.dmdbrands.gurus.weight.core.shared.utilities.browser

interface CustomTabEventListener {
    fun onTabShown()

    fun onTabHidden()

    fun onNavigationStarted()

    fun onNavigationFinished()

    fun onNavigationFailed()
}
