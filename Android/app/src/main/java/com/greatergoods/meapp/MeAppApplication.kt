package com.greatergoods.meapp

import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.greatergoods.meapp.core.service.pushNotification.ME_APP
import com.greatergoods.meapp.core.service.pushNotification.NotificationService
import com.greatergoods.notification.model.BuilderConfig
import com.greatergoods.notification.model.ChannelConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject
import android.app.Application
import android.app.NotificationManager
import android.widget.Toast

/**
 * Application class for MeApp. Initializes notification channels and retrieves the FCM token on startup.
 */
@HiltAndroidApp
class MeAppApplication : Application() {
    @Inject
    lateinit var notificationService: NotificationService

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createChannels()
        retrieveFCMToken()
    }

    private fun createChannels() {
        val channels =
            listOf(
                BuilderConfig(
                    ChannelConfig(
                        ME_APP,
                        "TEST",
                        NotificationManager.IMPORTANCE_HIGH,
                        "This is for testing purposes",
                    ),
                    smallIcon = R.drawable.ic_launcher_foreground,
                ),
            )

        notificationService.createInstance(channels)
    }

    private fun retrieveFCMToken() {
        FirebaseMessaging
            .getInstance()
            .token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Timber.d("FCM Token: $token")
                    // Here, you can handle the token as needed (e.g., send it to your server)
                } else {
                    Timber.e(task.exception, "Fetching FCM token failed")
                }
            }
    }

    private fun subscribeTopics() {
        Firebase.messaging
            .subscribeToTopic("meApp")
            .addOnCompleteListener { task ->
                var msg = "Subscribed"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Timber.d(msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }
    }
}
