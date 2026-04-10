package com.dmdbrands.gurus.weight.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity

/**
 * Entity representing a baby-specific entry in the database.
 * Links to [EntryEntity] for timestamp/account data and to [BabyProfileEntity]
 * for the baby this entry belongs to.
 *
 * Fields mirror the legacy babyApp Ionic schema. Nullable fields that are not
 * yet used in Phase 2 are kept for forward compatibility (avoids future migration).
 */
@Entity(
    tableName = "baby_entry",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BabyProfileEntity::class,
            parentColumns = ["babyId"],
            childColumns = ["babyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["babyId"]),
    ],
)
data class BabyEntryEntity(
    @PrimaryKey
    val id: Long,
    val babyId: String,

    // Weight & length (Phase 2 — active)
    val babyWeightDecigrams: Int? = null,
    val babyLengthMillimeters: Int? = null,
    val entryNote: String? = null,
    val entryType: String? = null,

    // Feeding (future)
    val feedingTimeLeft: Int? = null,
    val feedingTimeRight: Int? = null,
    val feedingMilliliters: Int? = null,

    // Diaper (future)
    val diaperType: String? = null,

    // Sleep (future)
    val sleepTime: Int? = null,

    // Display / sync
    val babyDisplayWeightDecigrams: Int? = null,
    val photoUri: String? = null,
    val isPlaceholder: Boolean? = null,
    val source: String? = null,
)
