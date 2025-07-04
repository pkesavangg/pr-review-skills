package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.service.AppStatusService
import com.greatergoods.meapp.core.shared.utilities.DeviceInfoUtil
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.ISupportAPI
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.log.LogEntity
import com.greatergoods.meapp.domain.model.api.support.DeviceInfo
import com.greatergoods.meapp.domain.model.api.support.LogEntry
import com.greatergoods.meapp.domain.model.api.support.SendLogRequest
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.domain.services.IAccountService
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
        private val supportAPI: ISupportAPI,
        private val accountService: IAccountService,
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

        override suspend fun sendLogs() {
            try {
                AppLog.i("LogRepository", "Log sending initiated")
                // Sync current account ID
                syncCurrentAccountId()
                // Get recent logs from the last 5 days for current account only
                val recentLogEntities = getLogsByAccountId(currentAccountId).first()
                    .filter {
                        val fiveDaysAgo = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L)
                        it.timestamp >= fiveDaysAgo
                    }
                AppLog.i("LogRepository", "Sending ${recentLogEntities.size} logs for account: $currentAccountId")
                // Convert LogEntity to LogEntry for API transmission
                val logEntries = recentLogEntities.map { LogEntry.from(it) }
                // Create device info for debugging context
                val deviceInfo = DeviceInfo(
                    platform = DeviceInfoUtil.getOSVersion(),
                    osVersion = DeviceInfoUtil.getAppVersion(),
                    deviceModel = "${DeviceInfoUtil.getManufacturer()} ${DeviceInfoUtil.getModel()}",
                    apiUrl = AppStatusService.apiUrl,
                    timezone = AppStatusService.getUserTimezone(),
                    timezoneOffset = AppStatusService.getUserTimezoneOffset()
                )
                // Create the request payload
                val sendLogRequest = SendLogRequest(
                    logs = logEntries,
                    version = AppStatusService.version,
                )

                // Send logs to support API (POST /support/log)
                val response = supportAPI.sendLog(sendLogRequest)

                if (response.isSuccessful) {
                    val responseText = response.body()?.string() ?: "No response body"
                    AppLog.i("LogRepository", "Logs sent successfully. Response: $responseText")
                } else {
                    val errorText = response.errorBody()?.string() ?: "Unknown error"
                    AppLog.e("LogRepository", "Failed to send logs. HTTP ${response.code()}: $errorText")
                    throw Exception("Failed to send logs: HTTP ${response.code()} - $errorText")
                }
            } catch (e: Exception) {
                AppLog.e("LogRepository", "Failed to send logs", e.toString())
                throw e
            }
        }

        override suspend fun sendLogsForCurrentAccount() {
            // This is now the default behavior of sendLogs()
            sendLogs()
        }

        override suspend fun clearLogsForCurrentAccount() {
            try {
                // Sync current account ID
                syncCurrentAccountId()
                AppLog.i("LogRepository", "Clearing logs for account: $currentAccountId")
                deleteLogsByAccountId(currentAccountId)
                AppLog.i("LogRepository", "Logs cleared for account: $currentAccountId")
            } catch (e: Exception) {
                AppLog.e("LogRepository", "Failed to clear logs for current account", e.toString())
                throw e
            }
        }

        /**
         * Updates the current account ID from the active account service.
         */
        private suspend fun syncCurrentAccountId() {
            val activeAccount = accountService.getCurrentAccount()
            currentAccountId = activeAccount?.id ?: "default"
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
