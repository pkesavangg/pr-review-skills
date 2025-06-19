package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry
import java.time.Instant

/**
 * Represents a scale entry, combining EntryEntity and ScaleEntryWithMetrics.
 */
data class ScaleEntry(
    override val entry: EntryEntity,
    val scale: ScaleEntryWithMetrics
) : Entry() {
    /**
     * Converts this database scale entry to the domain model ScaleEntry.
     * @return The domain model ScaleEntry.
     */
    fun toScaleApiEntry(): ScaleApiEntry {
        val metrics = scale.scaleEntryMetric
        val scaleEntity = scale.scaleEntry
        val ms = entry.entryTimestamp
        val iso = Instant.ofEpochMilli(ms).toString()
        return ScaleApiEntry(
            operationType = entry.operationType.lowercase(),
            entryTimestamp = iso,
            weight = scaleEntity.weight,
            bodyFat = scaleEntity.bodyFat,
            muscleMass = scaleEntity.muscleMass,
            boneMass = metrics?.boneMass ?: 0,
            water = scaleEntity.water,
            bmi = scaleEntity.bmi,
            source = scaleEntity.source,
            unit = entry.unit,
            impedance = metrics?.impedance,
            pulse = metrics?.pulse,
            visceralFatLevel = metrics?.visceralFatLevel,
            subcutaneousFatPercent = metrics?.subcutaneousFatPercent,
            proteinPercent = metrics?.proteinPercent,
            skeletalMusclePercent = metrics?.skeletalMusclePercent,
            bmr = metrics?.bmr,
            metabolicAge = metrics?.metabolicAge,
            serverTimestamp = iso,
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
        fun fromScaleApiEntry(scaleEntry: ScaleApiEntry, entryId: Long? = null, accountId: String): ScaleEntry {

            val scaleEntryEntity = BodyScaleEntryEntity(
                id = entryId ?: 0,
                weight = scaleEntry.weight,
                bodyFat = scaleEntry.bodyFat,
                muscleMass = scaleEntry.muscleMass,
                water = scaleEntry.water,
                bmi = scaleEntry.bmi,
                source = scaleEntry.source,
            )

            val scaleEntryMetricEntity = BodyScaleEntryMetricEntity(
                id = entryId ?: 0,
                bmr = scaleEntry.bmr ?: 0,
                metabolicAge = scaleEntry.metabolicAge ?: 0,
                proteinPercent = scaleEntry.proteinPercent ?: 0,
                pulse = scaleEntry.pulse ?: 0,
                skeletalMusclePercent = scaleEntry.skeletalMusclePercent ?: 0,
                subcutaneousFatPercent = scaleEntry.subcutaneousFatPercent ?: 0,
                visceralFatLevel = scaleEntry.visceralFatLevel ?: 0,
                boneMass = scaleEntry.boneMass,
                impedance = scaleEntry.impedance ?: 0,
            )

            val scaleEntity = ScaleEntryWithMetrics(
                scaleEntry = scaleEntryEntity,
                scaleEntryMetric = scaleEntryMetricEntity,
            )
            val isoString = scaleEntry.entryTimestamp
            val epochMillis = Instant.parse(isoString).toEpochMilli()
            val entryEntity = EntryEntity(
                id = entryId ?: 0,
                accountId = accountId,
                entryTimestamp = epochMillis,
                serverTimestamp = null,
                opTimestamp = null,
                operationType = scaleEntry.operationType,
                deviceType = "scale",
                deviceId = "manual",
                unit = scaleEntry.unit,
                isSynced = true,
            )
            return ScaleEntry(entryEntity, scaleEntity)
        }
    }
}
