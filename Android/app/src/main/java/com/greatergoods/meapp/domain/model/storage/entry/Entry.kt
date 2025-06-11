package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity

/**
 * Sealed class representing a database entry, which can be either a scale entry or a BPM entry.
 * All subclasses must override the [entry] property.
 */
sealed class Entry {
    /**
     * The core entry entity for this record.
     */
    abstract val entry: EntryEntity
    fun updateEntry(entry: EntryEntity): Entry {
        return when (this) {
            is ScaleEntry -> this.copy(entry = entry)
            is BpmEntry -> this.copy(entry = entry)
        }
    }
}



