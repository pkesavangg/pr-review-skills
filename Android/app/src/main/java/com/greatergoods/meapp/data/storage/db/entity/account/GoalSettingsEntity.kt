package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

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
