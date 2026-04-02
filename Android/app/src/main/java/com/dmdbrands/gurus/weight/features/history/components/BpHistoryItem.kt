package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.enums.BpSeverity
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * BP history month summary row using [HistoryRowLayout].
 * Systolic/diastolic value is color-coded by [BpSeverity].
 */
@Composable
fun BpHistoryItem(
    item: BpHistoryMonth,
    onClick: () -> Unit,
) {
    val severityColor = when (item.severity) {
        BpSeverity.NORMAL -> MeTheme.colorScheme.success
        BpSeverity.ELEVATED -> MeTheme.colorScheme.streak
        BpSeverity.HYPERTENSION -> MeTheme.colorScheme.textError
    }

    HistoryRowLayout(
        month = item.entryTimestamp,
        entryCount = item.entryCount,
        onClick = onClick,
    ) {
        // Avg pressure (severity-colored)
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = item.pressureDisplay,
                style = MeTheme.typography.heading5,
                color = severityColor,
            )
            Text(
                text = HistoryItemStrings.AvgPressure,
                style = MeTheme.typography.body3,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(top = MeTheme.spacing.x2s),
            )
        }
        // Avg pulse
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = item.avgPulse.toString(),
                style = MeTheme.typography.heading5,
                color = MeTheme.colorScheme.textBody,
            )
            Text(
                text = HistoryItemStrings.AvgPulse,
                style = MeTheme.typography.body3,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(top = MeTheme.spacing.x2s),
            )
        }
    }
}

@PreviewTheme
@Composable
fun BpHistoryItemPreview() {
    MeAppTheme {
        Column {
            BpHistoryItem(
                item = BpHistoryMonth(
                    entryTimestamp = "Dec 2025",
                    avgSystolic = 115,
                    avgDiastolic = 75,
                    avgPulse = 60,
                    entryCount = 5,
                ),
                onClick = {},
            )
            BpHistoryItem(
                item = BpHistoryMonth(
                    entryTimestamp = "Sep 2025",
                    avgSystolic = 125,
                    avgDiastolic = 78,
                    avgPulse = 62,
                    entryCount = 5,
                ),
                onClick = {},
            )
            BpHistoryItem(
                item = BpHistoryMonth(
                    entryTimestamp = "Jul 2025",
                    avgSystolic = 140,
                    avgDiastolic = 90,
                    avgPulse = 64,
                    entryCount = 5,
                ),
                onClick = {},
            )
        }
    }
}
