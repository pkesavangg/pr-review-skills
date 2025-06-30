package com.greatergoods.meapp.domain.model.api.user

data class AccountResponse(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: String?,
    val account: AccountInfo
)


data class AccountInfo(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val zipcode: String,
    val weightUnit: String,
    val isWeightlessOn: Boolean,
    val height: Int,
    val activityLevel: String,
    val dob: String,
    val weightlessTimestamp: String?,   // nullable
    val weightlessWeight: Float?,       // nullable
    val isStreakOn: Boolean,
    val dashboardType: String,
    val dashboardMetrics: List<String>
)
