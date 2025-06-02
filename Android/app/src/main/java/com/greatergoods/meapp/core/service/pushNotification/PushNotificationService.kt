package com.greatergoods.meapp.core.service.pushNotification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.greatergoods.meapp.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import timber.log.Timber

const val ME_APP = "ME_APP"

/**
 * Service for handling Firebase push notifications and forwarding them to the app UI.
 */
@AndroidEntryPoint
class PushNotificationService : FirebaseMessagingService() {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var notificationService: NotificationService

    /**
     * Called when a new FCM token is generated. Override to handle token updates.
     * @param token The new FCM token.
     */
    override fun onNewToken(token: String) {
        Timber.i("New FCM token: $token")
        // Send token to server if needed
    }

    /**
     * Called when a push notification is received. Prepares and shows a notification with tap action.
     * @param message The received remote message.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            action = "ACTION_HANDLE_NOTIFICATION"
            putExtra("destination", message.data["destination"]) // send to Activity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        notificationService.showTextWithTapAction(
            message.notification?.channelId ?: ME_APP,
            "PUSH_TEST",
            message.notification?.title ?: "Default Title",
            message.notification?.body ?: "You have a new message",
            pendingIntent,
        )
    }
}
