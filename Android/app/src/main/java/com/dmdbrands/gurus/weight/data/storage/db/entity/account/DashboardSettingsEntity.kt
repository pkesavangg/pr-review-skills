package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.dmdbrands.gurus.weight.data.storage.db.converter.JsonConverter

/**
 * Entity for storing dashboard settings including visible metrics and milestones.
 * Replaces the DashboardKeysDatastore functionality with DAO-based storage.
 */
@Entity(
    tableName = "dashboard_settings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(JsonConverter::class)
data class DashboardSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val dashboardMetrics: List<String>, // List of metric keys
    val dashboardMilestones: List<String>, // List of milestone keys
    val dashboardType: String,
    val isSynced: Boolean
)
