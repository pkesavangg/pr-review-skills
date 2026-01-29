package com.dmdbrands.gurus.weight.core.config

import java.util.concurrent.TimeUnit

object NetworkConfig {
    val PUBLIC_ENDPOINTS =
        setOf(
            "account/login",
            "refresh-token",
            "account/forgot-password",
        )

    fun isPublicEndpoint(path: String): Boolean = PUBLIC_ENDPOINTS.any { path.endsWith(it, ignoreCase = true) }

    // OkHttp timeout constants
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
}
