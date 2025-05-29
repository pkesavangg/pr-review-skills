package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a scale in the database.
 * Maps to the 'scale' table in the SQLite database.
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
    val id: String,

    @ColumnInfo(name = "scaleType")
    val scaleType: String?,

    @ColumnInfo(name = "bodyComp")
    val bodyComp: Boolean = false
) : BaseEntity() 