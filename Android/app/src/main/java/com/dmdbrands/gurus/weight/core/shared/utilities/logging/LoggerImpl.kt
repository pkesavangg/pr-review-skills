package com.dmdbrands.gurus.weight.core.shared.utilities.logging

import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggerImpl
    @Inject
    constructor(
        private val logRepository: ILogRepository,
        private val loggerScope: CoroutineScope,
    ) : ILogger {
        private var isInitialized = false

        override suspend fun initialize() {
            if (isInitialized) return
            isInitialized = true
            logRepository.initialize()
            Timber.d("Logger initialized")
        }

        override fun d(
            tag: String,
            message: String,
            data: String?,
        ) {
            Timber.tag(tag).d(message)
            logToDb(tag, message, "d", data)
        }

        override fun i(
            tag: String,
            message: String,
            data: String?,
        ) {
            Timber.tag(tag).i(message)
            logToDb(tag, message, "i", data)
        }

        override fun w(
            tag: String,
            message: String,
            data: String?,
        ) {
            Timber.tag(tag).w(message)
            logToDb(tag, message, "w", data)
        }

        override fun e(
            tag: String,
            message: String,
            data: String?,
        ) {
            Timber.tag(tag).e(message)
            logToDb(tag, message, "e", data)
        }

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable,
        ) {
            Timber.tag(tag).e(throwable, message)
            logToDb(tag, message, "e", throwable.toString())
        }

        private fun logToDb(
            tag: String,
            message: String,
            type: String,
            data: String?,
        ) {
            if (!isInitialized) {
                Timber.e("Logger not initialized")
                return
            }

            loggerScope.launch {
                try {
                    logRepository.log(tag, message, type, data)
                } catch (e: Exception) {
                    Timber.tag("Logger").e("Failed to log to DB: ${e.message}")
                }
            }
        }

        override fun reset() {
            isInitialized = false
            Timber.d("Logger reset")
        }
    }
