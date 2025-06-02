package com.greatergoods.meapp

import android.app.Application
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.google.firebase.BuildConfig
import com.greatergoods.meapp.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.greatergoods.notification.NotificationService
import com.greatergoods.meapp.core.shared.utilities.DatabaseLoggingTree
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

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
    lateinit var notificationManager: GGNotificationManager

    @Inject
    lateinit var logDao: LogDao

    override fun onCreate() {
        super.onCreate()
        
        // Initialize debug logging first
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize logging system and get session ID
        applicationScope.launch {
            logRepository.initialize()
            
            // Plant database logging tree with the session ID from repository
            logRepository.getSessionId()?.let { sessionId ->
                Timber.plant(DatabaseLoggingTree(logDao, "default", sessionId))
                Timber.d("Database logging tree planted with session ID: $sessionId")
            } ?: run {
                Timber.e("Failed to plant database logging tree: No session ID available")
            }
        }
    }
}
