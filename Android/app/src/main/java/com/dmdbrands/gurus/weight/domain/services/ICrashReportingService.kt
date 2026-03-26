package com.dmdbrands.gurus.weight.domain.services

interface ICrashReportingService {
    fun initialize()

    fun recordException(throwable: Throwable, tag: String? = null)

    fun setCustomKey(key: String, value: String)

    fun log(message: String)

    fun setCollectionEnabled(enabled: Boolean)
}
