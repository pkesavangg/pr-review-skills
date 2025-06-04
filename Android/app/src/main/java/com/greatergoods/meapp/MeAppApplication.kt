package com.greatergoods.meapp

import com.google.firebase.BuildConfig
import com.greatergoods.meapp.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.greatergoods.notification.NotificationService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject
import android.app.Application

/**
 * Application class for MeApp. Initializes notification channels and retrieves the FCM token on startup.
 */
@HiltAndroidApp
class MeAppApplication : Application() {
    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var notificationManager: GGNotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    companion object {
        lateinit var instance: MeAppApplication
            private set
    }
}
