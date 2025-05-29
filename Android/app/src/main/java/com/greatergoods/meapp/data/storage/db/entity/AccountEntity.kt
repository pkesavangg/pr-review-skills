package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entity class representing the account table in the database.
 * Stores user account details such as personal info, tokens, and app settings.
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val id: String,

    @ColumnInfo(name = "access_token")
    val accessToken: String? = null,

    @ColumnInfo(name = "activity_level")
    val activityLevel: String? = null,

    @ColumnInfo(name = "dashboard_metrics")
    val dashboardMetrics: String? = null,

    @ColumnInfo(name = "dashboard_type")
    val dashboardType: String? = null,

    @ColumnInfo(name = "dob")
    val dob: String? = null,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "expires_at")
    val expiresAt: String? = null,

    @ColumnInfo(name = "fcm_token")
    val fcmToken: String? = null,

    @ColumnInfo(name = "first_name")
    val firstName: String? = null,

    @ColumnInfo(name = "gender")
    val gender: String? = null,

    @ColumnInfo(name = "goal_type")
    val goalType: String? = null,

    @ColumnInfo(name = "goal_weight")
    val goalWeight: String? = null,

    @ColumnInfo(name = "height")
    val height: String? = null,

    @ColumnInfo(name = "initial_weight")
    val initialWeight: Float? = null,

    @ColumnInfo(name = "is_active_account")
    val isActiveAccount: Boolean = false,

    @ColumnInfo(name = "is_fitbit_on")
    val isFitbitOn: Boolean = false,

    @ColumnInfo(name = "is_fitbit_valid")
    val isFitbitValid: Boolean = false,

    @ColumnInfo(name = "is_google_fit_on")
    val isGoogleFitOn: Boolean = false,

    @ColumnInfo(name = "is_google_fit_valid")
    val isGoogleFitValid: Boolean = false,

    @ColumnInfo(name = "is_health_connect_on")
    val isHealthConnectOn: Boolean = false,

    @ColumnInfo(name = "is_health_kit_on")
    val isHealthKitOn: Boolean = false,

    @ColumnInfo(name = "is_logged_in")
    val isLoggedIn: Boolean = false,

    @ColumnInfo(name = "is_expired")
    val isExpired: Boolean = false,

    @ColumnInfo(name = "is_mfp_on")
    val isMFPOn: Boolean = false,

    @ColumnInfo(name = "is_mfp_valid")
    val isMFPValid: Boolean = false,

    @ColumnInfo(name = "is_streak_on")
    val isStreakOn: Boolean = false,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "is_ua_on")
    val isUAOn: Boolean = false,

    @ColumnInfo(name = "is_ua_valid")
    val isUAValid: Boolean = false,

    @ColumnInfo(name = "is_weightless_on")
    val isWeightlessOn: Boolean = false,

    @ColumnInfo(name = "last_active_time")
    val lastActiveTime: String? = null,

    @ColumnInfo(name = "last_name")
    val lastName: String? = null,

    @ColumnInfo(name = "met_previous_goal")
    val metPreviousGoal: Boolean = false,

    @ColumnInfo(name = "percent")
    val percent: Float? = null,

    @ColumnInfo(name = "preferred_input_method")
    val preferredInputMethod: String? = null,

    @ColumnInfo(name = "refresh_token")
    val refreshToken: String? = null,

    @ColumnInfo(name = "should_send_entry_notifications")
    val shouldSendEntryNotifications: Boolean = false,

    @ColumnInfo(name = "should_send_weight_in_entry_notifications")
    val shouldSendWeightInEntryNotifications: Boolean = false,

    @ColumnInfo(name = "streak_timestamp")
    val streakTimestamp: String? = null,

    @ColumnInfo(name = "type")
    val type: String? = null,

    @ColumnInfo(name = "weight_unit")
    val weightUnit: String? = null,

    @ColumnInfo(name = "weightless_body_fat")
    val weightlessBodyFat: Float? = null,

    @ColumnInfo(name = "weightless_muscle")
    val weightlessMuscle: Float? = null,

    @ColumnInfo(name = "weightless_timestamp")
    val weightlessTimestamp: String? = null,

    @ColumnInfo(name = "weightless_weight")
    val weightlessWeight: Float? = null,

    @ColumnInfo(name = "zipcode")
    val zipcode: String? = null
) 