package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.components.AppDraggableActionItem
import com.greatergoods.meapp.features.common.components.AppDraggableList
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * List of history detail items, using HistoryDetailItem for each row.
 * @param historyDetails List of history detail item models
 * @param onItemClick Callback when an item's "More Details" is clicked
 * @param onItemDelete Callback when an item's "Delete" is clicked
 */
@Composable
fun HistoryDetailList(
    historyDetails: List<ScaleEntry>,
    onItemClick: (ScaleEntry) -> Unit,
    onItemDelete: (ScaleEntry) -> Unit,
) {
    AppDraggableList(
        items = historyDetails,
        iconWidth = 88.dp,
        keySelector = { it.entry.id },
        trailingActions = { index, item ->
            AppDraggableActionItem(
                itemWidth = 88.dp,
                text = "Delete",
                contentDescription = "Delete item",
                backgroundColor = MeTheme.colorScheme.textError,
            ) {
                onItemDelete(item)
            }
        },
    ) { item ->
        HistoryDetailItem(
            item = item,
        )
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
                            weight = 150.0,
                            bodyFat = 15.2,
                            muscleMass = 35.0,
                            water = 55.0,
                            bmi = 22.5,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 478,
                            bmr = 1800.0,
                            metabolicAge = 28,
                            proteinPercent = 18.0,
                            pulse = 60,
                            skeletalMusclePercent = 52.7,
                            subcutaneousFatPercent = 10.3,
                            visceralFatLevel = 8.0,
                            boneMass = 4.4,
                            impedance = 500,
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
                            bodyFat = 14.0,
                            muscleMass = 33.0,
                            water = 58.0,
                            bmi = 21.5,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 479,
                            bmr = 1700.0,
                            metabolicAge = 27,
                            proteinPercent = 19.0,
                            pulse = 65,
                            skeletalMusclePercent = 50.1,
                            subcutaneousFatPercent = 9.5,
                            visceralFatLevel = 7.0,
                            boneMass = 4.1,
                            impedance = 510,
                        ),
                    ),
                ),
            )
        HistoryDetailList(
            historyDetails = sampleItems,
            onItemClick = {},
            onItemDelete = {},
        )
    }
}
