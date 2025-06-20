package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper.getMetrics
import com.greatergoods.meapp.features.historyDetail.strings.HistoryDetailScreenStrings
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.getDate
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.getTime
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun HistoryDetailItem(
    item: ScaleEntry,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val backgroundColor =
        if (isExpanded) MeTheme.colorScheme.textBody else Color.Transparent

    Column(modifier = modifier.background(backgroundColor)) {
        HistoryDetailItemHeader(
            item = item,
            canExpand = getMetrics(item).isNotEmpty(),
            isExpanded = isExpanded,
            onClick = {
                isExpanded = !isExpanded
            },
        )
        AnimatedVisibility(
            visible = isExpanded,
        ) {
            HistoryDetailItemDetails(
                item = item,
            )
        }
        if (!isExpanded) {
            HorizontalDivider(color = MeTheme.colorScheme.utility, thickness = 1.dp)
        }
    }
}

@Composable
private fun HistoryDetailItemHeader(
    item: ScaleEntry,
    canExpand: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) -90f else 90f, label = "")

    val textColor =
        if (isExpanded) MeTheme.colorScheme.primaryBackground else MeTheme.colorScheme.textBody
    val subTextColor =
        if (isExpanded) MeTheme.colorScheme.secondaryBackground else MeTheme.colorScheme.textSubheading

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand, onClick = onClick)
                .padding(MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = MeTheme.spacing.x3s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.getDate(),
                    style = MeTheme.typography.heading5,
                    color = textColor,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
                Text(
                    text = item.getTime(),
                    style = MeTheme.typography.subHeading2,
                    color = subTextColor,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = (item.scale.scaleEntry.weight).toString(),
                    style = MeTheme.typography.heading3,
                    color = textColor,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = item.entry.unit ?: "lb",
                    style = MeTheme.typography.subHeading2,
                    color = subTextColor,
                    modifier = Modifier.padding(start = MeTheme.spacing.x2s),
                )
            }
        }
        if (canExpand) {
            AppIcon(
                id = AppIcons.Default.RightCaret,
                contentDescription = HistoryDetailScreenStrings.EntryDetailContentDescription,
                modifier =
                    Modifier
                        .padding(start = MeTheme.spacing.sm)
                        .rotate(rotation),
            )
        } else {
            Spacer(modifier = Modifier.width(MeTheme.spacing.xl))
        }
    }
}

@PreviewTheme
@Composable
private fun HistoryDetailItemPreview() {
    MeAppTheme {
        AppScaffold("") {
            Column {
                HistoryDetailItem(
                    item = sampleScaleEntry,
                )
                HistoryDetailItem(
                    item = sampleScaleEntry,
                )
            }
        }
    }
}

private val sampleScaleEntry = ScaleEntry(
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
)
