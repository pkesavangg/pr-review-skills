package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import androidx.room.ColumnInfo
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
    val shouldSendEntryNotifications: Boolean,
    val shouldSendWeightInEntryNotifications: Boolean,
    val isSynced: Boolean,
    @ColumnInfo(defaultValue = "0")
    val willReceiveEmails: Boolean = false,
)
