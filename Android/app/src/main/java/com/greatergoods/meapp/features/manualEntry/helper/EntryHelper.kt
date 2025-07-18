package com.greatergoods.meapp.features.manualEntry.helper

import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.data.services.OperationType
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.entry.BpmEntry
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryForm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

object EntryHelper {
  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter
      .ofPattern("MMM-dd")
      .withZone(ZoneId.systemDefault())

  private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter
      .ofPattern("hh:mm a")
      .withZone(ZoneId.systemDefault())

  fun FormControl<String>.toDoubleSafe(default: Double = 0.0): Double = this.value.toDoubleOrNull() ?: default

  fun FormControl<String>.toIntSafe(default: Int = 0): Int = this.value.toIntOrNull() ?: default

  fun EntryForm.toScaleEntry(weightMode: WeightUnit): ScaleEntry {
    val entryEntity =
      EntryEntity(
        id = 0L, // Let Room auto-generate
        accountId = "TODO", // Replace with actual user/account ID
        entryTimestamp =
          DateTimeConverter.timestampToIso(
            weightDateTime.controls.dateTime.value
              .getTimestamp(),
          ),
        // Assuming DateTimeValue has .timestamp: Long
        serverTimestamp = null,
        opTimestamp = null,
        unit = weightMode, // or whatever is relevant
        operationType = OperationType.CREATE.name, // or appropriate value
        deviceType = "scale", // or from context
        deviceId = "TODO", // from current device
        attempts = 0,
        isSynced = false,
      )

    val scaleEntry =
      BodyScaleEntryEntity(
        id = 0L, // Will be set by DB
        weight = weightDateTime.controls.weight.toDoubleSafe() / 10,
        bodyFat = generalMetrics.controls.bodyFat.toDoubleSafe() / 10,
        muscleMass = generalMetrics.controls.muscleMass.toDoubleSafe() / 10,
        water = generalMetrics.controls.bodyWater.toDoubleSafe() / 10,
        bmi = generalMetrics.controls.bodyMassIndex.toDoubleSafe() / 10,
        source = "manual", // or "bluetooth", "cloud", etc.
      )

    val metricEntity =
      r4ScaleMetrics?.let {
        BodyScaleEntryMetricEntity(
          id = 0L,
          bmr = it.controls.bmr.toDoubleSafe(),
          metabolicAge = it.controls.metabolicAge.toIntSafe(),
          proteinPercent = it.controls.protein.toDoubleSafe() / 10,
          pulse = it.controls.heartRate.toIntSafe(),
          skeletalMusclePercent = it.controls.skeletalMuscles.toDoubleSafe() / 10,
          subcutaneousFatPercent = it.controls.subcutaneousFat.toDoubleSafe() / 10,
          visceralFatLevel = it.controls.visceralFat.toDoubleSafe() / 10,
          boneMass = it.controls.boneMass.toDoubleSafe() / 10,
          impedance = 0, // You didn’t provide this in form controls – use 0 or calculate if needed
        )
      }

    return ScaleEntry(
      entry = entryEntity,
      scale =
        ScaleEntryWithMetrics(
          scaleEntry = scaleEntry,
          scaleEntryMetric = metricEntity,
        ),
    )
  }

  fun Double?.rounded(): Double? = this?.let { round(it * 10) / 10 }

  fun ScaleEntry.getDate(): String {
    val instant = Instant.parse(entry.entryTimestamp)
    return dateFormatter.format(instant)
  }

  fun ScaleEntry.getTime(): String {
    val instant = Instant.parse(entry.entryTimestamp)
    return timeFormatter.format(instant)
  }

  fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
    return when {
      from == to -> value
      from == WeightUnit.KG && to == WeightUnit.LB -> value * 2.20462
      from == WeightUnit.LB && to == WeightUnit.KG -> value / 2.20462
      else -> value
    }
  }

  fun BodyScaleEntryEntity.convertToDisplay(): BodyScaleEntryEntity {
    return BodyScaleEntryEntity(
      id = id,
      weight = weight.div(10.0),
      bodyFat = bodyFat?.div(10.0),
      muscleMass = muscleMass?.div(10.0),
      water = water?.div(10.0),
      bmi = bmi?.div(10.0),
      source = source,
    )
  }

  fun BodyScaleEntryEntity.convertToStored(): BodyScaleEntryEntity {
    return BodyScaleEntryEntity(
      id = id,
      weight = weight.times(10.0),
      bodyFat = bodyFat?.times(10.0),
      muscleMass = muscleMass?.times(10.0),
      water = water?.times(10.0),
      bmi = bmi?.times(10.0),
      source = source,
    )
  }

  fun BodyScaleEntryMetricEntity?.convertToDisplay(): BodyScaleEntryMetricEntity? {
    return if (this == null)
      null
    else
      BodyScaleEntryMetricEntity(
        id = id,
        bmr = bmr?.div(10.0),
        metabolicAge = metabolicAge,
        proteinPercent = proteinPercent?.div(10.0),
        pulse = pulse,
        skeletalMusclePercent = skeletalMusclePercent?.div(10.0),
        subcutaneousFatPercent = subcutaneousFatPercent?.div(10.0),
        visceralFatLevel = visceralFatLevel?.div(10.0),
        boneMass = boneMass?.div(10.0),
        impedance = impedance,
      )
  }

  fun BodyScaleEntryMetricEntity?.convertToStored(): BodyScaleEntryMetricEntity? {
    return if (this == null)
      null
    else
      BodyScaleEntryMetricEntity(
        id = id,
        bmr = bmr?.times(10.0),
        metabolicAge = metabolicAge,
        proteinPercent = proteinPercent?.times(10.0),
        pulse = pulse,
        skeletalMusclePercent = skeletalMusclePercent?.times(10.0),
        subcutaneousFatPercent = subcutaneousFatPercent?.times(10.0),
        visceralFatLevel = visceralFatLevel?.times(10.0),
        boneMass = boneMass?.times(10.0),
        impedance = impedance,
      )
  }

  fun PeriodBodyScaleSummary.convertToDisplay(): PeriodBodyScaleSummary {
    return PeriodBodyScaleSummary(
      period = period, // assuming this is already suitable
      entryTimestamp = entryTimestamp, // assuming no conversion needed
      weight = weight.div(10.0),
      bodyFat = bodyFat?.div(10.0),
      muscleMass = muscleMass?.div(10.0),
      water = water?.div(10.0),
      bmi = bmi?.div(10.0),
      bmr = bmr?.div(10.0),
      metabolicAge = metabolicAge,
      proteinPercent = proteinPercent?.div(10.0),
      pulse = pulse,
      skeletalMusclePercent = skeletalMusclePercent?.div(10.0),
      subcutaneousFatPercent = subcutaneousFatPercent?.div(10.0),
      visceralFatLevel = visceralFatLevel?.div(10.0),
      boneMass = boneMass?.div(10.0),
      impedance = impedance,
      unit = unit,
    )
  }

  fun HistoryMonth.convertToDisplay(): HistoryMonth {
    return HistoryMonth(
      entryTimestamp = entryTimestamp,
      entryCount = entryCount,
      change = change?.div(10),
      avgWeight = avgWeight?.div(10),
    )
  }

  fun Entry.convertToStored(): Entry {
    return when (this) {
      is ScaleEntry -> {
        ScaleEntry(
          entry = entry,
          scale = ScaleEntryWithMetrics(
            scaleEntry = scale.scaleEntry.convertToStored(),
            scaleEntryMetric = scale.scaleEntryMetric.convertToStored(),
          ),
        )
      }

      is BpmEntry -> {
        BpmEntry(
          entry = entry,
          bpmEntry = bpmEntry,
        )
      }
    }
  }
}
