package com.greatergoods.meapp.domain.model.api.auth

data class LoginRequest(
    val email: String,
    val password: String
)
