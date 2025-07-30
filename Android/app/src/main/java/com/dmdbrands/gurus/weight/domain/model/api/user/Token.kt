package com.dmdbrands.gurus.weight.domain.model.api.user

data class Token(
    val accountId: String,
    val isActive: Boolean = false,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: String?
)
