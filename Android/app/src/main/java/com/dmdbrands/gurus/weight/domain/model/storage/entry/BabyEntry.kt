package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity

/**
 * Represents a baby entry, combining [EntryEntity] and [BabyEntryEntity].
 * Convenience accessors delegate to the underlying [babyEntry] entity.
 */
data class BabyEntry(
    override val entry: EntryEntity,
    val babyEntry: BabyEntryEntity,
) : Entry() {

    val babyId: String get() = babyEntry.babyId
    val babyWeightDecigrams: Int? get() = babyEntry.babyWeightDecigrams
    val babyLengthMillimeters: Int? get() = babyEntry.babyLengthMillimeters
    val entryNote: String? get() = babyEntry.entryNote
    val entryType: String? get() = babyEntry.entryType
}
