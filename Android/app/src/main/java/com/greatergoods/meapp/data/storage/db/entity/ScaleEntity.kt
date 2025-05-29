package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a scale in the database.
 * Extends DeviceEntity through a one-to-one relationship.
 */
@Entity(
    tableName = "scale",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScaleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // This is both PK and FK to device.id

    @ColumnInfo(name = "scaleType")
    val scaleType: String?,

    @ColumnInfo(name = "bodyComp")
    val bodyComp: Boolean = false
)