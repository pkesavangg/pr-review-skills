package com.dmdbrands.gurus.weight.features.manualEntry.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.services.OperationType
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryForm
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round
import kotlin.math.roundToInt

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
  fun Float.toDouble1dp(rounding: RoundingMode = RoundingMode.HALF_UP): Double =
    if (this.isFinite()) BigDecimal.valueOf(this.toDouble()).setScale(1, rounding).toDouble() else this.toDouble()

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

  fun GGScaleEntry.toScaleEntry(accountId: String, deviceId: String): ScaleEntry {
    val entryEntity = EntryEntity(
      accountId = accountId,
      entryTimestamp = DateTimeConverter.timestampToIso(date), // you may want to format it properly
      serverTimestamp = null,
      opTimestamp = null,
      operationType = operationType ?: "create", // default fallback
      deviceType = protocolType,
      deviceId = deviceId,
      unit = if (unit.lowercase() == "kg") WeightUnit.KG else WeightUnit.LB,
      isSynced = false,
    )

    val bodyScaleEntryEntity = BodyScaleEntryEntity(
      id = 0, // Will be auto-generated in DB
      weight = if (unit.lowercase() == "kg") weightInKg.toDouble() else weight.toDouble(),
      bodyFat = bodyFat.toDouble(),
      muscleMass = muscleMass.toDouble(),
      water = water.toDouble(),
      bmi = bmi.toDouble1dp(),
      source = getScaleSetupType(protocolType),
    )

    val metricEntity = BodyScaleEntryMetricEntity(
      id = 0, // Will be same as scaleEntryEntity.id when inserted
      bmr = bmr.toDouble(),
      metabolicAge = metabolicAge,
      proteinPercent = proteinPercent.toDouble(),
      pulse = pulse,
      skeletalMusclePercent = skeletalMusclePercent.toDouble(),
      subcutaneousFatPercent = subcutaneousFatPercent.toDouble(),
      visceralFatLevel = visceralFatLevel.toDouble(),
      boneMass = boneMass.toDouble(),
      impedance = impedance.toInt(),
    )

    val scaleEntryWithMetrics = ScaleEntryWithMetrics(
      scaleEntry = bodyScaleEntryEntity,
      scaleEntryMetric = metricEntity,
    )

    return ScaleEntry(
      entry = entryEntity,
      scale = scaleEntryWithMetrics,
    )
  }

  fun getScaleSetupType(protocolType: String): String = when (protocolType) {
    GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value -> ScaleSetupType.toSource(ScaleSetupType.BtWifiR4.value)
    else -> ScaleSetupType.toSource(ScaleSetupType.Bluetooth.value)
  }

  fun getCalculatedBMI(weight: Float, unit: WeightUnit, height: Int): Double {
    val weightKg = when (unit.value) {
      "kg" -> weight.toDouble()
      else -> weight.toDouble() * 0.453592 // Convert lbs to kg
    }
    val heightCm = ConversionTools.convertStoredHeightToCm(height)
    return ConversionTools.calculateBMIFromMetric(weightKg, heightCm)
  }

  /**
   * Converts AppSyncResult to ScaleEntry format
   */
  fun com.greatergoods.libs.appsync.model.AppSyncResult.toScaleEntry(
    accountId: String,
    unit: String,
    userHeight: Int? = null,
    isSaving: Boolean = false
  ): ScaleEntry {
    val currentTime = System.currentTimeMillis()
    val entryEntity = EntryEntity(
      id = 0L, // Let Room auto-generate
      accountId = accountId,
      entryTimestamp = DateTimeConverter.timestampToIso(currentTime),
      serverTimestamp = null,
      opTimestamp = null,
      unit = if (unit.lowercase() == "kg") WeightUnit.KG else WeightUnit.LB,
      operationType = OperationType.CREATE.name,
      deviceType = "appsync",
      deviceId = "appsync_scale",
      attempts = 0,
      isSynced = false,
    )

    // Calculate BMI if weight and height are available
    val calculatedBmi = if (weight != null && userHeight != null) {
      val weightKg = when (mode?.lowercase()) {
        "kg" -> weight!!.toDouble()
        else -> ConversionTools.convertStoredToKg(weight!!.toDouble() * 10) // Convert lbs to kg
      }
      val heightCm = ConversionTools.convertStoredHeightToCm(userHeight)
      ConversionTools.calculateBMIFromMetric(weightKg, heightCm)
    } else {
      null
    }
    val rawWeight = ConversionTools.convertAppSyncDisplayToStored(weight?.toDouble() ?: 0.0)
    val convertedWeight = ConversionTools.convertStoredToDisplay(rawWeight, unit == WeightUnit.KG.value)

    val scaleEntry = BodyScaleEntryEntity(
      id = 0L, // Will be set by DB
      weight = if (isSaving) convertedWeight else rawWeight,
      bodyFat = fat?.toDouble(),
      muscleMass = muscle?.toDouble(),
      water = water?.toDouble(),
      bmi = calculatedBmi,
      source = "appsync scale",
    )

    val scaleEntryWithMetrics = ScaleEntryWithMetrics(
      scaleEntry = scaleEntry,
      scaleEntryMetric = null, // AppSync doesn't provide R4 metrics
    )

    return ScaleEntry(
      entry = entryEntity,
      scale = scaleEntryWithMetrics,
    )
  }

  /**
   * Converts AppSyncResult to ScaleApiEntry format
   */
  fun com.greatergoods.libs.appsync.model.AppSyncResult.toScaleApiEntry(accountId: String): ScaleApiEntry {
    val currentTime = System.currentTimeMillis()

    // Process body composition data with proper conversion
    fat?.let {
      round(it * 10) / 10.0
    }?.roundToInt()

    val processedMuscleMass = muscle?.let {
      round(it * 10) / 10.0
    }?.roundToInt()

    val processedWater = water?.let {
      round(it * 10) / 10.0
    }?.roundToInt()

    return ScaleApiEntry(
      operationType = OperationType.CREATE.name.lowercase(),
      entryTimestamp = DateTimeConverter.timestampToIso(currentTime),
      weight = weight?.toInt() ?: 0,
      bodyFat = fat?.toInt(),
      muscleMass = processedMuscleMass,
      boneMass = null, // AppSync doesn't provide bone mass
      water = processedWater,
      bmi = null, // BMI calculated separately
      source = "appsync scale",
      unit = if (mode?.lowercase() == "kg") "kg" else "lb",
      impedance = null, // AppSync doesn't provide impedance
      pulse = null, // AppSync doesn't provide pulse
      visceralFatLevel = null, // AppSync doesn't provide visceral fat
      subcutaneousFatPercent = null, // AppSync doesn't provide subcutaneous fat
      proteinPercent = null, // AppSync doesn't provide protein
      skeletalMusclePercent = null, // AppSync doesn't provide skeletal muscle
      bmr = null, // AppSync doesn't provide BMR
      metabolicAge = null, // AppSync doesn't provide metabolic age
      serverTimestamp = null,
    )
  }
}
