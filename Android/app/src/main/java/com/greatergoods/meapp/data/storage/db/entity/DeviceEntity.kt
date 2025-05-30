package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
@TypeConverters(DateConverter::class, JsonConverter::class)
data class DeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "accountId")
    val accountId: String,

    @ColumnInfo(name = "peripheralIdentifier")
    val peripheralIdentifier: String?,

    @ColumnInfo(name = "nickname")
    val nickname: String?,

    @ColumnInfo(name = "sku")
    val sku: String?,

    @ColumnInfo(name = "mac")
    val mac: String?,

    @ColumnInfo(name = "password")
    val password: String?,

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deviceName")
    val deviceName: String?,

    @ColumnInfo(name = "deviceType")
    val deviceType: String?,

    @ColumnInfo(name = "broadcastId")
    val broadcastId: String?,

    @ColumnInfo(name = "broadcastIdString")
    val broadcastIdString: String?,

    @ColumnInfo(name = "userNumber")
    val userNumber: String?,

    @ColumnInfo(name = "protocolType")
    val protocolType: String?,

    @ColumnInfo(name = "createdAt")
    val createdAt: String?,

    @ColumnInfo(name = "lastModified")
    val lastModified: Long?,

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "isConnected")
    val isConnected: Boolean = false,

    @ColumnInfo(name = "wifiMac")
    val wifiMac: String?,

    @ColumnInfo(name = "isWifiConfigured")
    val isWifiConfigured: Boolean = false,

    @ColumnInfo(name = "token")
    val token: String?
)
