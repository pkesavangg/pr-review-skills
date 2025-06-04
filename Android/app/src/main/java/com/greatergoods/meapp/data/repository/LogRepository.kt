package com.greatergoods.meapp.data.repository

import android.util.Log
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.LogEntity
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.core.logging.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ILogRepository] that manages application logs.
 * Integrates with Timber for structured logging and Room for persistence.
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao
) : ILogRepository {

    private var currentSessionId: String? = null
    private var currentAccountId: String = "default"

    override suspend fun initialize() {
        currentSessionId = UUID.randomUUID().toString()
        AppLog.d("LogRepository", "Initialized with session ID: $currentSessionId")
    }

    override fun getSessionId(): String? = currentSessionId

    override fun getLogs(): Flow<List<LogEntity>> = logDao.getAllLogs()

    override fun getLogsByAccountId(accountId: String): Flow<List<LogEntity>> =
        logDao.getLogsByAccountId(accountId)

    override fun getLogsBySessionId(): Flow<List<LogEntity>> =
        currentSessionId?.let { logDao.getLogsBySessionId(it) } ?: logDao.getAllLogs()

    override suspend fun log(tag: String, message: String, type: String, data: String?) {
        val logEntry = LogEntity(
            id = UUID.randomUUID().toString(),
            accountId = currentAccountId,
            sessionId = currentSessionId ?: return,
            tag = tag,
            tagId = tag,
            type = type,
            message = message,
            timestamp = System.currentTimeMillis(),
            data = data
        )
        logDao.insertLog(logEntry)
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
        logDao.deleteAllLogs()
    }
} 