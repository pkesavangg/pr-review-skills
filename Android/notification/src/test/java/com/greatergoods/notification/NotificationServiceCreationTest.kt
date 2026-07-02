package com.greatergoods.notification

import androidx.core.app.NotificationCompat
import com.google.common.truth.Truth.assertThat
import com.greatergoods.notification.model.BuilderConfig
import com.greatergoods.notification.model.ChannelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Channel creation and builder retrieval tests for [NotificationService].
 */
class NotificationServiceCreationTest : NotificationServiceTestBase() {

    @Test
    fun `createInstance with list delegates to initializeChannels`() {
        val channels = listOf(builderConfig)

        service.createInstance(channels)

        verify(exactly = 1) { notificationHandler.initializeChannels(channels) }
    }

    @Test
    fun `createInstance with single channel delegates to initializeChannel`() {
        service.createInstance(builderConfig)

        verify(exactly = 1) { notificationHandler.initializeChannel(builderConfig) }
    }

    @Test
    fun `createInstance with multiple channels passes all to handler`() {
        val channel2 = BuilderConfig(
            channelConfig = ChannelConfig("ch2", "Channel 2", 2),
            smallIcon = android.R.drawable.ic_notification_overlay,
        )
        val channels = listOf(builderConfig, channel2)

        service.createInstance(channels)

        verify(exactly = 1) { notificationHandler.initializeChannels(channels) }
    }

    @Test
    fun `createInstance with empty list delegates to handler`() {
        val emptyList = emptyList<BuilderConfig>()

        service.createInstance(emptyList)

        verify(exactly = 1) { notificationHandler.initializeChannels(emptyList) }
    }

    @Test
    fun `getBuilder delegates to notificationHandler`() {
        val mockBuilder: NotificationCompat.Builder = mockk()
        every { notificationHandler.getBuilder(testChannelId) } returns mockBuilder

        val result = service.getBuilder(testChannelId)

        assertThat(result).isSameInstanceAs(mockBuilder)
        verify(exactly = 1) { notificationHandler.getBuilder(testChannelId) }
    }

    @Test
    fun `getBuilder with different channel ids delegates correctly`() {
        val builder1: NotificationCompat.Builder = mockk()
        val builder2: NotificationCompat.Builder = mockk()
        every { notificationHandler.getBuilder("channel_a") } returns builder1
        every { notificationHandler.getBuilder("channel_b") } returns builder2

        assertThat(service.getBuilder("channel_a")).isSameInstanceAs(builder1)
        assertThat(service.getBuilder("channel_b")).isSameInstanceAs(builder2)
    }
}
