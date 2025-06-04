package com.greatergoods.meapp

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.core.shared.utilities.DatabaseLoggingTree
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.domain.repository.ILogRepository
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

        // Initialize logging system and get session ID
        applicationScope.launch {
            try {
                logRepository.initialize()

                // Initialize database logging with the session ID from repository
                logRepository.getSessionId()?.let { sessionId ->
                    DatabaseLoggingTree(logDao, "default", sessionId)
                    AppLog.d("MeAppApplication", "Database logging initialized with session ID: $sessionId")
                } ?: run {
                    AppLog.e("MeAppApplication", "Failed to initialize database logging: No session ID available")
                }
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
