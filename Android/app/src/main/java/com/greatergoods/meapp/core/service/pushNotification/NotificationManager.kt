package com.greatergoods.meapp.core.service.pushNotification

import com.greatergoods.meapp.R
import com.greatergoods.meapp.core.config.NotificationConfig
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.notification.NotificationService
import com.greatergoods.notification.model.BuilderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
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
    private val appRepository: IAppRepository
) {
    init {
        createChannels()
        fetchFCMToken()
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
     * Requests the FCM token using NotificationService and logs/shows result.
     */
    private fun fetchFCMToken() {
        notificationService.fetchFCMToken(
            onSuccess = { token ->
                Timber.Forest.d("FCM Token: $token")
                CoroutineScope(IO).launch {
                    appRepository.setFcmToken(token)
                }
                // TODO: Here, you can handle the token as needed (e.g., send it to your server)
            },
            onError = { exception ->
                Timber.Forest.e(exception, "Fetching FCM token failed")
            },
        )
    }

    /**
     * Subscribes the device to the "meApp" topic for FCM notifications using NotificationService.
     * Shows a Toast and logs the result.
     */
    fun subscribeToMeAppTopic() {
        notificationService.subscribeToTopic(
            topic = "meApp",
            onSuccess = {
                val msg = "Subscribed"
                Timber.Forest.d(msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onError = { exception ->
                val msg = "Subscribe failed"
                Timber.Forest.e(exception, msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
        )
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
