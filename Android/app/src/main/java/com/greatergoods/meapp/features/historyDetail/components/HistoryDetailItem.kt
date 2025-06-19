package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.historyDetail.strings.HistoryDetailScreenStrings
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.getDate
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.getTime
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A single history detail item row, matching the Figma design (node 7657-211196).
 * @param item The history detail item data
 * @param onClick Callback when the item is clicked
 */
@Composable
fun HistoryDetailItem(
    item: ScaleEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Date & Time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.getDate(),
                    style = MeTheme.typography.heading5,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = item.getTime(),
                    style = MeTheme.typography.subHeading2,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }

            // Weight & Unit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = (item.scale.scaleEntry.weight / 10.0).toString(),
                    style = MeTheme.typography.heading3,
                    color = MeTheme.colorScheme.textBody,
                    textAlign = TextAlign.End,
                )
                    Text(
                        text = item.entry.unit ?: "lb",
                        style = MeTheme.typography.subHeading2,
                        color = MeTheme.colorScheme.textSubheading,
                        modifier = Modifier.padding(start = MeTheme.spacing.x2s),
                    )
            }

            // Chevron
            AppIcon(
                id = AppIcons.Default.RightCaret,
                contentDescription = HistoryDetailScreenStrings.EntryDetailContentDescription,
                modifier = Modifier.padding(start = MeTheme.spacing.sm),
            )
        }
        // Bottom border
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MeTheme.colorScheme.utility),
        )
    }
}

@PreviewTheme
@Composable
fun HistoryDetailItemPreview() {
    MeAppTheme {
        AppScaffold("") {
            HistoryDetailItem(
                item =
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
                                weight = 50,
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
                onClick = {},
            )
        }
    }
}
