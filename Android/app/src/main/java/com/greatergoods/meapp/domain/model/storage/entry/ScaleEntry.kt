package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.convertToDisplay
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.convertToStored

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
    val metrics = scale.scaleEntryMetric.convertToStored()
    val scaleEntity = scale.scaleEntry.convertToStored()
    return ScaleApiEntry(
      operationType = entry.operationType.lowercase(),
      entryTimestamp = entry.entryTimestamp,
      weight = (scaleEntity.weight).toInt(),
      bodyFat = scaleEntity.bodyFat,
      muscleMass = scaleEntity.muscleMass,
      boneMass = metrics?.boneMass,
      water = scaleEntity.water,
      bmi = scaleEntity.bmi,
      source = scaleEntity.source,
      unit = entry.unit.value,
      impedance = metrics?.impedance,
      pulse = metrics?.pulse,
      visceralFatLevel = metrics?.visceralFatLevel,
      subcutaneousFatPercent = metrics?.subcutaneousFatPercent,
      proteinPercent = metrics?.proteinPercent,
      skeletalMusclePercent = metrics?.skeletalMusclePercent,
      bmr = metrics?.bmr,
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
          weight = scaleEntry.weight.toDouble(),
          bodyFat = scaleEntry.bodyFat,
          muscleMass = scaleEntry.muscleMass,
          water = scaleEntry.water,
          bmi = scaleEntry.bmi,
          source = scaleEntry.source,
        ).convertToDisplay()

      val scaleEntryMetricEntity =
        BodyScaleEntryMetricEntity(
          id = entryId ?: 0,
          bmr = scaleEntry.bmr,
          metabolicAge = scaleEntry.metabolicAge,
          proteinPercent = scaleEntry.proteinPercent,
          pulse = scaleEntry.pulse,
          skeletalMusclePercent = scaleEntry.skeletalMusclePercent,
          subcutaneousFatPercent = scaleEntry.subcutaneousFatPercent,
          visceralFatLevel = scaleEntry.visceralFatLevel,
          boneMass = scaleEntry.boneMass,
          impedance = scaleEntry.impedance,
        ).convertToDisplay()

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
