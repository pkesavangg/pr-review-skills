package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SwipeableListItemScope
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper.getMetrics
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getDate
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun SwipeableListItemScope.WeightHistoryDetailItem(
    item: ScaleEntry,
    isExpanded: Boolean = false,
    onItemOpen: (Long) -> Unit = {},
    onEditEntry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bodyMetric = fromScaleEntry(item)

    Swipeable {
        WeightHistoryDetailItemHeader(
            item = item,
            // Always expandable so note-less entries can still reveal the add-note
            // affordance in the details section (MOB-438).
            canExpand = true,
            isExpanded = isExpanded,
            onClick = {
                onItemOpen(item.entry.id)
            },
        )
    }

    Static {
        AnimatedVisibility(
            visible = isExpanded,
            modifier = modifier.fillMaxWidth(),
        ) {
            WeightHistoryDetailItemDetails(
                item = item,
                onEditEntry = onEditEntry,
            )
        }
        if (!isExpanded) {
            HorizontalDivider(color = MeTheme.colorScheme.utility, thickness = 1.dp)
        }
    }
}

@Composable
fun WeightHistoryDetailItemHeader(
    item: ScaleEntry,
    canExpand: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) -90f else 90f, label = "")
  val tintColor = if(isExpanded) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.primaryAction
    val backgroundColor =
        if (isExpanded) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.secondaryBackground
    val textColor =
        if (isExpanded) MeTheme.colorScheme.primaryBackground else MeTheme.colorScheme.textBody
    val subTextColor =
        if (isExpanded) MeTheme.colorScheme.secondaryBackground else MeTheme.colorScheme.textSubheading
  var lastClickTime by remember { mutableStateOf(0L) }
  val debounceTime = 500L // Prevent multiple clicks within 300ms
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("entry_row")
                .background(backgroundColor)
                .combinedClickable(
                  enabled = canExpand,
                  onClick = {
                    val currentTime = android.os.SystemClock.elapsedRealtime()
                    if (currentTime - lastClickTime >= debounceTime) {
                      lastClickTime = currentTime
                      onClick()
                    }
                  },
                  onLongClick = {})
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
                    text = buildString {
                        item.scale.scaleEntry.prefix?.let { append(it) }  // append prefix if not null
                        append(formatWeightValue(item.scale.scaleEntry.weight))        // always append value with sign
                    },
                    style = MeTheme.typography.heading3,
                    color = textColor,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = item.entry.unit.label,
                    style = MeTheme.typography.subHeading2,
                    color = subTextColor,
                    modifier = Modifier.padding(start = MeTheme.spacing.x2s),
                )
            }
        }
        if (canExpand) {
            AppIcon(
              id = AppIcons.Default.RightCaret,
              onClick = null,
              contentDescription = HistoryDetailScreenStrings.EntryDetailContentDescription,
              tintColor = tintColor,
              modifier =
                    Modifier
                        .padding(start = MeTheme.spacing.lg)
                        .rotate(rotation),
            )
        } else {
            Spacer(modifier = Modifier.width(MeTheme.spacing.x3l))
        }
    }
}

@PreviewTheme
@Composable
private fun SwipeableListItemScope.WeightHistoryDetailItemPreview() {
    MeAppTheme {
        AppScaffold("") {
            WeightHistoryDetailItem(
                item = sampleScaleEntry,
                isExpanded = false,
                onItemOpen = {},
            )
            WeightHistoryDetailItem(
                item = sampleScaleEntry,
                isExpanded = true,
                onItemOpen = {},
            )
        }
    }
}

private val sampleScaleEntry =
    ScaleEntry(
        entry =
            EntryEntity(
                id = 478,
                accountId = "4SWOWDAP9t2gS50MFp9HQS",
                entryTimestamp = "2025-06-19T06:30:00.000Z",
                serverTimestamp = "2025-06-19T10:29:13.914Z",
                opTimestamp = null,
                operationType = "create",
                deviceType = "scale",
                deviceId = "manual",
                attempts = 0,
                unit = WeightUnit.LB,
                isSynced = true,
            ),
        scale =
            ScaleEntryWithMetrics(
                scaleEntry =
                    BodyScaleEntryEntity(
                        id = 478,
                        weight = 150.0,
                        bodyFat = 15.2,
                        muscleMass = 35.0,
                        water = 55.0,
                        bmi = 22.5,
                        source = "manual",
                    ),
                scaleEntryMetric =
                    BodyScaleEntryMetricEntity(
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
