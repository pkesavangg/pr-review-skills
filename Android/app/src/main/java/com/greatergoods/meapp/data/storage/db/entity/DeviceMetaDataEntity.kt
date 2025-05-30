package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing device metadata in the database.
 * Extends DeviceEntity through a one-to-one relationship.
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

    val modelNumber: String?,

    val serialNumber: String?,

    val firmwareRevision: String?,

    val hardwareRevision: String?,

    val softwareRevision: String?,

    val manufacturerName: String?,

    val systemId: String?,

    val latestVersion: String?
)