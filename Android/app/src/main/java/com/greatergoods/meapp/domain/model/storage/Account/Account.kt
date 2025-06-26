package com.greatergoods.meapp.domain.model.storage.Account

/**
 * Domain model representing a user account and its settings.
 */
data class Account(
    val id: String,
    val firstName: String,
    val lastName: String,
    val dob: String,
    val email: String,
    val expiresAt: String? = null,
    val fcmToken: String? = null,
    val gender: String,
    val isActiveAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isExpired: Boolean = false,
    val isSynced: Boolean = false,
    val lastActiveTime: String? = null,
    val zipcode: String,

    // Add other settings as needed, or use separate domain models
    val weightUnit: String?,
    val isWeightlessOn: Boolean? = false,
    val height: Int?,
    val activityLevel: String?,
    val weightlessTimestamp: String?,   // nullable
    val weightlessWeight: Float?,       // nullable
    val isStreakOn: Boolean?,
    val dashboardType: String?,
    val dashboardMetrics: List<String>?
)
