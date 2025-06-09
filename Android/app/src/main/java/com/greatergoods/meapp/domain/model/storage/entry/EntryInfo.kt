package com.greatergoods.meapp.domain.model.storage.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.data.storage.db.entity.entry.ActiveEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BaseEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity

/**
 * Wrapper class that combines an entry entity with its related entities.
 * This provides a complete view of an entry with all associated data.
 */
data class EntryInfo<T : BaseEntryEntity>(
    @Embedded val entry: T,

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
     * Converts this EntryWithRelations to the appropriate Entry domain model.
     * @return The appropriate Entry subclass (BpmEntry or ScaleEntry), or null if data is insufficient.
     */
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

    /**
     * Checks if this entry has BPM data.
     */
    val hasBpmData: Boolean
        get() = bpmEntry != null

    /**
     * Checks if this entry has scale data.
     */
    val hasScaleData: Boolean
        get() = scaleEntry != null && scaleEntryMetric != null

    /**
     * Gets the device type from the entry.
     */
    val deviceType: String
        get() = entry.deviceType
}

// Type alias for common usage
typealias PopulatedEntry = EntryInfo<EntryEntity>
typealias PopulatedActiveEntry = EntryInfo<ActiveEntryEntity>
