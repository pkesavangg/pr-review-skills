package com.greatergoods.meapp

import com.greatergoods.meapp.core.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import android.app.Application

/**
 * Application class for MeApp.
 * Handles application-level initialization and configuration.
 */
@HiltAndroidApp
class MeAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize services needed for the app
        initService()
    }

    /**
     * Initialize services needed for the app at app startup
     */
    private fun initService() {
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val initializer = entryPoint.appInitializer()
        initializer.initialize()
    }
}
