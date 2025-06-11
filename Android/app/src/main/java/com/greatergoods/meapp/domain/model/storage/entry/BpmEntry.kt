package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity

/**
 * Represents a BPM entry, combining EntryEntity and BpmEntryEntity.
 */
data class BpmEntry(
    override val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity
) : Entry()
