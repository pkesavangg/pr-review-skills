package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry
import com.greatergoods.meapp.domain.model.common.WeightUnit

/**
 * Represents a scale entry, combining EntryEntity and ScaleEntryWithMetrics.
 */
data class ScaleEntry(
    override val entry: EntryEntity,
    val scale: ScaleEntryWithMetrics,
) : Entry() {
    /**
     * Converts this database scale entry to the domain model ScaleEntry.
     * @return The domain model ScaleEntry.
     */
    fun toScaleApiEntry(): ScaleApiEntry {
        val metrics = scale.scaleEntryMetric
        val scaleEntity = scale.scaleEntry
        return ScaleApiEntry(
            operationType = entry.operationType.lowercase(),
            entryTimestamp = entry.entryTimestamp,
            weight = scaleEntity.weight * 10.0,
            bodyFat = scaleEntity.bodyFat?.times(10.0),
            muscleMass = scaleEntity.muscleMass?.times(10.0),
            boneMass = metrics?.boneMass?.times(10.0),
            water = scaleEntity.water?.times(10.0),
            bmi = scaleEntity.bmi?.times(10.0),
            source = scaleEntity.source,
            unit = entry.unit.value,
            impedance = metrics?.impedance,
            pulse = metrics?.pulse,
            visceralFatLevel = metrics?.visceralFatLevel?.times(10.0),
            subcutaneousFatPercent = metrics?.subcutaneousFatPercent?.times(10.0),
            proteinPercent = metrics?.proteinPercent?.times(10.0),
            skeletalMusclePercent = metrics?.skeletalMusclePercent?.times(10.0),
            bmr = metrics?.bmr?.times(10.0),
            metabolicAge = metrics?.metabolicAge,
            serverTimestamp = entry.serverTimestamp,
        )
    }

    companion object {
        /**
         * Creates a ScaleEntry from a domain model ScaleEntry.
         * @param scaleEntry The domain model ScaleEntry.
         * @param entryId Optional entry ID.
         * @param accountId The account ID.
         * @return The ScaleEntry database model.
         */
        fun fromScaleApiEntry(
            scaleEntry: ScaleApiEntry,
            entryId: Long? = null,
            accountId: String,
        ): ScaleEntry {
            val scaleEntryEntity =
                BodyScaleEntryEntity(
                    id = entryId ?: 0,
                    weight = scaleEntry.weight / 10.0,
                    bodyFat = scaleEntry.bodyFat?.div(10.0),
                    muscleMass = scaleEntry.muscleMass?.div(10.0),
                    water = scaleEntry.water?.div(10.0),
                    bmi = scaleEntry.bmi?.div(10.0),
                    source = scaleEntry.source,
                )

            val scaleEntryMetricEntity =
                BodyScaleEntryMetricEntity(
                    id = entryId ?: 0,
                    bmr = scaleEntry.bmr?.div(10.0),
                    metabolicAge = scaleEntry.metabolicAge,
                    proteinPercent = scaleEntry.proteinPercent?.div(10.0),
                    pulse = scaleEntry.pulse,
                    skeletalMusclePercent = scaleEntry.skeletalMusclePercent?.div(10.0),
                    subcutaneousFatPercent = scaleEntry.subcutaneousFatPercent?.div(10.0),
                    visceralFatLevel = scaleEntry.visceralFatLevel?.div(10.0),
                    boneMass = scaleEntry.boneMass?.div(10.0),
                    impedance = scaleEntry.impedance,
                )

            val scaleEntity =
                ScaleEntryWithMetrics(
                    scaleEntry = scaleEntryEntity,
                    scaleEntryMetric = scaleEntryMetricEntity,
                )
            val entryEntity =
                EntryEntity(
                    id = entryId ?: 0,
                    accountId = accountId,
                    entryTimestamp = scaleEntry.entryTimestamp,
                    serverTimestamp = scaleEntry.serverTimestamp,
                    opTimestamp = null,
                    operationType = scaleEntry.operationType,
                    deviceType = "scale",
                    deviceId = "manual",
                    unit = WeightUnit.from(scaleEntry.unit),
                    isSynced = true,
                )
            return ScaleEntry(entryEntity, scaleEntity)
        }
    }
}
