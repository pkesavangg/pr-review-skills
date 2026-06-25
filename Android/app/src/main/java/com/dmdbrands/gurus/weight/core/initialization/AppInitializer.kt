package com.dmdbrands.gurus.weight.core.initialization

import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.ILogger
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
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
        private val deviceRepository: IDeviceRepository,
        @ApplicationScope private val appScope: CoroutineScope,
    ) {
        private var isInitialized = false

        fun initialize() {
            if (isInitialized) return

            appScope.launch {
                try {
                    initializeLogging()
                    initializeCrashReporting()
                    repairDeviceTypes()
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

                AppLog.d("AppInitializer", "Logging system initialized successfully")
            } catch (e: Exception) {
                AppLog.e("AppInitializer", "Failed to initialize logging system", e)
                throw e
            }
        }

        /**
         * One-time data repair: reconciles persisted device setup/protocol types with the
         * SKU's known setup type. Fixes legacy Ionic-migrated records that show the wrong
         * Scale Type (e.g. "AppSync" for the Bluetooth SKU 0375). Idempotent and best-effort —
         * never fails app startup. (MOB-204)
         */
        private suspend fun repairDeviceTypes() {
            try {
                val repaired = deviceRepository.repairDeviceTypesFromSku()
                AppLog.d("AppInitializer", "Device setup-type repair complete; repaired $repaired device(s)")
            } catch (e: Exception) {
                AppLog.e("AppInitializer", "Failed to repair device setup types", e)
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
