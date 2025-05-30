package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a blood pressure monitor in the database.
 * Maps to the 'bpm' table in the SQLite database.
 */
@Entity(
    tableName = "bpm",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BpmEntity(
    @PrimaryKey
    val id: String,

    val hasNumericUsers: Boolean = false
) 