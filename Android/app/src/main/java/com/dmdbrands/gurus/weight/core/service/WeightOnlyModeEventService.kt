package com.dmdbrands.gurus.weight.core.service

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Events for weight-only mode management across ViewModels.
 */
enum class WeightOnlyModeEventType {
    SHOW_ALERT,
    HIDE_ALERT,
    ENABLE_BODY_METRICS,
    DISMISS_ALERT
}

/**
 * Singleton service for managing weight-only mode events across the app.
 * Similar to AppNotificationEventService pattern.
 */
object WeightOnlyModeEventService {
    private val _events = MutableSharedFlow<WeightOnlyModeEventType>()
    val events = _events

    suspend fun emit(event: WeightOnlyModeEventType) {
        _events.emit(event)
    }
}
