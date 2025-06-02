package com.greatergoods.meapp.core.service.pushNotification

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Service that listens to system notifications and updates the app's notification state accordingly.
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {
    @Inject
    lateinit var notificationService: NotificationService

    /**
     * Called when a notification is posted to the status bar.
     * @param sbn The status bar notification.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.tag?.let { notificationService.update(it, true) }
    }

    /**
     * Called when a notification is removed from the status bar.
     * @param sbn The status bar notification.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.tag?.let { notificationService.update(it, false) }
    }
}
