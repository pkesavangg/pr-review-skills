package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.DatabaseLoggingTree
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.LogEntity
import com.greatergoods.meapp.domain.repository.ILogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ILogRepository] that manages application logs.
 * Integrates with Timber for structured logging and Room for persistence.
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao,
    private val currentAccountId: String
) : ILogRepository {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Session ID generated once per app launch and maintained throughout the session.
     * This allows tracking logs across different app launches while maintaining session context.
     */
    private var sessionId: String? = null

    /**
     * Flag to track if the logging system has been initialized
     */
    private var isInitialized = false

    override suspend fun initialize() {
        if (isInitialized) {
            Timber.w("Logging system already initialized with session ID: $sessionId")
            return
        }

        // Generate a new session ID on every app launch
        sessionId = UUID.randomUUID().toString()
        
        // Plant the database logging tree AFTER sessionId is set
        Timber.plant(DatabaseLoggingTree(logDao, currentAccountId, sessionId, ioScope))
        
        isInitialized = true
        Timber.i("New session started with ID: $sessionId")
    }

    /**
     * Clear the current session and generate a new session ID.
     * This should be called when explicitly wanting to start a new session.
     */
    fun clearSession() {
        // Remove the existing logging tree
        Timber.uprootAll()
        
        sessionId = null
        isInitialized = false
        Timber.i("Session cleared")
        
        // Reinitialize with a new session
        initialize()
    }

    override fun getSessionId(): String? = sessionId

    override fun getLogs(): Flow<List<LogEntity>> {
        ensureInitialized()
        return logDao.getLogsByAccountId(currentAccountId)
    }

    override fun getLogsByAccountId(accountId: String): Flow<List<LogEntity>> {
        ensureInitialized()
        return logDao.getLogsByAccountId(accountId)
    }

    override fun getLogsBySessionId(): Flow<List<LogEntity>> {
        ensureInitialized()
        return sessionId?.let { logDao.getLogsBySessionId(it) }
            ?: throw IllegalStateException("Session ID not initialized. Call initialize() first.")
    }

    override fun getLogsByType(type: ILogRepository.LogType): Flow<List<LogEntity>> {
        ensureInitialized()
        return logDao.getLogsByType(type.name.lowercase().first().toString())
    }

    override fun getLogsForLastDays(days: Int): Flow<List<LogEntity>> {
        ensureInitialized()
        val startTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
        return logDao.getLogsForLastDays(startTimestamp)
    }

    override suspend fun deleteAllLogs() {
        ensureInitialized()
        logDao.deleteAllLogs()
    }

    override suspend fun deleteLogsByAccountId(accountId: String) {
        ensureInitialized()
        logDao.deleteLogsByAccountId(accountId)
    }

    override suspend fun deleteLogsOlderThanDays(days: Int) {
        ensureInitialized()
        val startTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
        logDao.deleteLogsOlderThanDays(startTimestamp)
    }

    /**
     * Ensures the logging system is initialized before performing operations
     * @throws IllegalStateException if the system is not initialized
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Logging system not initialized. Call initialize() first.")
        }
    }

    override suspend fun log(tag: String, message: String, type: String, data: String?) {
        if (!isInitialized) {
            Timber.e("Logging system not initialized. Skipping log entry.")
            return
        }

        val logEntry = LogEntity(
            id = UUID.randomUUID().toString(),
            accountId = currentAccountId,
            sessionId = sessionId ?: return,
            tag = tag,
            tagId = tag,
            type = type,
            message = message,
            timestamp = System.currentTimeMillis(),
            data = data
        )

        try {
            logDao.insertLog(logEntry)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert log entry")
        }
    }

    override fun getAllLogs(): Flow<List<LogEntity>> = logDao.getAllLogs()

    override suspend fun clearLogs() {
        logDao.deleteAllLogs()
    }
} 