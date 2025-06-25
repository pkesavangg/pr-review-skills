package com.greatergoods.meapp.features.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.R.attr.contentDescription

/**
 * A single history month summary row, matching the Figma design.
 * @param item The history item data
 * @param onClick Callback when the item is clicked
 */
@Composable
fun HistoryItem(
    item: HistoryMonth,
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
            // Month & Entries
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.entryTimestamp.toString(),
                    style = MeTheme.typography.heading5,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = item.entryCount.toString().plus(" entries"),
                    style = MeTheme.typography.subHeading2,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Average
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.avgWeight.toString().plus(item.unit ?: " lbs"),
                    style = MeTheme.typography.body2,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = "Average",
                    style = MeTheme.typography.body3,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.change.toString().plus(item.unit ?: " lbs"),
                    style = MeTheme.typography.body2,
                    color = MeTheme.colorScheme.textBody,
                )
                Text(
                    text = "Change",
                    style = MeTheme.typography.body3,
                    color = MeTheme.colorScheme.textSubheading,
                    modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                )
            }
            Spacer(modifier = Modifier.weight(.2f))
            // Chevron (right arrow, rotated 180deg)
            AppIcon(
                id = AppIcons.Default.RightCaret,
                contentDescription = "Go to month view",
            )
        }
        // Bottom border
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MeTheme.colorScheme.utility),
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
