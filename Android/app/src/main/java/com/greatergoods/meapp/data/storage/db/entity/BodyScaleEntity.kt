package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a body scale in the database.
 * Extends DeviceEntity through a one-to-one relationship.
 */
@Entity(
    tableName = "body_scale",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BodyScaleEntity(
    @PrimaryKey
    val id: String, // This is both PK and FK to device.id

    val scaleType: String?,

    val bodyComp: Boolean = false
)