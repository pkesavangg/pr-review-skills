package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a scale entry in the database.
 * Maps to the 'scale_entry' table in the SQLite database.
 */
@Entity(
    tableName = "scale_entry",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScaleEntryEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "weight")
    val weight: Int,

    @ColumnInfo(name = "bodyFat")
    val bodyFat: Int,

    @ColumnInfo(name = "muscleMass")
    val muscleMass: Int,

    @ColumnInfo(name = "water")
    val water: Int,

    @ColumnInfo(name = "bmi")
    val bmi: Int,

    @ColumnInfo(name = "source")
    val source: String
) : BaseEntity() 