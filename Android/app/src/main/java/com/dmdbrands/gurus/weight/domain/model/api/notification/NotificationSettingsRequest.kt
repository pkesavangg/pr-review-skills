package com.dmdbrands.gurus.weight.domain.model.api.notification

data class NotificationSettingsRequest(
    val shouldSendEntryNotifications: Boolean,
    val shouldSendWeightInEntryNotifications: Boolean
)
