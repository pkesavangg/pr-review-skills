package com.greatergoods.meapp.domain.model.api.user

data class AccountResponse(
    val id: String,
    val email: String,
    val name: String?,
    val height: Double?,
    val activityLevel: String?,
    val weightUnit: String?,
    val dashboardType: String?,
    val createdAt: String,
    val updatedAt: String
)
