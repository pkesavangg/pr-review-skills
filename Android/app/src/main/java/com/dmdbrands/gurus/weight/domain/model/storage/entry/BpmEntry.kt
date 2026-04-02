package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity

/**
 * Represents a BPM entry, combining EntryEntity and BpmEntryEntity.
 */
data class BpmEntry(
    override val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity,
) : Entry() {

    /** Systolic blood pressure reading (mmHg). */
    val systolic: Int get() = bpmEntry.systolic

    /** Diastolic blood pressure reading (mmHg). */
    val diastolic: Int get() = bpmEntry.diastolic

    /** Pulse / heart rate (bpm). */
    val pulse: Int get() = bpmEntry.pulse

    /** Mean arterial pressure. */
    val meanArterial: String get() = bpmEntry.meanArterial

    /** Optional note attached to the entry. */
    val note: String? get() = bpmEntry.note
}
