package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "weightless_settings",
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
