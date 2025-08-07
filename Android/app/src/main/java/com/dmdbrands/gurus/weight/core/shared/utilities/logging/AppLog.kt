package com.dmdbrands.gurus.weight.core.shared.utilities.logging

import timber.log.Timber

object AppLog {
    private var logger: ILogger? = null

    fun initialize(logger: ILogger) {
        this.logger = logger
        Timber.d("AppLog initialized")
    }

    fun d(
        tag: String,
        message: String,
        data: String? = null,
    ) {
        Timber.tag(tag).d(message)
        logger?.d(tag, message, data)
    }

    fun i(
        tag: String,
        message: String,
        data: String? = null,
    ) {
        Timber.tag(tag).i(message)
        logger?.i(tag, message, data)
    }

    fun w(
        tag: String,
        message: String,
        data: String? = null,
    ) {
        Timber.tag(tag).w(message)
        logger?.w(tag, message, data)
    }

    fun e(
        tag: String,
        message: String,
        data: String? = null,
    ) {
        Timber.tag(tag).e(message)
        logger?.e(tag, message, data)
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) {
        Timber.tag(tag).e(throwable, message)
        logger?.e(tag, message, throwable)
    }
}
