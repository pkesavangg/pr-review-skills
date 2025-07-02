package com.greatergoods.meapp.core.service.pushNotification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.greatergoods.meapp.MainActivity
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.enum.NotificationChannel
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.notification.NotificationService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Service for handling Firebase push notifications and forwarding them to the app UI.
 */
@AndroidEntryPoint
class PushNotificationService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PushNotificationService"
    }

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var appRepository: IAppRepository

    /**
     * Called when a new FCM token is generated. Override to handle token updates.
     * @param token The new FCM token.
     */
    override fun onNewToken(token: String) {
        AppLog.i(TAG, "New FCM token: $token")

        // Update the token and device info
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateFcmToken(token)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to update FCM token", e.toString())
            }
        }
    }

    /**
     * Updates the FCM token and device info on server.
     * @param newToken The new FCM token to update.
     */
    private suspend fun updateFcmToken(newToken: String) {
        try {
            // Get current token from device info service
            val currentToken = appRepository.getFcmToken()

            if (currentToken != newToken) {
                appRepository.setFcmToken(newToken)
                AppLog.d(TAG, "FCM token updated: $newToken")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check/update FCM token", e.toString())
        }
    }

    /**
     * Called when a push notification is received. Prepares and shows a notification with tap action.
     * @param message The received remote message.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                action = "ACTION_HANDLE_NOTIFICATION"
                putExtra("destination", message.data["destination"]) // send to Activity
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        notificationService.showTextWithTapAction(
            message.notification?.channelId ?: NotificationChannel.GENERAL,
            "PUSH_TEST",
            message.notification?.title ?: "Default Title",
            message.notification?.body ?: "You have a new message",
            pendingIntent,
        )
    }
}
