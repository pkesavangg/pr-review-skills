package com.greatergoods.meapp

import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.core.logging.AppLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Application

/**
 * Application class for MeApp.
 * Handles application-level initialization and configuration.
 */
@HiltAndroidApp
class MeAppApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject
    lateinit var logRepository: ILogRepository

    @Inject
    lateinit var logDao: LogDao

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize AppLog for dual logging
        AppLog.logRepository = logRepository
        // Initialize logging system
        applicationScope.launch {
            try {
                logRepository.initialize()
                AppLog.d("MeAppApplication", "Logging system initialized")
            } catch (e: Exception) {
                AppLog.e("MeAppApplication", "Failed to initialize logging system", e.toString())
            }
        }
    }

    companion object {
        lateinit var instance: MeAppApplication
            private set
    }
}
