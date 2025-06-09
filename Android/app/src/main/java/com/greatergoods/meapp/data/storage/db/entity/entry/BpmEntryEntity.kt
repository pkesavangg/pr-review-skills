package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a blood pressure entry in the database.
 * Maps to the 'bpm_entry' table in the SQLite database.
 */
@Entity(
    tableName = "bpm_entry",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BpmEntryEntity(
    @PrimaryKey
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val meanArterial: String,
    val note: String?,
)
