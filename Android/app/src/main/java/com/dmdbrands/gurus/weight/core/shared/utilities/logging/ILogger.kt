package com.dmdbrands.gurus.weight.core.shared.utilities.logging

interface ILogger {
    suspend fun initialize()

    fun d(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun i(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun w(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun e(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    )

    fun v(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun a(
        tag: String,
        message: String,
        data: String? = null,
    )

    fun reset()
}
