package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Locale

/**
 * A single baby history detail entry row.
 * Shows time, weight (lb/oz), length (in), percentile — values in baby purple
 * with inline unit labels in gray, matching Figma.
 */
@Composable
fun BabyDayHistoryItem(
    item: BabyEntry,
    isMetric: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onEditEntry: () -> Unit = {},
) {
    val hasNote = !item.entryNote.isNullOrBlank()
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) -90f else 90f,
        label = "chevron",
    )
    val babyColor = MeTheme.colorScheme.baby
    val unitColor = MeTheme.colorScheme.textSubheading
    val boldStyle = SpanStyle(color = babyColor, fontWeight = FontWeight.Bold)
    val unitStyle = SpanStyle(color = unitColor, fontWeight = FontWeight.Normal)

    // Build weight text: "8 lbs 14.9 oz" or "4.05 kg"
    // Source-aware graduation is applied inside ConversionTools.
    val weightText = buildAnnotatedString {
        val dg = item.babyWeightDecigrams
        val source = item.babyEntry.source
        if (dg != null) {
            if (isMetric) {
                val kg = ConversionTools.convertBabyWeightToKg(dg, source)
                withStyle(boldStyle) { append(String.format(Locale.US, "%.2f", kg)) }
                withStyle(unitStyle) { append(" kg") }
            } else {
                val (lbs, oz) = ConversionTools.convertBabyWeightToLbOz(dg, source)
                withStyle(boldStyle) { append("$lbs ") }
                withStyle(unitStyle) { append("lbs ") }
                withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", oz)) }
                withStyle(unitStyle) { append(" oz") }
            }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // Build length text: "12 in" or "30.5 cm"
    val lengthText = buildAnnotatedString {
        val mm = item.babyLengthMillimeters
        if (mm != null) {
            if (isMetric) {
                withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", ConversionTools.convertMmToCm(mm))) }
                withStyle(unitStyle) { append(" cm") }
            } else {
                withStyle(boldStyle) { append(String.format(Locale.US, "%.0f", ConversionTools.convertMmToInches(mm))) }
                withStyle(unitStyle) { append(" in") }
            }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // Build percentile text: "6 th"
    val percentText = buildAnnotatedString {
        // Percentile not stored in BabyEntryEntity yet
        withStyle(boldStyle) { append("--") }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.lg),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Time
                Column {
                    Text(
                        text = item.getTime(),
                        style = MeTheme.typography.heading5,
                        color = MeTheme.colorScheme.textBody,
                    )
                }
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
            // Chevron — right caret rotated (up when expanded). Always present so entries
            // without a note can still expand to reveal the add-note affordance (MOB-438).
            AppIcon(
                id = AppIcons.Default.RightCaret,
                contentDescription = "",
                onClick = { onToggleExpand() },
                modifier = Modifier.rotate(rotation),
            )
        }

        // Expandable note — shows the saved note or an add-note prompt, plus an edit pencil.
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(
                    start = MeTheme.spacing.sm,
                    end = MeTheme.spacing.sm,
                    bottom = MeTheme.spacing.sm,
                ),
            ) {
                HorizontalDivider(
                    thickness = MeTheme.spacing.x6s,
                    color = MeTheme.colorScheme.utility,
                    modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (hasNote) item.entryNote.orEmpty() else HistoryItemStrings.NoNoteYet,
                        style = MeTheme.typography.subHeading2,
                        color = if (hasNote) MeTheme.colorScheme.textBody else MeTheme.colorScheme.textSubheading,
                        modifier = Modifier.weight(1f),
                    )
                    AppIcon(
                        id = AppIcons.Default.EditPencil,
                        contentDescription = HistoryItemStrings.EditNoteContentDescription,
                        onClick = { onEditEntry() },
                        modifier = Modifier.padding(start = MeTheme.spacing.sm),
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = MeTheme.spacing.x6s,
            color = MeTheme.colorScheme.utility,
        )
    }
}
