package com.greatergoods.meapp.core.shared.utilities

import android.util.Log
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Custom Timber Tree that stores logs in the database.
 * This class handles the conversion of Timber logs to database entries.
 */
class DatabaseLoggingTree(
    private val logDao: LogDao,
    private val currentAccountId: String,
    private val sessionId: String?,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Skip if session ID is not set
        val currentSessionId = sessionId ?: return

        // Skip if this is a log from DatabaseLoggingTree itself to prevent infinite loops
        if (tag == "DatabaseLoggingTree") return

        val type = when (priority) {
            Log.ERROR -> "e"
            Log.INFO -> "i"
            Log.DEBUG -> "d"
            Log.WARN -> "w"
            Log.VERBOSE -> "v"
            Log.ASSERT -> "a"
            else -> "i"
        }

        val stackTrace = Throwable().stackTrace.getOrNull(6) // Skip Timber's internal calls
        val tagId = stackTrace?.methodName ?: "unknown"
        val className = stackTrace?.className?.substringAfterLast('.') ?: (tag ?: "unknown")

        val logEntry = LogEntity(
            id = UUID.randomUUID().toString(),
            accountId = currentAccountId,
            sessionId = currentSessionId,
            tag = className,
            tagId = tagId,
            type = type,
            message = message,
            timestamp = System.currentTimeMillis(),
            data = t?.stackTraceToString()
        )

        ioScope.launch {
            try {
                logDao.insertLog(logEntry)
            } catch (e: Exception) {
                // Use Android's Log directly to avoid infinite recursion
                Log.e("DatabaseLoggingTree", "Failed to insert log: ${e.message}")
            }
        }
    }
} 