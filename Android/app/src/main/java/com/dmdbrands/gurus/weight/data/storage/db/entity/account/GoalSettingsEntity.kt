package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_settings",
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
    val goalType: String?,
    val weight: Float,
    val goalWeight: String,
    val goalPercent: Float,
    val isSynced: Boolean
)
