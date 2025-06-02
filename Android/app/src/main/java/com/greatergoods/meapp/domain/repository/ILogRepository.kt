package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.LogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing application logs.
 * Provides methods for logging, retrieving, and managing log entries.
 * Integrates with Timber for structured logging.
 */
interface ILogRepository {
    /**
     * Log levels for different types of log entries
     */
    enum class LogType(val priority: Int) {
        VERBOSE(android.util.Log.VERBOSE),
        DEBUG(android.util.Log.DEBUG),
        INFO(android.util.Log.INFO),
        WARN(android.util.Log.WARN),
        ERROR(android.util.Log.ERROR),
        ASSERT(android.util.Log.ASSERT)
    }

    /**
     * Initialize the logging system with Timber
     */
    suspend fun initialize()

    /**
     * Get the current session ID.
     * @return The current session ID, or null if not initialized.
     */
    fun getSessionId(): String?

    /**
     * Get all logs for the current account
     * @return Flow of log entries
     */
    fun getLogs(): Flow<List<LogEntity>>

    /**
     * Get logs for a specific account
     * @param accountId Account ID
     * @return Flow of log entries
     */
    fun getLogsByAccountId(accountId: String): Flow<List<LogEntity>>

    /**
     * Get logs for the current session
     * @return Flow of log entries
     */
    fun getLogsBySessionId(): Flow<List<LogEntity>>

    /**
     * Get logs by type
     * @param type Log type
     * @return Flow of log entries
     */
    fun getLogsByType(type: LogType): Flow<List<LogEntity>>

    /**
     * Get logs for the last N days
     * @param days Number of days to look back
     * @return Flow of log entries
     */
    fun getLogsForLastDays(days: Int): Flow<List<LogEntity>>

    /**
     * Delete all logs for the current account
     */
    suspend fun deleteAllLogs()

    /**
     * Delete logs for a specific account
     * @param accountId Account ID
     */
    suspend fun deleteLogsByAccountId(accountId: String)

    /**
     * Delete logs older than specified days
     * @param days Number of days to keep
     */
    suspend fun deleteLogsOlderThanDays(days: Int)
} 