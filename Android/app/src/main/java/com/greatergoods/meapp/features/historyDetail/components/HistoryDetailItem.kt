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
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.historyDetail.strings.HistoryDetailScreenStrings
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
    item: HistoryDetailItemModel,
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
                    text = item.date,
                    style = MeTheme.typography.heading5,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = item.time,
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
                    text = item.weight,
                    style = MeTheme.typography.heading3,
                    color = MeTheme.colorScheme.textBody,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = item.unit,
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
                    HistoryDetailItemModel(
                        date = "Dec 16",
                        time = "2:10 PM",
                        weight = "149.2",
                    ),
                onClick = {},
            )
        }
    }
}
