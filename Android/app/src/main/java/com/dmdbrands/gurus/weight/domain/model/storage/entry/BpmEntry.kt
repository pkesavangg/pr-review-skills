package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity

/**
 * Represents a BPM entry, combining EntryEntity and BpmEntryEntity.
 */
data class BpmEntry(
    override val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity
) : Entry()
