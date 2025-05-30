package com.greatergoods.meapp.domain.model.api

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String
)
