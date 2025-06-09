package com.greatergoods.meapp.domain.model.api.user

data class Token(
    val id: String,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: String?
) 