package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing device metadata in the database.
 * Maps to the 'device_meta_data' table in the SQLite database.
 */
@Entity(
    tableName = "device_meta_data",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DeviceMetaDataEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "modelNumber")
    val modelNumber: String?,

    @ColumnInfo(name = "serialNumber")
    val serialNumber: String?,

    @ColumnInfo(name = "latestVersion")
    val latestVersion: String?,

    @ColumnInfo(name = "firmwareRevision")
    val firmwareRevision: String?,

    @ColumnInfo(name = "hardwareRevision")
    val hardwareRevision: String?,

    @ColumnInfo(name = "softwareRevision")
    val softwareRevision: String?,

    @ColumnInfo(name = "manufacturerName")
    val manufacturerName: String?,

    @ColumnInfo(name = "systemId")
    val systemId: String?
) : BaseEntity() 