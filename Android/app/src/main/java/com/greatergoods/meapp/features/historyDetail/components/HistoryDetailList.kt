package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * List of history detail items, using HistoryDetailItem for each row.
 * @param items List of history detail item models
 * @param onItemClick Callback when an item is clicked
 */
@Composable
fun HistoryDetailList(
    items: List<ScaleEntry>,
    onItemClick: (ScaleEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items) { item ->
            HistoryDetailItem(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@PreviewTheme
@Composable
fun HistoryDetailListPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                ScaleEntry(
                    entry = EntryEntity(
                        id = 478,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-19T06:30:00.000Z",
                        serverTimestamp = "2025-06-19T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = "lb",
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 478,
                            weight = 50.0,
                            bodyFat = 0,
                            muscleMass = 0,
                            water = 0,
                            bmi = 0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 478,
                            bmr = 0,
                            metabolicAge = 0,
                            proteinPercent = 0,
                            pulse = 0,
                            skeletalMusclePercent = 0,
                            subcutaneousFatPercent = 0,
                            visceralFatLevel = 0,
                            boneMass = 0,
                            impedance = 0,
                        ),
                    ),
                ),
                ScaleEntry(
                    entry = EntryEntity(
                        id = 479,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-20T06:30:00.000Z",
                        serverTimestamp = "2025-06-20T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = "kg",
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 479,
                            weight = 70.0,
                            bodyFat = 0,
                            muscleMass = 0,
                            water = 0,
                            bmi = 0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 479,
                            bmr = 0,
                            metabolicAge = 0,
                            proteinPercent = 0,
                            pulse = 0,
                            skeletalMusclePercent = 0,
                            subcutaneousFatPercent = 0,
                            visceralFatLevel = 0,
                            boneMass = 0,
                            impedance = 0,
                        ),
                    ),
                ),
            )
        HistoryDetailList(
            sampleItems,
            onItemClick = {},
        )
    }
}
