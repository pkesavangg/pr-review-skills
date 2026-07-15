package com.dmdbrands.gurus.weight.data.storage.db.entity.entry

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
    /** PK and FK to entry.id — matches production 5.0.0 schema. Do not rename. */
    @PrimaryKey
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val meanArterial: String,
    val note: String?,
    /**
     * Measurement origin ("manual" / "bluetooth" / …) — distinguishes manually-entered readings
     * (fully editable) from device-synced ones (note-only). Nullable for pre-v9 rows / legacy
     * entries. (MOB-1173)
     */
    val source: String? = null,
)
