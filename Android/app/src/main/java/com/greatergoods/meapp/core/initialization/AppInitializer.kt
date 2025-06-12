package com.greatergoods.meapp.core.initialization

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.core.shared.utilities.logging.ILogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class AppInitializer
    @Inject
    constructor(
        private val logger: ILogger,
    ) {
        private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var isInitialized = false

        fun initialize() {
            if (isInitialized) return

            initializationScope.launch {
                try {
                    initializeLogging()
                    // Add other initialization methods here
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e("AppInitializer", "Failed to initialize app", e)
                    throw e
                }
            }
        }

        private suspend fun initializeLogging() {
            try {
                // Initialize AppLog
                AppLog.initialize(logger)

                // Initialize LogRepository
                logger.initialize()

                AppLog.d("AppInitializer", "Logging system initialized successfully")
            } catch (e: Exception) {
                Log.e("AppInitializer", "Failed to initialize logging system", e)
                throw e
            }
        }

        // Add other initialization methods here
        // Example:
        // private suspend fun initializeAnalytics() { ... }
        // private suspend fun initializeCrashReporting() { ... }
        // private suspend fun initializeDatabase() { ... }
    }
