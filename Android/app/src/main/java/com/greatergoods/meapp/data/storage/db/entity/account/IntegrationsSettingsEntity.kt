package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "integrations_settings",
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
    val isHealthConnectOn: Boolean,
    val isHealthKitOn: Boolean,
    val isMFPOn: Boolean,
    val isMFPValid: Boolean,
    val isSynced: Boolean
)
