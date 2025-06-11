package com.greatergoods.meapp.core.shared.utilities.logging

import com.greatergoods.meapp.data.storage.db.entity.log.LogEntity
import com.greatergoods.meapp.domain.repository.ILogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogManager
    @Inject
    constructor(
        private val logRepository: ILogRepository,
    ) {
        /**
         * Get logs for the last specified days
         * @param days Number of days to look back
         */
        fun getRecentLogs(days: Int = 1): Flow<List<LogEntity>> = logRepository.getLogsForLastDays(days)

        /**
         * Get logs by type
         * @param type LogType to filter by
         */
        fun getLogsByType(type: ILogRepository.LogType): Flow<List<LogEntity>> = logRepository.getLogsByType(type)

        /**
         * Get logs by session ID (current session)
         */
        fun getLogsBySession(): Flow<List<LogEntity>> = logRepository.getLogsBySessionId()

        /**
         * Clean up logs older than specified days
         * @param days Number of days to keep logs for
         */
        suspend fun cleanupOldLogs(days: Int = 7) {
            logRepository.deleteLogsOlderThanDays(days)
            AppLog.d("LogManager", "Cleaned up logs older than $days days")
        }

        /**
         * Delete all logs for the current account
         */
        suspend fun deleteAllLogs() {
            logRepository.deleteAllLogs()
            AppLog.d("LogManager", "Deleted all logs")
        }

        /**
         * Log an error with exception
         * @param message Log message
         * @param throwable Exception to log
         */
        fun logError(
            message: String,
            throwable: Throwable,
        ) {
            AppLog.e("LogManager", message, throwable?.toString())
        }
    }
