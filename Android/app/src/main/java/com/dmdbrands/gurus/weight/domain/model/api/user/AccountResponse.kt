package com.dmdbrands.gurus.weight.domain.model.api.user

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
    val preferredInputMethod: String?,
    val height: Int,
    val activityLevel: String,
    val dob: String,
    val weightlessBodyFat: Float?,
    val weightlessMuscle: Float?,
    val weightlessTimestamp: String?,
    val weightlessWeight: Float?,
    val isStreakOn: Boolean,
    val dashboardType: String,
    val dashboardMetrics: List<String>,
    val goalType: String? = null,
    val goalWeight: Float?,
    val initialWeight: Float?,
    val metPreviousGoal: Boolean = false,      // whether previous goal was met
    val goalPercent: Int = 0,
    val shouldSendEntryNotifications: Boolean,
    val shouldSendWeightInEntryNotifications: Boolean,
    val isFitbitOn: Boolean = false,
    val isFitbitValid: Boolean = false,
    val isHealthConnectOn: Boolean = false,
    val isHealthKitOn: Boolean = false,
    val isMFPOn: Boolean = false,
    val isMFPValid: Boolean = false,
)
