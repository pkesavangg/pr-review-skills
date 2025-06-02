package com.greatergoods.meapp.core.service.pushNotification

import com.google.firebase.messaging.FirebaseMessaging
import com.greatergoods.meapp.R
import com.greatergoods.meapp.core.config.NotificationConfig
import com.greatergoods.notification.model.BuilderConfig
import timber.log.Timber
import android.content.Context
import android.widget.Toast

/**
 * Manages notification-related operations such as creating channels, retrieving FCM tokens,
 * subscribing to topics, and generating notification channel configurations.
 *
 * @property context The application context used for system operations and Toasts.
 * @property notificationService The service responsible for creating notification channels.
 */
class NotificationManager(
    private val context: Context,
    private val notificationService: NotificationService,
) {
    init {
        createChannels()
        retrieveFCMToken()
    }

    /**
     * Creates notification channels using the provided NotificationService.
     * Channels are generated from the app's NotificationConfig.
     */
    private fun createChannels() {
        val channels = generateBuilderConfig()
        notificationService.createInstance(channels)
    }

    /**
     * Retrieves the Firebase Cloud Messaging (FCM) token for the device.
     * Logs the token or an error if retrieval fails.
     */
    private fun retrieveFCMToken() {
        FirebaseMessaging
            .getInstance()
            .token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Timber.Forest.d("FCM Token: $token")
                    // TODO: Here, you can handle the token as needed (e.g., send it to your server)
                } else {
                    Timber.Forest.e(task.exception, "Fetching FCM token failed")
                }
            }
    }

    /**
     * Subscribes the device to the "meApp" topic for FCM notifications.
     * Shows a Toast and logs the result.
     */
    private fun subscribeTopics() {
        FirebaseMessaging
            .getInstance()
            .subscribeToTopic("meApp")
            .addOnCompleteListener { task ->
                var msg = "Subscribed"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Timber.Forest.d(msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Generates a list of BuilderConfig objects for all notification channels defined in NotificationConfig.
     *
     * @return List of BuilderConfig for channel creation.
     */
    private fun generateBuilderConfig(): List<BuilderConfig> {
        var builderConfig = listOf<BuilderConfig>()
        builderConfig =
            NotificationConfig.NotificationChannels.map { it ->
                BuilderConfig(
                    it,
                    smallIcon = R.drawable.ic_launcher_foreground,
                )
            }
        return builderConfig
    }
}
