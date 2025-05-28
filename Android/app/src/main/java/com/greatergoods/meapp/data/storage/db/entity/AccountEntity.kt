package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.DateConverter
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter

/**
 * Entity class representing the account table in the database.
 * Stores user account details such as personal info, tokens, and app settings.
 */
@Entity(tableName = "account")
@TypeConverters(DateConverter::class, JsonConverter::class)
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "accessToken")
    val accessToken: String? = null,

    @ColumnInfo(name = "activityLevel")
    val activityLevel: String? = null,

    @ColumnInfo(name = "dashboardMetrics")
    val dashboardMetrics: String? = null,

    @ColumnInfo(name = "dashboardType")
    val dashboardType: String? = null,

    @ColumnInfo(name = "dob")
    val dob: String? = null,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "expiresAt")
    val expiresAt: String? = null,

    @ColumnInfo(name = "fcmToken")
    val fcmToken: String? = null,

    @ColumnInfo(name = "firstName")
    val firstName: String? = null,

    @ColumnInfo(name = "gender")
    val gender: String? = null,

    @ColumnInfo(name = "goalType")
    val goalType: String? = null,

    @ColumnInfo(name = "goalWeight")
    val goalWeight: String? = null,

    @ColumnInfo(name = "height")
    val height: String? = null,

    @ColumnInfo(name = "initialWeight")
    val initialWeight: Float? = null,

    @ColumnInfo(name = "isActiveAccount")
    val isActiveAccount: Boolean = false,

    @ColumnInfo(name = "isFitbitOn")
    val isFitbitOn: Boolean = false,

    @ColumnInfo(name = "isFitbitValid")
    val isFitbitValid: Boolean = false,

    @ColumnInfo(name = "isGoogleFitOn")
    val isGoogleFitOn: Boolean = false,

    @ColumnInfo(name = "isGoogleFitValid")
    val isGoogleFitValid: Boolean = false,

    @ColumnInfo(name = "isHealthConnectOn")
    val isHealthConnectOn: Boolean = false,

    @ColumnInfo(name = "isHealthKitOn")
    val isHealthKitOn: Boolean = false,

    @ColumnInfo(name = "isLoggedIn")
    val isLoggedIn: Boolean = false,

    @ColumnInfo(name = "isExpired")
    val isExpired: Boolean = false,

    @ColumnInfo(name = "isMFPOn")
    val isMFPOn: Boolean = false,

    @ColumnInfo(name = "isMFPValid")
    val isMFPValid: Boolean = false,

    @ColumnInfo(name = "isStreakOn")
    val isStreakOn: Boolean = false,

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "isUAOn")
    val isUAOn: Boolean = false,

    @ColumnInfo(name = "isUAValid")
    val isUAValid: Boolean = false,

    @ColumnInfo(name = "isWeightlessOn")
    val isWeightlessOn: Boolean = false,

    @ColumnInfo(name = "lastActiveTime")
    val lastActiveTime: String? = null,

    @ColumnInfo(name = "lastName")
    val lastName: String? = null,

    @ColumnInfo(name = "metPreviousGoal")
    val metPreviousGoal: Boolean = false,

    @ColumnInfo(name = "percent")
    val percent: Float? = null,

    @ColumnInfo(name = "preferredInputMethod")
    val preferredInputMethod: String? = null,

    @ColumnInfo(name = "refreshToken")
    val refreshToken: String? = null,

    @ColumnInfo(name = "shouldSendEntryNotifications")
    val shouldSendEntryNotifications: Boolean = false,

    @ColumnInfo(name = "shouldSendWeightInEntryNotifications")
    val shouldSendWeightInEntryNotifications: Boolean = false,

    @ColumnInfo(name = "streakTimestamp")
    val streakTimestamp: String? = null,

    @ColumnInfo(name = "type")
    val type: String? = null,

    @ColumnInfo(name = "weightUnit")
    val weightUnit: String? = null,

    @ColumnInfo(name = "weightlessBodyFat")
    val weightlessBodyFat: Float? = null,

    @ColumnInfo(name = "weightlessMuscle")
    val weightlessMuscle: Float? = null,

    @ColumnInfo(name = "weightlessTimestamp")
    val weightlessTimestamp: String? = null,

    @ColumnInfo(name = "weightlessWeight")
    val weightlessWeight: Float? = null,

    @ColumnInfo(name = "zipcode")
    val zipcode: String? = null
) 