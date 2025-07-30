package com.dmdbrands.gurus.weight.domain.model.storage.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity

/**
 * Combines a BodyScaleEntryEntity with its related BodyScaleEntryMetricEntity.
 * Used for Room relations to fetch both together.
 */
data class ScaleEntryWithMetrics(
    @Embedded val scaleEntry: BodyScaleEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = BodyScaleEntryMetricEntity::class
    )
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
)
