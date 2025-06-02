package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a body scale entry in the database.
 * Maps to the 'body_scale_entry' table in the SQLite database.
 */
@Entity(
    tableName = "body_scale_entry",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BodyScaleEntryEntity(
    @PrimaryKey
    val id: Long,
    val weight: Int,
    val bodyFat: Int,
    val muscleMass: Int,
    val water: Int,
    val bmi: Int,
    val source: String,
)
