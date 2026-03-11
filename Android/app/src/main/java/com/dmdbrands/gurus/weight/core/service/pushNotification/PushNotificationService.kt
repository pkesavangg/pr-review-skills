package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.MainActivity
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.NotificationChannel
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.greatergoods.notification.NotificationService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
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

  @Inject
  @ApplicationScope
  lateinit var appScope: CoroutineScope

  /**
   * Called when a new FCM token is generated. Override to handle token updates.
   * @param token The new FCM token.
   */
  override fun onNewToken(token: String) {
    AppLog.v(TAG, "New FCM token: $token")

    // Update the token and device info
    appScope.launch {
      try {
        updateFcmToken(token)
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to update FCM token", e)
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
        AppLog.v(TAG, "FCM token updated: $newToken")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to check/update FCM token", e)
    }
  }

  /**
   * Called when a push notification is received. Prepares and shows a notification with tap action.
   * @param message The received remote message.
   */
  override fun onMessageReceived(message: RemoteMessage) {
    AppLog.d(TAG, "Received message: ${message.messageId}")
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
      message.notification?.channelId ?: NotificationChannel.DEFAULT,
      "PUSH_TEST",
      message.notification?.title ?: "Default Title",
      message.notification?.body ?: "You have a new message",
      pendingIntent,
    )

    appScope.launch {
      AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
    }
  }
}
