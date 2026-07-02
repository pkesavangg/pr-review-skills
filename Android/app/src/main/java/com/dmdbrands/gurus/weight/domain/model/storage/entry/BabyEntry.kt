package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity

/**
 * Represents a baby entry, combining [EntryEntity] and [BabyEntryEntity].
 * Convenience accessors delegate to the underlying [babyEntry] entity.
 *
 * [percentile] is a transient, read-time CDC growth percentile (0–100) for this
 * entry's measurement — it is NOT persisted. Populated by the read layer when a
 * baby profile (sex + birthdate) is known; null otherwise (sample data,
 * "private" sex, or age outside the CDC tables).
 */
data class BabyEntry(
    override val entry: EntryEntity,
    val babyEntry: BabyEntryEntity,
    val percentile: Int? = null,
) : Entry() {

    val babyId: String get() = babyEntry.babyId
    val babyWeightDecigrams: Int? get() = babyEntry.babyWeightDecigrams
    val babyLengthMillimeters: Int? get() = babyEntry.babyLengthMillimeters
    val entryNote: String? get() = babyEntry.entryNote
    val entryType: String? get() = babyEntry.entryType
}
