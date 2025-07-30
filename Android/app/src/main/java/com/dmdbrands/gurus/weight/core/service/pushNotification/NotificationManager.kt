package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.core.config.NotificationConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.greatergoods.notification.NotificationService
import com.greatergoods.notification.model.BuilderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
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
    private val appRepository: IAppRepository,
) {
    init {
        createChannels()
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
                AppLog.d("NotificationManager", "FCM Token: $token")
                CoroutineScope(IO).launch {
                    appRepository.setFcmToken(token)
                }
                // TODO: Here, you can handle the token as needed (e.g., send it to your server)
            },
            onError = { exception ->
                AppLog.e("NotificationManager", "Fetching FCM token failed", exception.toString())
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
                AppLog.d("NotificationManager", msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onError = { exception ->
                val msg = "Subscribe failed"
                AppLog.e("NotificationManager", msg, exception.toString())
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
