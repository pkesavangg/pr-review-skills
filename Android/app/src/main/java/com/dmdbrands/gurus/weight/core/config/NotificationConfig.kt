package com.dmdbrands.gurus.weight.core.config

import com.dmdbrands.gurus.weight.domain.enums.NotificationChannel
import com.greatergoods.notification.model.ChannelConfig
import android.app.Notification
import android.app.NotificationManager
import android.os.Build

object NotificationConfig {
    /**
     * Returns the appropriate importance/priority for notification channels based on SDK version.
     */
    private fun importance(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            Notification.PRIORITY_HIGH
        }

    val NotificationChannels =
        listOf(
            ChannelConfig(
                NotificationChannel.GENERAL,
                "General",
                importance(),
                "General notification",
            ),
            ChannelConfig(
                NotificationChannel.ENTRY_NOTIFICATION,
                "Entry Notification",
                importance(),
                "Entry notification",
            ),
        )
}
