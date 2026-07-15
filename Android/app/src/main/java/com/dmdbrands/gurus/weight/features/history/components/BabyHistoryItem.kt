package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A single baby history week row.
 * Shows date, entry count, weight (lb/oz), length (in), percentile.
 * Values in baby purple with inline unit labels in gray.
 */
@Suppress("LongMethod")
@Composable
fun BabyHistoryItem(
    item: BabyWeekHistory,
    onClick: () -> Unit,
    showBalloon: Boolean = false,
) {
    val babyColor = MeTheme.colorScheme.baby
    val unitColor = MeTheme.colorScheme.textSubheading
    val boldStyle = SpanStyle(color = babyColor, fontWeight = FontWeight.Bold)
    val unitStyle = SpanStyle(color = unitColor, fontWeight = FontWeight.Normal)

    val weightText = buildAnnotatedString {
        if (item.weightLb != null) {
            withStyle(boldStyle) { append("${item.weightLb} ") }
            withStyle(unitStyle) { append("lbs ") }
        }
        if (item.weightOz != null) {
            withStyle(boldStyle) { append("${item.weightOz}") }
            withStyle(unitStyle) { append(" oz") }
        }
        if (item.weightLb == null && item.weightOz == null) {
            withStyle(boldStyle) { append("--") }
        }
    }

    val lengthText = buildAnnotatedString {
        if (item.lengthInches != null) {
            withStyle(boldStyle) { append("${item.lengthInches.toInt()}") }
            withStyle(unitStyle) { append(" in") }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    val percentText = buildAnnotatedString {
        if (item.percentile != null) {
            withStyle(boldStyle) { append("${item.percentile}") }
            withStyle(unitStyle) { append(" th") }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // TalkBack: collapse the date + weight + length + percentile columns and the
    // decorative chevron into one coherent row announcement.
    val rowDescription = buildString {
        append(item.date)
        if (item.entryCount > 0) {
            append(", ${item.entryCount} ${HistoryItemStrings.accEntriesSuffix}")
        }
        append(", ${HistoryItemStrings.accWeightLabel} ${weightText.text}")
        append(", ${HistoryItemStrings.accLengthLabel} ${lengthText.text}")
        append(", ${HistoryItemStrings.accPercentileLabel} ${percentText.text}")
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            // Date cell (flexible) — leading birthday balloon on the baby's birth date, then the
            // date + entry count. Weighted so it fills the leftover space and the date ellipsizes
            // instead of pushing the metrics into an overflow/wrap. (Figma 32758-31114)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
            ) {
                if (showBalloon) {
                    AppIcon(
                        id = AppIcons.Default.BirthdayBalloon,
                        contentDescription = HistoryItemStrings.BirthdayBalloonContentDescription,
                        // Self-coloured vector (purple + white) — render untinted.
                        type = AppIconType.Default,
                        onClick = null,
                    )
                }
                Column {
                    Text(
                        text = item.date,
                        style = MeTheme.typography.heading5,
                        color = MeTheme.colorScheme.textBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.entryCount > 0) {
                        Text(
                            text = "${item.entryCount} ${HistoryItemStrings.Entries}",
                            style = MeTheme.typography.subHeading2,
                            color = unitColor,
                            modifier = Modifier.padding(top = MeTheme.spacing.x2s),
                        )
                    }
                }
            }
            // Metrics group — content-sized so weight/length/percent keep even gaps and never wrap.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
            ) {
                // Weight (lb oz)
                Column {
                    Text(text = weightText, style = MeTheme.typography.heading5)
                    Text(
                        text = HistoryItemStrings.Weight,
                        style = MeTheme.typography.subHeading2,
                        color = unitColor,
                    )
                }
                // Length
                Column {
                    Text(text = lengthText, style = MeTheme.typography.heading5)
                    Text(
                        text = HistoryItemStrings.Length,
                        style = MeTheme.typography.subHeading2,
                        color = unitColor,
                    )
                }
                // Percentile
                Column {
                    Text(text = percentText, style = MeTheme.typography.heading5)
                    Text(
                        text = HistoryItemStrings.Percent,
                        style = MeTheme.typography.subHeading2,
                        color = unitColor,
                    )
                }
            }
            // Chevron
            AppIcon(
                id = AppIcons.Default.RightCaret,
                contentDescription = HistoryItemStrings.GoToMonthView,
                onClick = null,
            )
        }
        HorizontalDivider(
            thickness = MeTheme.spacing.x6s,
            color = MeTheme.colorScheme.utility,
        )
    }
}
