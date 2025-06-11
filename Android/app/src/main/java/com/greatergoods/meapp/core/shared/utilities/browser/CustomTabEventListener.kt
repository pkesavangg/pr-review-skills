package com.greatergoods.meapp.utils.browser

interface CustomTabEventListener {
    fun onTabShown()
    fun onTabHidden()
    fun onNavigationStarted()
    fun onNavigationFinished()
    fun onNavigationFailed()
}

