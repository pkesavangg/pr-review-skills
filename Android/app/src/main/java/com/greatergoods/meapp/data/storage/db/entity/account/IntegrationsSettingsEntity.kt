package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

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
