package com.dmdbrands.gurus.weight.core.service

import kotlinx.coroutines.flow.MutableSharedFlow

enum class NotificationEventType {
    NOTIFICATION_RECEIVED,
    NOTIFICATION_TAPPED,
    TOKEN_UPDATED,
    ERROR_OCCURRED
}

object AppNotificationEventService {
    private val _events = MutableSharedFlow<NotificationEventType>() // Or any type of event
    val events = _events

    suspend fun emit(event: NotificationEventType) {
        _events.emit(event)
    }
}
