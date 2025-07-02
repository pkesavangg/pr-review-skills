package com.greatergoods.meapp.domain.model.api.auth

data class ChangePasswordResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
)
