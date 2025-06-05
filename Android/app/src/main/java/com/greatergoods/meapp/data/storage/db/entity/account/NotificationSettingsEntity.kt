package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_settings",
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
