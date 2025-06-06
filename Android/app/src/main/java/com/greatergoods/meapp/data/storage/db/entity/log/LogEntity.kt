package com.greatergoods.meapp.data.storage.db.entity.log

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a log entry in the database.
 * @property id Unique identifier for the log entry
 * @property accountId ID of the account associated with the log
 * @property sessionId ID of the session when the log was created
 * @property tag Class name or tag associated with the log
 * @property tagId Method name or tag ID associated with the log
 * @property type Type of log (e: error, i: info, d: debug, w: warning, v: verbose, a: assert)
 * @property message Log message
 * @property timestamp Timestamp when the log was created
 * @property data Additional data (e.g., stack trace for exceptions)
 */
@Entity(
    tableName = "logs",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["sessionId"]),
        Index(value = ["type"]),
        Index(value = ["timestamp"])
    ]
)
data class LogEntity(
    /**
     * Unique identifier for the log entry
     */
    @PrimaryKey
    val id: String,

    /**
     * ID of the account associated with this log
     */
    val accountId: String,

    /**
     * Session identifier, generated on app launch
     */
    val sessionId: String,

    /**
     * Class name or component that generated the log
     */
    val tag: String,

    /**
     * Function or method name that generated the log
     */
    val tagId: String,

    /**
     * Type of log (e: error, i: info, d: debug, w: warning, v: verbose, a: assert)
     */
    val type: String,

    /**
     * Short message describing the log entry
     */
    val message: String,

    /**
     * Timestamp when the log was created
     */
    val timestamp: Long,

    /**
     * Additional data associated with the log entry
     */
    val data: String?
)
