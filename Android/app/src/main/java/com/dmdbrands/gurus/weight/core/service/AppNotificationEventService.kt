package com.dmdbrands.gurus.weight.core.service

import kotlinx.coroutines.channels.BufferOverflow
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

    // replay = 1 so a tap emitted during a cold start (process killed, launched via
    // MainActivity.onCreate) is retained until AppViewModel attaches its collector; otherwise
    // the deep-link navigation would be silently dropped (MOB-434). The collector calls
    // resetReplayCache() after consuming so the tap is never re-delivered to a later subscriber.
    private val _tapEvents = MutableSharedFlow<NotificationTapPayload>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tapEvents = _tapEvents

    /** Clears the retained tap so it is not replayed to a future subscriber. */
    fun consumeTap() {
        _tapEvents.resetReplayCache()
    }

    suspend fun emit(event: NotificationEventType) {
        _events.emit(event)
    }

    suspend fun emitTap(payload: NotificationTapPayload) {
        _tapEvents.emit(payload)
    }
}
