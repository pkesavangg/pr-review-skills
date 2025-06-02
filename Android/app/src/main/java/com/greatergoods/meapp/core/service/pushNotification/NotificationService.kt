package com.greatergoods.meapp.core.service.pushNotification

import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.example.notification.NotificationHandler
import com.greatergoods.notification.model.BuilderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
class NotificationService @Inject constructor(
    private val notificationHandler: NotificationHandler,
) {
    private val _statusMap = MutableLiveData<MutableMap<String, Boolean>>(mutableMapOf())

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
        CoroutineScope(Dispatchers.IO).launch {
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
    fun getBuilder(channelId: String): NotificationCompat.Builder {
        return notificationHandler.getBuilder(channelId)
    }

    /**
     * Shows a notification with the given ID and Notification object.
     * @param notificationId The notification ID.
     * @param notification The Notification object.
     */
    fun showNotification(notificationId: Int, notification: Notification) {
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
    fun cancelGroupedNotification(notificationId: Int, groupId: Int) {
        notificationHandler.cancelGroupedNotification(notificationId, groupId)
    }

    /**
     * Cancels a notification by ID and optional tag.
     * @param notificationId The notification ID.
     * @param tag The notification tag (optional).
     */
    fun cancelNotification(notificationId: Int, tag: String? = null) {
        notificationHandler.cancelNotification(notificationId, tag)
        tag?.let { update(it, false) }
    }

    /**
     * Cancels all notifications and updates status map.
     */
    fun cancelAll() {
        notificationHandler.cancelAll()
        _statusMap.value?.map { update(it.key, false) }
    }

    /**
     * Checks if there are no active notifications for a channel.
     * @param channelId The channel ID.
     * @return True if no active notifications, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun checkNoActiveNotification(channelId: Int): Boolean {
        return notificationHandler.checkNoActiveNotifications(channelId)
    }

    /**
     * Updates the status of a notification in the status map.
     * @param notificationId The notification ID or tag.
     * @param status True if active, false otherwise.
     */
    fun update(notificationId: String, status: Boolean) {
        val map = _statusMap.value?.apply {
            put(notificationId, status)
        }
        _statusMap.postValue(map!!)
    }

    /**
     * Subscribes to notification status changes as a Flow.
     * @return Flow of notification status map.
     */
    fun subscribeStatus(): Flow<Map<String, Boolean>> {
        return _statusMap.asFlow()
    }

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
}
