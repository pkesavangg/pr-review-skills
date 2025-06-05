package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.data.storage.db.entity.Entry.Companion.fromViewEntry

/**
 * Wrapper class that combines ValidEntryEntity with its related entities.
 * This is useful for working with valid entries and their associated data.
 */
data class EntryView(
    @Embedded val entry: EntryViewEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
    )
    val bpmEntry: BpmEntryEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
    )
    val scaleEntry: BodyScaleEntryEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
    )
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
) {

    fun toEntry(): Entry = fromViewEntry(this)
}


