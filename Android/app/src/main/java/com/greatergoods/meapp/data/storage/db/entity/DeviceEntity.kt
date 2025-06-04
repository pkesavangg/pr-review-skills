package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.DateConverter
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter

/**
 * Entity class representing a device in the database.
 * Maps to the 'device' table in the SQLite database.
 */
@Entity(
    tableName = "device",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
@TypeConverters(DateConverter::class, JsonConverter::class)
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val peripheralIdentifier: String?,
    val nickname: String?,
    val sku: String?,
    val mac: String?,
    val password: String?,
    val isDeleted: Boolean = false,
    val deviceName: String?,
    val deviceType: String?,
    val broadcastId: String?,
    val broadcastIdString: String?,
    val userNumber: String?,
    val protocolType: String?,
    val createdAt: String?,
    val lastModified: Long?,
    val isSynced: Boolean = false,
    val isConnected: Boolean = false,
    val wifiMac: String?,
    val isWifiConfigured: Boolean = false,
    val token: String?,
)
