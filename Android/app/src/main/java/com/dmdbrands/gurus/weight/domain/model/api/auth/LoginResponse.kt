package com.dmdbrands.gurus.weight.domain.model.api.auth

import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo

data class LoginResponse(
    val id: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val account: AccountInfo
)


