package com.dmdbrands.gurus.weight.domain.model.api.auth

data class LoginRequest(
    val email: String,
    val password: String
)
