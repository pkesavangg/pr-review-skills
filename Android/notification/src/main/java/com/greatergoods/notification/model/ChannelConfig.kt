package com.greatergoods.notification.model
/**
 * Configuration for a notification channel.
 * @property id The unique channel ID.
 * @property name The channel name.
 * @property importance The importance level for notifications in this channel.
 * @property description The channel description (optional).
 */
data class ChannelConfig(
    val id: String,
    val name: String,
    val importance: Int,
    val description: String? = null,
)

/**
 * Configuration for building a notification, including its channel and icon.
 * @property channelConfig The channel configuration.
 * @property smallIcon The resource ID for the small icon.
 */
data class BuilderConfig(
    val channelConfig: ChannelConfig,
    val smallIcon: Int,
)
