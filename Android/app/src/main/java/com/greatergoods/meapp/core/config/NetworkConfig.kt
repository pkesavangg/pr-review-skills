package com.greatergoods.meapp.core.config

object NetworkConfig {
    val PUBLIC_ENDPOINTS = setOf(
        "account/login",
        "refresh-token",
        "account/forgot-password",
    )

    fun isPublicEndpoint(path: String): Boolean {
        return PUBLIC_ENDPOINTS.any { path.endsWith(it, ignoreCase = true) }
    }
}
