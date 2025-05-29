package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
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
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BpmEntryEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "systolic")
    val systolic: Int,

    @ColumnInfo(name = "diastolic")
    val diastolic: Int,

    @ColumnInfo(name = "pulse")
    val pulse: Int,

    @ColumnInfo(name = "meanArterial")
    val meanArterial: String,

    @ColumnInfo(name = "note")
    val note: String?
)