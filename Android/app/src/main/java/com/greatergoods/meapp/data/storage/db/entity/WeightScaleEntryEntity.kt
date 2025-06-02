package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a weight scale entry in the database.
 * Maps to the 'weight_scale_entry' table in the SQLite database.
 */
@Entity(
    tableName = "weight_scale_entry",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WeightScaleEntryEntity(
    @PrimaryKey
    val id: Long,

    val weight: Int,

    val bodyFat: Int,

    val muscleMass: Int,

    val water: Int,

    val bmi: Int,

    val source: String
)