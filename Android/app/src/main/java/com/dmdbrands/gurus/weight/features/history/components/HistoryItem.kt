package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A single history month summary row, matching the Figma design.
 * @param item The history item data
 * @param onClick Callback when the item is clicked
 */
@Composable
fun HistoryItem(
    item: HistoryMonth,
    onClick: () -> Unit,
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceTime = 500L // Prevent multiple clicks within 300ms

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                  onClick = {
                    val currentTime = android.os.SystemClock.elapsedRealtime()
                    if (currentTime - lastClickTime >= debounceTime) {
                      lastClickTime = currentTime
                      onClick()
                    }
                  },
                  onLongClick = { /* Prevent long press navigation */ }
                )
                .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // Month & Entries (left, wider)
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = item.entryTimestamp.toString(),
                    style = MeTheme.typography.heading5,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = item.entryCount.toString().plus(" ").plus(HistoryItemStrings.Entries),
                    style = MeTheme.typography.subHeading2,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }
            // Average (middle, right-aligned)

            Column(
                modifier = Modifier
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = buildString {
                        append(item.avgWeightPrefix ?: "")
                        append(formatWeightValue(item.avgWeight))
                        append(" ${item.unit ?: "lbs"}")
                    },
                    style = MeTheme.typography.body2,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = HistoryItemStrings.Average,
                    style = MeTheme.typography.body3,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier
                        .padding(top = MeTheme.spacing.x2s),
                )
            }
            // Change (right, right-aligned)
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = buildString {
                        item.change?.let {
                          if (it > 0) {
                            append("+")
                          }
                        }
                        append(formatWeightValue(item.change))
                        append(" ${item.unit ?: "lbs"}")
                    },
                    style = MeTheme.typography.body2,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = HistoryItemStrings.Change,
                    style = MeTheme.typography.body3,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }
        }
        // Chevron (right arrow, rotated 180deg)
        AppIcon(
          id = AppIcons.Default.RightCaret,
          contentDescription = HistoryItemStrings.GoToMonthView,
          onClick = null
        )
    }
}

@PreviewTheme
@Composable
fun HistoryItemPreview() {
    MeAppTheme {
        HistoryItem(
            item =
                HistoryMonth(
                    entryTimestamp = "Dec 2022",
                    entryCount = 3,
                    avgWeight = 148.4,
                    change = -1.4,
                ),
            onClick = {},
        )
    }
}
