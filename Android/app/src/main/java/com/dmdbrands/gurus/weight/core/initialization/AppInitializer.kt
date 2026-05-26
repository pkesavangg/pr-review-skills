package com.dmdbrands.gurus.weight.core.initialization

import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.ILogger
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import com.dmdbrands.gurus.weight.BuildConfig

@Singleton
class AppInitializer
    @Inject
    constructor(
        private val logger: ILogger,
        private val crashReportingService: ICrashReportingService,
        @ApplicationScope private val appScope: CoroutineScope,
    ) {
        private var isInitialized = false

        fun initialize() {
            if (isInitialized) return

            appScope.launch {
                try {
                    initializeLogging()
                    initializeCrashReporting()
                    isInitialized = true
                } catch (e: Exception) {
                    AppLog.e("AppInitializer", "Failed to initialize app", e)
                    throw e
                }
            }
        }

        private suspend fun initializeLogging() {
            try {
                // Initialize Timber with DebugTree for logcat logging
                if (BuildConfig.DEBUG) {
                    Timber.plant(Timber.DebugTree())
                }

                // Initialize AppLog
                AppLog.initialize(logger)

                // Initialize LogRepository
                logger.initialize()

                // Bridge AppSync library logs into AppLog so scan diagnostics show up
                // in user-submitted debug bundles (MA-AppSync scan-hang investigation).
                AppSyncLogger.install(
                    object : AppSyncLogger.Sink {
                        override fun d(tag: String, message: String, data: String?) = AppLog.d(tag, message, data)
                        override fun i(tag: String, message: String, data: String?) = AppLog.i(tag, message, data)
                        override fun w(tag: String, message: String, data: String?) = AppLog.w(tag, message, data)
                        override fun e(tag: String, message: String, throwable: Throwable?) {
                            if (throwable != null) AppLog.e(tag, message, throwable) else AppLog.e(tag, message)
                        }
                    },
                )

                AppLog.d("AppInitializer", "Logging system initialized successfully")
            } catch (e: Exception) {
                AppLog.e("AppInitializer", "Failed to initialize logging system", e)
                throw e
            }
        }

        private fun initializeCrashReporting() {
            try {
                crashReportingService.initialize()
                AppLog.d("AppInitializer", "Crash reporting initialized successfully")
            } catch (e: Exception) {
                AppLog.e("AppInitializer", "Failed to initialize crash reporting", e)
            }
        }
    }
