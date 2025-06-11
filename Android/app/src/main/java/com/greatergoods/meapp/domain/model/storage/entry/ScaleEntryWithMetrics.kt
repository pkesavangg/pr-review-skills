package com.greatergoods.meapp.domain.model.storage.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity

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
