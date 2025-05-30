package com.greatergoods.meapp.domain.model.api

data class NotificationSettingsRequest(
    val shouldSendEntryNotifications: Boolean,
    val shouldSendWeightInEntryNotifications: Boolean
)
