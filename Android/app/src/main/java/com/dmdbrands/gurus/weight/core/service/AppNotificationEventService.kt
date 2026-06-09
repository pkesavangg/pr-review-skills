package com.dmdbrands.gurus.weight.core.service

import kotlinx.coroutines.flow.MutableSharedFlow

enum class NotificationEventType {
    NOTIFICATION_RECEIVED,
    NOTIFICATION_TAPPED,
    TOKEN_UPDATED,
    ERROR_OCCURRED
}

/**
 * Deep-link context carried from a tapped notification to the app (MOB-434).
 * @property accountId account the entry synced to (used to switch account if needed).
 * @property destination [com.dmdbrands.gurus.weight.domain.enums.ProductType] id route hint.
 * @property monthKey optional month/timestamp key to open the matching History detail.
 */
data class NotificationTapPayload(
    val accountId: String?,
    val destination: String?,
    val monthKey: String?,
)

object AppNotificationEventService {
    private val _events = MutableSharedFlow<NotificationEventType>() // Or any type of event
    val events = _events

    private val _tapEvents = MutableSharedFlow<NotificationTapPayload>()
    val tapEvents = _tapEvents

    suspend fun emit(event: NotificationEventType) {
        _events.emit(event)
    }

    suspend fun emitTap(payload: NotificationTapPayload) {
        _tapEvents.emit(payload)
    }
}
