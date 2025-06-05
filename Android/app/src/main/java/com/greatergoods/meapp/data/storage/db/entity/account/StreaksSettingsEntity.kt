package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

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
