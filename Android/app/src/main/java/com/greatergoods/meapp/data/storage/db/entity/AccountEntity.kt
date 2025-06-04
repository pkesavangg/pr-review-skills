package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing the account table in the database.
 * Stores user account details such as personal info, tokens, and app settings.
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "accountId")
    val id: String,
    val accessToken: String? = null,
    val activityLevel: String? = null,
    val dashboardMetrics: String? = null,
    val dashboardType: String? = null,
    val dob: String? = null,
    val email: String? = null,
    val expiresAt: String? = null,
    val fcmToken: String? = null,
    val firstName: String? = null,
    val gender: String? = null,
    val goalType: String? = null,
    val goalWeight: String? = null,
    val height: String? = null,
    val initialWeight: Float? = null,
    val isActiveAccount: Boolean = false,
    val isFitbitOn: Boolean = false,
    val isFitbitValid: Boolean = false,
    val isGoogleFitOn: Boolean = false,
    val isGoogleFitValid: Boolean = false,
    val isHealthConnectOn: Boolean = false,
    val isHealthKitOn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isExpired: Boolean = false,
    val isMFPOn: Boolean = false,
    val isMFPValid: Boolean = false,
    val isStreakOn: Boolean = false,
    val isSynced: Boolean = false,
    val isUAOn: Boolean = false,
    val isUAValid: Boolean = false,
    val isWeightlessOn: Boolean = false,
    val lastActiveTime: String? = null,
    val lastName: String? = null,
    val metPreviousGoal: Boolean = false,
    val percent: Float? = null,
    val preferredInputMethod: String? = null,
    val refreshToken: String? = null,
    val shouldSendEntryNotifications: Boolean = false,
    val shouldSendWeightInEntryNotifications: Boolean = false,
    val streakTimestamp: String? = null,
    val weightUnit: String? = null,
    val weightlessBodyFat: Float? = null,
    val weightlessMuscle: Float? = null,
    val weightlessTimestamp: String? = null,
    val weightlessWeight: Float? = null,
    val zipcode: String? = null,
)
