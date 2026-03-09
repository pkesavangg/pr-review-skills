package com.example.notification

import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.greatergoods.notification.model.BuilderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import timber.log.Timber
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.StatusBarNotification

/**
 * Handles creation, display, and management of notifications and channels for the app.
 * Provides utility methods for various notification styles and actions.
 * @property context The application context used for notification operations.
 */
class NotificationHandler(
    private val context: Context,
) {
    private var notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var sageChannels: List<BuilderConfig>

    fun initializeChannels(channels: List<BuilderConfig>) {
        if (!this::sageChannels.isInitialized) {
            sageChannels = channels
        } else {
            sageChannels += channels
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannels: List<NotificationChannel> =
                channels.map {
                    val notificationChannel =
                        NotificationChannel(
                            it.channelConfig.id,
                            it.channelConfig.name,
                            it.channelConfig.importance,
                        )
                    notificationChannel.description = it.channelConfig.description
                    notificationChannel
                }

            notificationManager.createNotificationChannels(notificationChannels)
        }
    }

    fun initializeChannel(channel: BuilderConfig) {
        if (!this::sageChannels.isInitialized) {
            sageChannels = listOf(channel)
        } else {
            sageChannels += channel
        }
        val notificationChannel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel(
                    channel.channelConfig.id,
                    channel.channelConfig.name,
                    channel.channelConfig.importance,
                )
            } else {
                TODO("VERSION.SDK_INT < O")
            }
        notificationChannel.description = channel.channelConfig.description
        notificationManager.createNotificationChannel(notificationChannel)
    }

    fun getBuilder(channelId: String): NotificationCompat.Builder {
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }
        return NotificationCompat
            .Builder(context, channelId)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(requiredConfig.smallIcon)
    }

    fun showNotification(
        notificationId: Int,
        notification: Notification,
    ) {
        notificationManager.notify(notificationId, notification)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun cancelGroupedNotification(
        notificationId: Int,
        groupId: Int,
    ) {
        notificationManager.cancel(notificationId)
        clearGroupNotification(groupId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun activeNotifications(channelId: String): Flow<StatusBarNotification> =
        notificationManager.activeNotifications
            .filter { it.notification.channelId == channelId }
            .asFlow()

    @RequiresApi(Build.VERSION_CODES.M)
    private fun clearGroupNotification(id: Int) {
        if (notificationManager.activeNotifications.filter { it.groupKey.contains(id.toString()) }.size == 1) {
            notificationManager.cancel(id)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun checkNoActiveNotifications(channelId: Int): Boolean =
        notificationManager.activeNotifications
            .filterNot { it.id == channelId.hashCode() }
            .isEmpty()

    fun cancelNotification(
        notificationId: Int,
        tag: String? = null,
    ) {
        notificationManager.cancel(tag, notificationId)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    fun showSimpleText(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setPriority(priority)

        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    fun showTextWithTapAction(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        contentIntent: PendingIntent,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setContentIntent(contentIntent)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setPriority(priority)
                .setAutoCancel(true)

        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    fun showTextWithButtons(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        action: NotificationCompat.Action,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .addAction(action)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setPriority(priority)
                .setAutoCancel(true)

        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    fun showLargeText(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText("Drag to read more")
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(textContent),
                ).setPriority(priority)

        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    fun showTextWithIcon(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        icon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val drawable =
            ContextCompat.getDrawable(context, icon) // Replace 'icon' with the vector drawable ID
        val bitmapImage = drawable?.let { drawableToBitmap(it) }
        if (bitmapImage == null) {
            Timber.e("Failed to load bitmap image from resource ID: $icon")
            return
        }
        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setLargeIcon(
                    bitmapImage,
                ).setPriority(priority)

        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    fun showTextWithThumbnail(
        channelId: String,
        notificationName: String,
        textTitle: String,
        textContent: String,
        icon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        val drawable =
            ContextCompat.getDrawable(context, icon) // Replace 'icon' with the vector drawable ID
        val bitmapImage = drawable?.let { drawableToBitmap(it) }
        if (bitmapImage == null) {
            Timber.e("Failed to load bitmap image from resource ID: $icon")
            return
        }

        val requiredConfig = requireNotNull(sageChannels.find { it.channelConfig.id == channelId }) { "No channel config found for id: $channelId" }

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(requiredConfig.smallIcon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(bitmapImage),
                ).setPriority(priority)
        notificationManager.notify(notificationName, notificationName.hashCode(), builder.build())
    }

    private fun drawableToBitmap(
        drawable: Drawable,
        targetWidth: Int = 128,
        targetHeight: Int = 128,
    ): Bitmap {
        // Create a bitmap with the desired dimensions
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // Create a canvas to draw the bitmap
        val canvas = Canvas(bitmap)

        // Calculate the center position for the drawable
        val left = (targetWidth - drawable.intrinsicWidth) / 2
        val top = (targetHeight - drawable.intrinsicHeight) / 2

        // Set the bounds of the drawable to center it in the bitmap
        drawable.setBounds(
            left,
            top,
            left + drawable.intrinsicWidth,
            top + drawable.intrinsicHeight,
        )

        // Draw the drawable onto the canvas
        drawable.draw(canvas)

        return bitmap
    }
}
