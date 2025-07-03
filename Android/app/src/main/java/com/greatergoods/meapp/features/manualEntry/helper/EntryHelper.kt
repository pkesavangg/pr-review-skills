package com.greatergoods.meapp.features.manualEntry.helper

import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.data.services.OperationType
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryForm
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        System.currentTimeMillis()
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

    fun HistoryMonth.process(unit: WeightUnit?): HistoryMonth {
        val monthYear =
            entryTimestamp?.let {
                try {
                    val zonedDateTime = ZonedDateTime.parse(it)
                    DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(zonedDateTime)
                } catch (e: Exception) {
                    it // fallback to original string if parsing fails
                }
            }

        val conversionFactor =
            when (unit) {
                WeightUnit.LB -> 1.0
                WeightUnit.KG -> 0.453592
                else -> 1.0
            }

        return this.copy(
            entryTimestamp = monthYear,
            avgWeight = avgWeight?.times(conversionFactor)?.rounded(),
            change = change?.times(conversionFactor)?.rounded(),
            unit = unit?.name,
        )
    }

    fun Double?.rounded(): Double? = this?.let { String.format("%.2f", it).toDouble() }

    fun ScaleEntry.getDate(): String {
        val instant = Instant.parse(entry.entryTimestamp)
        return dateFormatter.format(instant)
    }

    fun ScaleEntry.getTime(): String {
        val instant = Instant.parse(entry.entryTimestamp)
        return timeFormatter.format(instant)
    }
}
