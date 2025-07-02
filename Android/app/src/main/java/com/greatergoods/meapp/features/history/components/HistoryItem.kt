package com.greatergoods.meapp.features.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.history.strings.HistoryItemStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
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
                    text = item.avgWeight.toString().plus(item.unit ?: " lbs"),
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
                    text = item.change.toString().plus(item.unit ?: " lbs"),
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
