package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.MainActivity
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.NotificationChannel
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
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
 *
 * Entry-sync notifications are branded with a constant "me.App" title + brand icon, an
 * account-aware body, OS grouping and lock-screen privacy, and a tap deep-link (MOB-434).
 * The reading value + account context are derived from the FCM data payload (with a local
 * account lookup); see [NotificationPayload] for the expected payload contract.
 */
@AndroidEntryPoint
class PushNotificationService : FirebaseMessagingService() {
  companion object {
    private const val TAG = "PushNotificationService"
    const val ACTION_HANDLE_NOTIFICATION = "ACTION_HANDLE_NOTIFICATION"
    const val EXTRA_ACCOUNT_ID = "accountId"
    const val EXTRA_DESTINATION = "destination"
    const val EXTRA_MONTH_KEY = "monthKey"
    private const val GROUP_KEY = "com.dmdbrands.gurus.weight.ENTRY_NOTIFICATIONS"
  }

  @Inject
  @ApplicationContext
  lateinit var context: Context

  @Inject
  lateinit var notificationService: NotificationService

  @Inject
  lateinit var appRepository: IAppRepository

  @Inject
  lateinit var accountService: IAccountService

  @Inject
  @ApplicationScope
  lateinit var appScope: CoroutineScope

  /**
   * Called when a new FCM token is generated. Override to handle token updates.
   * @param token The new FCM token.
   */
  override fun onNewToken(token: String) {
    AppLog.v(TAG, "New FCM token received")

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
        AppLog.v(TAG, "FCM token updated")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to check/update FCM token", e)
    }
  }

  /**
   * Called when a push notification is received. Resolves account context locally, shows a
   * branded entry notification with a tap deep-link, and triggers a sync.
   * @param message The received remote message.
   */
  override fun onMessageReceived(message: RemoteMessage) {
    AppLog.d(TAG, "Received message: ${message.messageId}")
    val payload =
      NotificationPayload.from(
        data = message.data,
        notificationTitle = message.notification?.title,
        notificationBody = message.notification?.body,
      )
    // The name becomes the notification tag + hashCode() id. Prefer the FCM messageId, but when
    // it is absent fall back to a per-entry-unique composite (account + month/timestamp +
    // measurement) so distinct entries get distinct ids instead of all collapsing onto accountId
    // or the constant TAG — which would let entries silently overwrite each other and defeat the
    // OS grouping this notification relies on (MOB-434).
    val notificationName =
      message.messageId
        ?: listOfNotNull(payload.accountId, payload.monthKey, payload.measurement)
          .takeIf { it.isNotEmpty() }
          ?.joinToString(separator = ":")
        ?: TAG

    appScope.launch {
      val firstName = resolveFirstName(payload.accountId)
      showEntryNotification(payload, firstName, notificationName)
      AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
    }
  }

  /**
   * Resolves the account first name from the local store by [accountId], or null when the
   * id is absent/unknown. No network is required — accounts are already persisted locally.
   */
  private suspend fun resolveFirstName(accountId: String?): String? {
    val id = accountId ?: return null
    return try {
      accountService.getLoggedInAccounts().firstOrNull { it.id == id }?.firstName
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to resolve account name for notification", e)
      null
    }
  }

  /**
   * Builds and shows the branded, grouped entry notification with a tap deep-link.
   */
  private fun showEntryNotification(
    payload: NotificationPayload,
    firstName: String?,
    notificationName: String,
  ) {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        setPackage(context.packageName)
        action = ACTION_HANDLE_NOTIFICATION
        putExtra(EXTRA_ACCOUNT_ID, payload.accountId)
        putExtra(EXTRA_DESTINATION, payload.destination)
        putExtra(EXTRA_MONTH_KEY, payload.monthKey)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    notificationService.showBrandedNotification(
      channelId = NotificationChannel.ENTRY_NOTIFICATION,
      notificationName = notificationName,
      textTitle = NotificationContentFormatter.title(),
      textContent = NotificationContentFormatter.body(payload, firstName),
      smallIcon = R.drawable.wg_logo,
      contentIntent = pendingIntent,
      groupKey = GROUP_KEY,
    )
  }
}
