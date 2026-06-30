package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Weight history month summary row using [HistoryRowLayout].
 */
/**
 * TalkBack: one coherent row announcement, e.g.
 * "Dec 2022, 3 entries, average 148.4 lbs, change -1.4 lbs".
 */
private fun weightRowDescription(item: HistoryMonth): String {
    val unit = item.unit ?: "lbs"
    val avgText = "${item.avgWeightPrefix ?: ""}${formatWeightValue(item.avgWeight)} $unit"
    val changeText = buildString {
        item.change?.let { if (it > 0) append("+") }
        append(formatWeightValue(item.change))
        append(" $unit")
    }
    return buildString {
        append(item.entryTimestamp.toString())
        append(", ${item.entryCount ?: 0} ${HistoryItemStrings.accEntriesSuffix}")
        append(", ${HistoryItemStrings.accAverageLabel} $avgText")
        append(", ${HistoryItemStrings.accChangeLabel} $changeText")
    }
}

@Composable
fun WeightHistoryItem(
    item: HistoryMonth,
    onClick: () -> Unit,
) {
    HistoryRowLayout(
        month = item.entryTimestamp.toString(),
        entryCount = item.entryCount ?: 0,
        onClick = onClick,
        rowContentDescription = weightRowDescription(item),
    ) {
        // Average weight
        Column(
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
                modifier = Modifier.padding(top = MeTheme.spacing.x2s),
            )
        }
        // Change
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = buildString {
                    item.change?.let { if (it > 0) append("+") }
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
}

@PreviewTheme
@Composable
fun WeightHistoryItemPreview() {
    MeAppTheme {
        WeightHistoryItem(
            item = HistoryMonth(
                entryTimestamp = "Dec 2022",
                entryCount = 3,
                avgWeight = 148.4,
                change = -1.4,
            ),
            onClick = {},
        )
    }
}
