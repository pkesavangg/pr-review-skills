package com.greatergoods.meapp.domain.model.api.support

import com.greatergoods.meapp.data.storage.db.entity.log.LogEntity

/**
 * Request model for sending logs to support.
 * Based on Angular http.service.ts sendLog() method.
 *
 * @property logs List of log entries to send to support.
 * @property deviceInfo Additional device information for debugging.
 * @property appVersion Current application version.
 * @property timestamp When the logs were collected.
 */
data class SendLogRequest(
    /**
     * List of log entries from the local database.
     */
    val logs: List<LogEntry>,

    /**
     * Device and app information for context.
     */
    val deviceInfo: DeviceInfo,

    /**
     * Application version when logs were sent.
     */
    val appVersion: String,

    /**
     * Timestamp when the logs were collected and sent.
     */
    val timestamp: Long
)

/**
 * Simplified log entry for API transmission.
 * Based on LogEntity but optimized for network transfer.
 */
data class LogEntry(
    /**
     * Unique identifier for the log entry.
     */
    val id: String,

    /**
     * Account ID associated with the log.
     */
    val accountId: String,

    /**
     * Session ID when the log was created.
     */
    val sessionId: String,

    /**
     * Component or class that generated the log.
     */
    val tag: String,

    /**
     * Method or function that generated the log.
     */
    val tagId: String,

    /**
     * Type of log (e, i, d, w, v, a).
     */
    val type: String,

    /**
     * Log message.
     */
    val message: String,

    /**
     * Timestamp when the log was created.
     */
    val timestamp: Long,

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
                id = logEntity.id,
                accountId = logEntity.accountId,
                sessionId = logEntity.sessionId,
                tag = logEntity.tag,
                tagId = logEntity.tagId,
                type = logEntity.type,
                message = logEntity.message,
                timestamp = logEntity.timestamp,
                data = logEntity.data
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
