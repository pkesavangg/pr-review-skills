package com.greatergoods.meapp.domain.model.storage.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.data.storage.db.entity.entry.ActiveEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BaseEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity

sealed class EntryInfo<T : BaseEntryEntity> {
    abstract val entry: T
    abstract val bpmEntry: BpmEntryEntity?
    abstract val scaleEntry: BodyScaleEntryEntity?
    abstract val scaleEntryMetric: BodyScaleEntryMetricEntity?
    fun toEntry(): Entry {
        val entryEntity = EntryEntity(
            id = entry.id,
            accountId = entry.accountId,
            operationType = entry.operationType,
            entryTimestamp = entry.entryTimestamp,
            opTimestamp = entry.opTimestamp,
            isSynced = entry.isSynced,
            serverTimestamp = entry.serverTimestamp,
            deviceType = entry.deviceType,
            deviceId = entry.deviceId,
            attempts = entry.attempts,
        )

        val bpmEntry = this.bpmEntry
        val scaleEntry = this.scaleEntry
        val scaleEntryMetric = this.scaleEntryMetric
        return when {
            bpmEntry != null -> BpmEntry(
                entry = entryEntity,
                bpmEntry = bpmEntry,
            )

            scaleEntry != null -> ScaleEntry(
                entry = entryEntity,
                scale = ScaleEntryWithMetrics(
                    scaleEntry = scaleEntry,
                    scaleEntryMetric = scaleEntryMetric,
                ),
            )

            else -> throw IllegalStateException("Unexpected null: both bpmEntry and scaleEntry are null.")
        }
    }
}

/**
 * Concrete class for Room: PopulatedEntry (EntryEntity)
 */
data class PopulatedEntry(
    @Embedded override val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val bpmEntry: BpmEntryEntity?,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val scaleEntry: BodyScaleEntryEntity?,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val scaleEntryMetric: BodyScaleEntryMetricEntity?
) : EntryInfo<EntryEntity>()

/**
 * Concrete class for Room: PopulatedActiveEntry (ActiveEntryEntity)
 */
data class PopulatedActiveEntry(
    @Embedded override val entry: ActiveEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val bpmEntry: BpmEntryEntity?,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val scaleEntry: BodyScaleEntryEntity?,
    @Relation(parentColumn = "id", entityColumn = "id")
    override val scaleEntryMetric: BodyScaleEntryMetricEntity?
) : EntryInfo<ActiveEntryEntity>()
