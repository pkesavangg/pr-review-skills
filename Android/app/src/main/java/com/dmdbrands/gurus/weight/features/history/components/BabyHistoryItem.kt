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
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import java.util.Locale
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
    babyWeightUnit: WeightUnit = WeightUnit.LB_OZ,
    showBalloon: Boolean = false,
) {
    val babyColor = MeTheme.colorScheme.baby
    val unitColor = MeTheme.colorScheme.textSubheading
    val boldStyle = SpanStyle(color = babyColor, fontWeight = FontWeight.Bold)
    val unitStyle = SpanStyle(color = unitColor, fontWeight = FontWeight.Normal)

    // Weight per My Kids unit: "8 lbs 14.9 oz" (LB_OZ), "8.9 lbs" (LB) or "4.05 kg" (KG).
    val weightText = buildAnnotatedString {
        val dg = item.weightDecigrams
        if (dg != null) {
            when (babyWeightUnit) {
                WeightUnit.KG -> {
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.2f", ConversionTools.convertDecigramsToKg(dg))) }
                    withStyle(unitStyle) { append(" kg") }
                }
                WeightUnit.LB -> {
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", ConversionTools.convertDecigramsToLbExact(dg))) }
                    withStyle(unitStyle) { append(" lb") }
                }
                else -> {
                    withStyle(boldStyle) { append("${ConversionTools.convertDecigramsToLb(dg)} ") }
                    withStyle(unitStyle) { append("lb ") }
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", ConversionTools.convertDecigramsToOz(dg))) }
                    withStyle(unitStyle) { append(" oz") }
                }
            }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    val lengthText = buildAnnotatedString {
        val mm = item.lengthMillimeters
        if (mm != null) {
            if (babyWeightUnit == WeightUnit.KG) {
                withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", ConversionTools.convertMmToCm(mm))) }
                withStyle(unitStyle) { append(" cm") }
            } else {
                withStyle(boldStyle) { append("${ConversionTools.convertMmToInches(mm).toInt()}") }
                withStyle(unitStyle) { append(" in") }
            }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // Capped to "> 99" / "< 1" at the extremes (matches the CDC sheet + babyApp reference —
    // never "100th"/"0th"). "--" for null. (MOB-1499)
    val percentText = buildAnnotatedString {
        val label = BabyPercentileHelper.formatPercentileNumber(item.percentile)
        if (label != null) {
            withStyle(boldStyle) { append(label) }
            if (label.all { it.isDigit() }) withStyle(unitStyle) { append(" th") }
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
