package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.log.LogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for log entries in the database.
 * Provides methods to insert, query, and delete log entries.
 */
@Dao
interface LogDao {
    /**
     * Insert a log entry into the database.
     * @param log The log entry to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    /**
     * Insert multiple log entries into the database.
     * @param logs List of log entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntity>)

    /**
     * Update an existing log entry in the database.
     * @param log The log entity to update
     */
    @Update
    suspend fun updateLog(log: LogEntity)

    /**
     * Delete a log entry from the database.
     * @param log The log entity to delete
     */
    @Delete
    suspend fun deleteLog(log: LogEntity)

    /**
     * Get all logs from the database.
     * @return A Flow of all logs
     */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    /**
     * Get all logs for a specific account.
     * @param accountId The account ID to filter logs by
     * @return Flow of list of log entries
     */
    @Query("SELECT * FROM logs WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getLogsByAccountId(accountId: String): Flow<List<LogEntity>>

    /**
     * Get all logs for a specific session.
     * @param sessionId The session ID to filter logs by
     * @return Flow of list of log entries
     */
    @Query("SELECT * FROM logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getLogsBySessionId(sessionId: String): Flow<List<LogEntity>>

    /**
     * Get all logs of a specific type.
     * @param type The type of logs to retrieve (e: error, i: info, d: debug, w: warning, v: verbose, a: assert)
     * @return Flow of list of log entries
     */
    @Query("SELECT * FROM logs WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: String): Flow<List<LogEntity>>

    /**
     * Get logs within a time range.
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return A Flow of logs within the specified time range
     */
    @Query("SELECT * FROM logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<LogEntity>>

    /**
     * Get logs by tag.
     * @param tag The tag (class name)
     * @return A Flow of logs with the specified tag
     */
    @Query("SELECT * FROM logs WHERE tag = :tag ORDER BY timestamp DESC")
    fun getLogsByTag(tag: String): Flow<List<LogEntity>>

    /**
     * Get logs by tag ID (function name).
     * @param tagId The tag ID (function name)
     * @return A Flow of logs with the specified tag ID
     */
    @Query("SELECT * FROM logs WHERE tagId = :tagId ORDER BY timestamp DESC")
    fun getLogsByTagId(tagId: String): Flow<List<LogEntity>>

    /**
     * Delete all logs from the database.
     */
    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()

    /**
     * Delete all logs for a specific account.
     * @param accountId The account ID to delete logs for
     */
    @Query("DELETE FROM logs WHERE accountId = :accountId")
    suspend fun deleteLogsByAccountId(accountId: String)

    /**
     * Delete all logs for a specific session.
     * @param sessionId The session ID
     */
    @Query("DELETE FROM logs WHERE sessionId = :sessionId")
    suspend fun deleteLogsBySessionId(sessionId: String)

    /**
     * Delete logs older than a specific timestamp.
     * @param startTimestamp The timestamp to delete logs before
     */
    @Query("DELETE FROM logs WHERE timestamp < :startTimestamp")
    suspend fun deleteLogsOlderThanDays(startTimestamp: Long)

    /**
     * Get logs created after a specific timestamp.
     * @param startTimestamp The timestamp to filter logs by
     * @return Flow of list of log entries
     */
    @Query("SELECT * FROM logs WHERE timestamp >= :startTimestamp ORDER BY timestamp DESC")
    fun getLogsForLastDays(startTimestamp: Long): Flow<List<LogEntity>>

    /**
     * Get logs by account ID with a limit.
     * @param accountId The account ID
     * @param limit Maximum number of logs to return
     * @return A Flow of logs for the specified account
     */
    @Query("SELECT * FROM logs WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByAccountIdWithLimit(accountId: String, limit: Int): Flow<List<LogEntity>>

    /**
     * Get logs by account ID and type.
     * @param accountId The account ID
     * @param type The log type (i/e/d/s)
     * @return A Flow of logs matching the criteria
     */
    @Query("SELECT * FROM logs WHERE accountId = :accountId AND type = :type ORDER BY timestamp DESC")
    fun getLogsByAccountIdAndType(accountId: String, type: String): Flow<List<LogEntity>>

    /**
     * Get all logs from the database with pagination.
     * @param limit Maximum number of logs to return
     * @param offset Number of logs to skip
     * @return A Flow of logs
     */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAllLogsPaginated(limit: Int, offset: Int): Flow<List<LogEntity>>

    /**
     * Get total count of logs in the database.
     * @return Flow of total log count
     */
    @Query("SELECT COUNT(*) FROM logs")
    fun getLogCount(): Flow<Int>
}
