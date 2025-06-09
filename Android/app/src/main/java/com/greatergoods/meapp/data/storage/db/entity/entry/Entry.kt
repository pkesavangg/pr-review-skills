package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.domain.model.api.entry.ScaleEntry

/**
 * Wrapper class that combines EntryEntity with its related entities (BpmEntry, ScaleEntry, ScaleEntryMetric).
 * This makes it easier to fetch related data in a single query.
 */
data class Entry(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = BpmEntryEntity::class
    )
    val bpmEntry: BpmEntryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = BodyScaleEntryEntity::class
    )
    val scaleEntry: BodyScaleEntryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = BodyScaleEntryMetricEntity::class
    )
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
) {
    fun toScaleEntry(): ScaleEntry? {
        val scale = scaleEntry ?: return null
        val metrics = scaleEntryMetric

        return ScaleEntry(
            operationType = entry.operationType,
            entryTimestamp = entry.entryTimestamp,
            weight = scale.weight,
            bodyFat = scale.bodyFat,
            muscleMass = scale.muscleMass,
            boneMass = metrics?.boneMass ?: 0,
            water = scale.water,
            bmi = scale.bmi,
            source = scale.source,
            unit = metrics?.unit ?: "kg",
            impedance = metrics?.impedance,
            pulse = metrics?.pulse ?: bpmEntry?.pulse,
            visceralFatLevel = metrics?.visceralFatLevel,
            subcutaneousFatPercent = metrics?.subcutaneousFatPercent,
            proteinPercent = metrics?.proteinPercent,
            skeletalMusclePercent = metrics?.skeletalMusclePercent,
            bmr = metrics?.bmr,
            metabolicAge = metrics?.metabolicAge,
        )
    }

    companion object {
        fun fromViewEntry(entry: EntryView): Entry {
            val entryEntity = EntryEntity(
                id = entry.entry.id,
                accountId = entry.entry.accountId,
                entryTimestamp = entry.entry.entryTimestamp,
                serverTimestamp = entry.entry.serverTimestamp,
                opTimestamp = entry.entry.opTimestamp,
                operationType = entry.entry.operationType,
                deviceType = entry.entry.deviceType,
                deviceId = entry.entry.deviceId,
                attempts = entry.entry.attempts,
                isSynced = entry.entry.isSynced,
            )
            return Entry(
                entry = entryEntity,
                bpmEntry = entry.bpmEntry,
                scaleEntry = entry.scaleEntry,
                scaleEntryMetric = entry.scaleEntryMetric,
            )
        }

        fun fromScaleEntry(scaleEntry: ScaleEntry, entryId: Long? = null, accountId: String): Entry {
            val entryEntity = EntryEntity(
                id = 0,
                accountId = accountId,
                entryTimestamp = scaleEntry.entryTimestamp,
                serverTimestamp = null,
                opTimestamp = null,
                operationType = scaleEntry.operationType,
                deviceType = "scale",
                deviceId = "manual",
                isSynced = true,
            )

            val scaleEntryEntity = BodyScaleEntryEntity(
                id = 0,
                weight = scaleEntry.weight,
                bodyFat = scaleEntry.bodyFat,
                muscleMass = scaleEntry.muscleMass,
                water = scaleEntry.water,
                bmi = scaleEntry.bmi,
                source = scaleEntry.source,
            )

            val scaleEntryMetricEntity = BodyScaleEntryMetricEntity(
                id = 0,
                bmr = scaleEntry.bmr ?: 0,
                metabolicAge = scaleEntry.metabolicAge ?: 0,
                proteinPercent = scaleEntry.proteinPercent ?: 0,
                pulse = scaleEntry.pulse ?: 0,
                skeletalMusclePercent = scaleEntry.skeletalMusclePercent ?: 0,
                subcutaneousFatPercent = scaleEntry.subcutaneousFatPercent ?: 0,
                visceralFatLevel = scaleEntry.visceralFatLevel ?: 0,
                boneMass = scaleEntry.boneMass,
                impedance = scaleEntry.impedance ?: 0,
                unit = scaleEntry.unit,
            )

            return Entry(
                entry = entryEntity,
                bpmEntry = null,
                scaleEntry = scaleEntryEntity,
                scaleEntryMetric = scaleEntryMetricEntity,
            )
        }
    }
}



