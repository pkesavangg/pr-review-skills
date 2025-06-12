package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.log.LogEntity
import com.greatergoods.meapp.domain.repository.ILogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ILogRepository] that manages application logs.
 * Integrates with Timber for structured logging and Room for persistence.
 */
@Singleton
class LogRepository
    @Inject
    constructor(
        private val logDao: LogDao,
    ) : ILogRepository {
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var currentSessionId: String = UUID.randomUUID().toString()
        private var currentAccountId: String = "default"
        private var isInitialized = false

        companion object {
            private const val MAX_LOG_AGE_DAYS = 7
            private const val MAX_LOG_SIZE = 10000 // Maximum number of logs to keep
        }

        init {
            AppLog.d("LogRepository", "Initialized with session ID: $currentSessionId")
        }

        override suspend fun initialize() {
            if (isInitialized) return

            repositoryScope.launch {
                try {
                    // Clean up old logs
                    deleteLogsOlderThanDays(MAX_LOG_AGE_DAYS)

                    // Check and trim log size if needed
                    val logCount = logDao.getLogCount().first()
                    if (logCount > MAX_LOG_SIZE) {
                        logDao.deleteOldestLogs(MAX_LOG_SIZE)
                    }

                    isInitialized = true
                    AppLog.d("LogRepository", "Initialized with session ID: $currentSessionId")
                } catch (e: Exception) {
                    AppLog.e("LogRepository", "Failed to initialize logging system", e.toString())
                    throw e
                }
            }
        }

        override fun getSessionId(): String = currentSessionId

        override fun getLogs(): Flow<List<LogEntity>> =
            flow {
                if (!isInitialized) {
                    throw IllegalStateException("LogRepository not initialized")
                }
                emitAll(logDao.getAllLogs())
            }.catch { e ->
                AppLog.e("LogRepository", "Error fetching logs", e.toString())
                emit(emptyList())
            }

        override fun getLogsByAccountId(accountId: String): Flow<List<LogEntity>> =
            flow {
                if (!isInitialized) {
                    throw IllegalStateException("LogRepository not initialized")
                }
                emitAll(logDao.getLogsByAccountId(accountId))
            }.catch { e ->
                AppLog.e("LogRepository", "Error fetching logs for account $accountId", e.toString())
                emit(emptyList())
            }

        override fun getLogsBySessionId(): Flow<List<LogEntity>> =
            flow {
                if (!isInitialized) {
                    throw IllegalStateException("LogRepository not initialized")
                }
                emitAll(logDao.getLogsBySessionId(currentSessionId))
            }.catch { e ->
                AppLog.e("LogRepository", "Error fetching logs for session $currentSessionId", e.toString())
                emit(emptyList())
            }

        override suspend fun log(
            tag: String,
            message: String,
            type: String,
            data: String?,
        ) {
            if (!isInitialized) {
                AppLog.e("LogRepository", "Attempting to log before initialization")
                return
            }

            try {
                val logEntry =
                    LogEntity(
                        id = UUID.randomUUID().toString(),
                        accountId = currentAccountId,
                        sessionId = currentSessionId,
                        tag = tag,
                        tagId = tag,
                        type = type,
                        message = message,
                        timestamp = System.currentTimeMillis(),
                        data = data,
                    )
                logDao.insertLog(logEntry)
            } catch (e: Exception) {
                AppLog.e("LogRepository", "Failed to log message", e.toString())
            }
        }

        override fun getAllLogs(): Flow<List<LogEntity>> = logDao.getAllLogs()

        override fun getLogsByType(type: ILogRepository.LogType): Flow<List<LogEntity>> =
            logDao.getLogsByType(type.name)

        override fun getLogsForLastDays(days: Int): Flow<List<LogEntity>> {
            val startTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            return logDao.getLogsForLastDays(startTimestamp)
        }

        override suspend fun deleteLogsOlderThanDays(days: Int) {
            val startTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            logDao.deleteLogsOlderThanDays(startTimestamp)
        }

        override suspend fun deleteAllLogs() {
            logDao.deleteAllLogs()
        }

        override suspend fun deleteLogsByAccountId(accountId: String) {
            logDao.deleteLogsByAccountId(accountId)
        }

        override suspend fun clearLogs() {
            try {
                logDao.deleteAllLogs()
                AppLog.d("LogRepository", "All logs cleared")
            } catch (e: Exception) {
                AppLog.e("LogRepository", "Failed to clear logs", e.toString())
                throw e
            }
        }

        fun updateAccountId(accountId: String) {
            currentAccountId = accountId
            AppLog.d("LogRepository", "Account ID updated to: $accountId")
        }

        fun resetSession() {
            currentSessionId = UUID.randomUUID().toString()
            AppLog.d("LogRepository", "New session started: $currentSessionId")
        }
    }
