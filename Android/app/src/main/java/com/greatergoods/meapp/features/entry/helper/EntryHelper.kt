package com.greatergoods.meapp.features.entry.helper

import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.data.services.OperationType
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.entry.viewmodel.EntryFormControls
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object EntryHelper {
    fun FormControl<String>.toIntSafe(default: Int = 0): Int {
        return this.value.toIntOrNull() ?: default
    }

    fun EntryFormControls.toScaleEntry(weightMode: String): ScaleEntry {
        System.currentTimeMillis()
        val entryEntity = EntryEntity(
            id = 0L, // Let Room auto-generate
            accountId = "TODO", // Replace with actual user/account ID
            entryTimestamp = DateTimeConverter.timestampToIso(weightDateTime.dateTime.value.getTimestamp()), // Assuming DateTimeValue has .timestamp: Long
            serverTimestamp = null,
            opTimestamp = null,
            unit = "lb", // or whatever is relevant
            operationType = OperationType.CREATE.name, // or appropriate value
            deviceType = "scale",     // or from context
            deviceId = "TODO",        // from current device
            attempts = 0,
            isSynced = false,
        )

        val scaleEntry = BodyScaleEntryEntity(
            id = 0L, // Will be set by DB
            weight = weightDateTime.weight.toIntSafe(),
            bodyFat = generalMetrics.bodyFat.toIntSafe(),
            muscleMass = generalMetrics.muscleMass.toIntSafe(),
            water = generalMetrics.bodyWater.toIntSafe(),
            bmi = generalMetrics.bodyMassIndex.toIntSafe(),
            source = "manual", // or "bluetooth", "cloud", etc.
        )

        val metricEntity = r4ScaleMetrics?.let {
            BodyScaleEntryMetricEntity(
                id = 0L,
                bmr = it.bmr.toIntSafe(),
                metabolicAge = it.metabolicAge.toIntSafe(),
                proteinPercent = it.protein.toIntSafe(),
                pulse = it.heartRate.toIntSafe(),
                skeletalMusclePercent = it.skeletalMuscles.toIntSafe(),
                subcutaneousFatPercent = it.subcutaneousFat.toIntSafe(),
                visceralFatLevel = it.visceralFat.toIntSafe(),
                boneMass = it.boneMass.toIntSafe(),
                impedance = 0, // You didn’t provide this in form controls – use 0 or calculate if needed
            )
        }

        return ScaleEntry(
            entry = entryEntity,
            scale = ScaleEntryWithMetrics(
                scaleEntry = scaleEntry,
                scaleEntryMetric = metricEntity,
            ),
        )
    }

    fun HistoryMonth.process(): HistoryMonth {
        val monthYear = entryTimestamp?.let {
            try {
                val zonedDateTime = ZonedDateTime.parse(it)
                DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(zonedDateTime)
            } catch (e: Exception) {
                it // fallback to original string if parsing fails
            }
        }

        fun Double?.rounded(): Double? = this?.let { String.format("%.2f", it).toDouble() }

        return this.copy(
            entryTimestamp = monthYear,
            avgWeight = avgWeight?.div(10.0).rounded(),
            change = change?.div(10.0).rounded()
            // entryCount is already Int? so no need to change
        )
    }
}
