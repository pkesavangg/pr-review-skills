package com.greatergoods.meapp.core.logging

import android.util.Log
import com.greatergoods.meapp.domain.repository.ILogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLog {
    private val loggingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var logRepository: ILogRepository

    fun i(tag: String, message: String, data: String? = null) {
        Log.i(tag, message)
        logToDb(tag, message, "i", data)
    }

    fun d(tag: String, message: String, data: String? = null) {
        Log.d(tag, message)
        logToDb(tag, message, "d", data)
    }

    fun w(tag: String, message: String, data: String? = null) {
        Log.w(tag, message)
        logToDb(tag, message, "w", data)
    }

    fun e(tag: String, message: String, data: String? = null) {
        Log.e(tag, message)
        logToDb(tag, message, "e", data)
    }

    private fun logToDb(tag: String, message: String, type: String, data: String?) {
        if (!::logRepository.isInitialized) {
            Log.w("AppLog", "LogRepository not initialized, skipping DB log")
            return
        }
        
        loggingScope.launch {
            try {
                logRepository.log(tag, message, type, data)
            } catch (e: Exception) {
                // Log to system logs only since DB logging failed
                Log.e("AppLog", "Failed to log to DB: ${e.message}")
            }
        }
    }
} 