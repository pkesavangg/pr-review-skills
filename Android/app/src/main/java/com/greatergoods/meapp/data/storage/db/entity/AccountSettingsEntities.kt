package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "weightCompSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WeightCompSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val height: String,
    val activityLevel: String,
    val weightUnit: String,
    val isSynced: Boolean
)

@Entity(
    tableName = "goalSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val goalType: String,
    val weight: Float,
    val goalWeight: String,
    val goalPercent: Float,
    val isSynced: Boolean
)

@Entity(
    tableName = "streaksSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StreaksSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val isStreakOn: Boolean,
    val streakTimestamp: String,
    val isSynced: Boolean
)

@Entity(
    tableName = "weightlessSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WeightlessSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val isWeightlessOn: Boolean,
    val weightlessTimestamp: String,
    val weightlessWeight: Float,
    val isSynced: Boolean
)

@Entity(
    tableName = "notificationSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NotificationSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val entryNotificationsEnabled: Boolean,
    val showWeightInNotifications: Boolean,
    val isSynced: Boolean
)

@Entity(
    tableName = "dashboardSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DashboardSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val dashboardMetrics: String,
    val dashboardType: String,
    val isSynced: Boolean
)

@Entity(
    tableName = "integrationsSettings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IntegrationsSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val isFitbitOn: Boolean,
    val isFitbitValid: Boolean,
    val isGoogleFitOn: Boolean,
    val isGoogleFitValid: Boolean,
    val isHealthConnectOn: Boolean,
    val isHealthKitOn: Boolean,
    val isUaOn: Boolean,
    val isUaValid: Boolean,
    val isMfpOn: Boolean,
    val isMfpValid: Boolean,
    val isSynced: Boolean
) 