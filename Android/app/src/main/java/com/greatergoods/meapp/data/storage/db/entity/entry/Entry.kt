package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.domain.model.storage.EntryDTO

/**
 * Wrapper class that combines EntryEntity with its related entities (BpmEntry, ScaleEntry, ScaleEntryMetric).
 * This makes it easier to fetch related data in a single query.
 */
data class Entry(
    @Embedded val entry: EntryEntity,
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
    /**
     * Converts this Entry wrapper to its database entities.
     * This is used when saving data back to the database.
     */
    fun toDTO(): EntryDTO =
        EntryDTO(
            entry,
            bpmEntry,
            scaleEntry,
            scaleEntryMetric,
        )
}

fun fromDTO(dto: EntryDTO): Entry =
    Entry(
        entry = dto.entry,
        bpmEntry = dto.bpmEntry,
        scaleEntry = dto.scaleEntry,
        scaleEntryMetric = dto.scaleEntryMetric,
    )
