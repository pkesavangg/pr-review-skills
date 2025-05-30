package com.greatergoods.meapp.domain.model.api.user

data class CreateAccountRequest(
    val device: String,
    val email: String,
    val password: String
)
