package com.dmdbrands.gurus.weight.domain.model.api.support

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.db.entity.log.LogEntity

/**
 * Request model for sending logs to support.
 * Based on Angular http.service.ts sendLog() method.
 *
 * @property logs List of log entries to send to support.
 * @property version Current application version.
 */
data class SendLogRequest(
    /**
     * List of log entries from the local database.
     */
    val logs: List<LogEntry>,

    /**
     * Application version when logs were sent.
     */
    val version: String,
)

/**
 * Simplified log entry for API transmission.
 * Based on LogEntity but optimized for network transfer.
 */
data class LogEntry(

    /**
     * Timestamp when the log was created.
     */
    val time: String,

    /**
     * Additional data (stack traces, etc.).
     */
    val data: String?
) {
    companion object {
        /**
         * Converts LogEntity to LogEntry for API transmission.
         */
        fun from(logEntity: LogEntity): LogEntry {
            return LogEntry(
                time = DateTimeConverter.timestampToIso(logEntity.timestamp),
                data = logEntity.message
            )
        }
    }
}

/**
 * Device information for debugging context.
 */
data class DeviceInfo(
    /**
     * Platform (Android/iOS).
     */
    val platform: String,

    /**
     * Operating system version.
     */
    val osVersion: String,

    /**
     * Device model.
     */
    val deviceModel: String,

    /**
     * API URL being used.
     */
    val apiUrl: String,

    /**
     * Current timezone.
     */
    val timezone: String,

    /**
     * Timezone offset.
     */
    val timezoneOffset: String
)
