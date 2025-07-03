package com.greatergoods.meapp.domain.model.api.notification

data class NotificationSettingsRequest(
    val shouldSendEntryNotifications: Boolean,
    val shouldSendWeightInEntryNotifications: Boolean
)
