package com.greatergoods.meapp.data.storage.db.entity.account

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
