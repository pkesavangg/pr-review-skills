package com.greatergoods.notification

import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.notification.NotificationHandler
import com.google.firebase.messaging.FirebaseMessaging
import com.greatergoods.notification.model.BuilderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Notification
import android.app.PendingIntent
import android.os.Build

/**
 * Service for managing and displaying notifications using NotificationHandler.
 * Provides various methods to show, update, and cancel notifications, as well as subscribe to notification status changes.
 * @property notificationHandler The handler responsible for notification operations.
 */
class NotificationService
@Inject
constructor(
    private val notificationHandler: NotificationHandler,
) {
    // Use a private CoroutineScope for background work (consider proper lifecycle management)
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val statusMap: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    /**
     * Initializes multiple notification channels.
     * @param channels List of channel configurations.
     */
    fun createInstance(channels: List<BuilderConfig>) {
        notificationHandler.initializeChannels(channels)
    }

    /**
     * Initializes a single notification channel.
     * @param channel Channel configuration.
     */
    fun createInstance(channel: BuilderConfig) {
        notificationHandler.initializeChannel(channel)
    }

    /**
     * Collects and updates active notifications for a channel (API 26+).
     * @param channelId The channel ID to check.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun activeNotification(channelId: String) {
        serviceScope.launch {
            notificationHandler.activeNotifications(channelId).collect {
                update(it.tag, it.isOngoing)
            }
        }
    }

    /**
     * Returns a NotificationCompat.Builder for the given channel.
     * @param channelId The channel ID.
     * @return NotificationCompat.Builder instance.
     */
    fun getBuilder(channelId: String): NotificationCompat.Builder = notificationHandler.getBuilder(channelId)

    /**
     * Shows a notification with the given ID and Notification object.
     * @param notificationId The notification ID.
     * @param notification The Notification object.
     */
    fun showNotification(
        notificationId: Int,
        notification: Notification,
    ) {
        notificationHandler.showNotification(
            notificationId = notificationId,
            notification = notification,
        )
    }

    /**
     * Cancels a grouped notification by ID and group ID.
     * @param notificationId The notification ID.
     * @param groupId The group ID.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun cancelGroupedNotification(
        notificationId: Int,
        groupId: Int,
    ) {
        notificationHandler.cancelGroupedNotification(notificationId, groupId)
    }

    /**
     * Cancels a notification by ID and optional tag.
     * @param notificationId The notification ID.
     * @param tag The notification tag (optional).
     */
    fun cancelNotification(
        notificationId: Int,
        tag: String? = null,
    ) {
        notificationHandler.cancelNotification(notificationId, tag)
        tag?.let { update(it, false) }
    }

    /**
     * Cancels all notifications and updates status map.
     */
    fun cancelAll() {
        notificationHandler.cancelAll()
        statusMap.value.keys.forEach { update(it, false) }
    }

    /**
     * Checks if there are no active notifications for a channel.
     * @param channelId The channel ID.
     * @return True if no active notifications, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun checkNoActiveNotification(channelId: Int): Boolean =
        notificationHandler.checkNoActiveNotifications(channelId)

    /**
     * Updates the status of a notification in the status map.
     * @param notificationId The notification ID or tag.
     * @param status True if active, false otherwise.
     */
    fun update(
        notificationId: String,
        status: Boolean,
    ) {
        val map = statusMap.value.toMutableMap()
        map[notificationId] = status
        statusMap.value = map.toMap() // Always emit a new immutable map
    }

    /**
     * Subscribes to notification status changes as a StateFlow.
     * @return StateFlow of notification status map.
     */
    fun subscribeStatus(): StateFlow<Map<String, Boolean>> = statusMap.asStateFlow()

    /**
     * Shows a simple text notification.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param priority The notification priority.
     */
    fun showSimpleText(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showSimpleText(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a text notification with a tap action.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param contentIntent The PendingIntent for tap action.
     * @param priority The notification priority.
     */
    fun showTextWithTapAction(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        contentIntent: PendingIntent,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showTextWithTapAction(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            contentIntent = contentIntent,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a branded, grouped notification with a tap action and lock-screen visibility
     * control (MOB-434).
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The constant brand title.
     * @param textContent The body text.
     * @param smallIcon The brand small-icon resource id.
     * @param contentIntent The PendingIntent for the tap action.
     * @param groupKey The group key used to collapse related notifications.
     * @param visibility Lock-screen visibility (defaults to VISIBILITY_PRIVATE).
     * @param priority The notification priority.
     */
    fun showBrandedNotification(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        smallIcon: Int,
        contentIntent: PendingIntent,
        groupKey: String,
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showBrandedNotification(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            smallIcon = smallIcon,
            contentIntent = contentIntent,
            groupKey = groupKey,
            visibility = visibility,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a text notification with action buttons.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param pendingIntent The action for the button.
     * @param priority The notification priority.
     */
    fun showTextWithButtons(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        pendingIntent: NotificationCompat.Action,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showTextWithButtons(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            action = pendingIntent,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a large text notification.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param priority The notification priority.
     */
    fun showLargeText(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showLargeText(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a text notification with an icon.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param icon The icon resource ID.
     * @param priority The notification priority.
     */
    fun showTextWithIcon(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        icon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showTextWithIcon(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            icon = icon,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Shows a text notification with a thumbnail icon.
     * @param channelId The channel ID.
     * @param notificationName The notification name/tag.
     * @param textTitle The notification title.
     * @param textContent The notification content.
     * @param icon The icon resource ID.
     * @param priority The notification priority.
     */
    fun showTextWithThumbnail(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        icon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        notificationHandler.showTextWithThumbnail(
            channelId = channelId,
            notificationName = notificationName,
            textTitle = textTitle,
            textContent = textContent,
            icon = icon,
            priority = priority,
        )
        update(notificationName, true)
    }

    /**
     * Retrieves the Firebase Cloud Messaging (FCM) token for the device.
     * @param onSuccess Callback invoked with the token on success.
     * @param onError Callback invoked with the exception on error.
     */
    fun fetchFCMToken(
        onSuccess: (token: String) -> Unit,
        onError: (exception: Exception?) -> Unit,
    ) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                onSuccess(token)
            } else {
                onError(task.exception)
            }
        }
    }

    /**
     * Subscribes the device to a topic for FCM notifications.
     * @param topic The topic to subscribe to.
     * @param onSuccess Callback invoked on successful subscription.
     * @param onError Callback invoked with the exception on error.
     */
    fun subscribeToTopic(
        topic: String,
        onSuccess: () -> Unit,
        onError: (exception: Exception?) -> Unit,
    ) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onSuccess()
            } else {
                onError(task.exception)
            }
        }
    }
}
