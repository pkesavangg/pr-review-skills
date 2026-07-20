package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Locale

/**
 * A single baby history detail entry row.
 * Shows time, weight (lb/oz), length (in), percentile — values in baby purple
 * with inline unit labels in gray, matching Figma.
 */
@Suppress("LongMethod")
@Composable
fun BabyDayHistoryItem(
    item: BabyEntry,
    babyWeightUnit: WeightUnit = WeightUnit.LB_OZ,
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

    // Build weight text per unit: "8 lbs 14.9 oz" (LB_OZ), "8.9 lbs" (LB), or "4.05 kg" (KG).
    // Source-aware graduation is applied inside ConversionTools.
    val weightText = buildAnnotatedString {
        val dg = item.babyWeightDecigrams
        val source = item.babyEntry.source
        if (dg != null) {
            when (babyWeightUnit) {
                WeightUnit.KG -> {
                    val kg = ConversionTools.convertBabyWeightToKg(dg, source)
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.2f", kg)) }
                    withStyle(unitStyle) { append(" kg") }
                }
                WeightUnit.LB -> {
                    // Decimal pounds, derived from the graduated lb+oz so it matches the lb-oz view.
                    val (lbs, oz) = ConversionTools.convertBabyWeightToLbOz(dg, source)
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", lbs + oz / 16.0)) }
                    withStyle(unitStyle) { append(" lb") }
                }
                else -> {
                    val (lbs, oz) = ConversionTools.convertBabyWeightToLbOz(dg, source)
                    withStyle(boldStyle) { append("$lbs ") }
                    withStyle(unitStyle) { append("lb ") }
                    withStyle(boldStyle) { append(String.format(Locale.US, "%.1f", oz)) }
                    withStyle(unitStyle) { append(" oz") }
                }
            }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // Build length text: "12 in" or "30.5 cm" (cm only for the metric/KG unit).
    val lengthText = buildAnnotatedString {
        val mm = item.babyLengthMillimeters
        if (mm != null) {
            if (babyWeightUnit == WeightUnit.KG) {
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

    // Build percentile text: "6 th", capped to "> 99" / "< 1" at the extremes (matches the CDC
    // sheet + the babyApp reference, which never shows "100th"/"0th"). "--" for null. (MOB-1499)
    val percentText = buildAnnotatedString {
        val label = BabyPercentileHelper.formatPercentileNumber(item.percentile)
        if (label != null) {
            withStyle(boldStyle) { append(label) }
            // Ordinal suffix only for a plain numeric percentile; "> 99" / "< 1" stand alone.
            if (label.all { it.isDigit() }) withStyle(unitStyle) { append(" th") }
        } else {
            withStyle(boldStyle) { append("--") }
        }
    }

    // TalkBack: read the baby entry as one announcement with an expand/collapse state, e.g.
    // "6:30 AM, weight 8 lbs 14.9 oz, length 12 in, percentile --".
    val rowDescription = buildString {
        append(item.getTime())
        append(", ${HistoryItemStrings.accWeightLabel} ${weightText.text}")
        append(", ${HistoryItemStrings.accLengthLabel} ${lengthText.text}")
        append(", ${HistoryItemStrings.accPercentileLabel} ${percentText.text}")
    }
    val expandState = if (isExpanded) {
        HistoryDetailScreenStrings.accExpandedState
    } else {
        HistoryDetailScreenStrings.accCollapsedState
    }

    // Opaque row fill (white collapsed, grey when expanded) so the red swipe-to-delete action
    // behind the row can't bleed through the content — the row had no background. (MOB-1259)
    Column(
        modifier = Modifier
            .testTag(TestTags.History.EntryRow)
            .testTag(TestTags.History.BabyEntryRow)
            .background(
                if (isExpanded) MeTheme.colorScheme.secondaryBackground else MeTheme.colorScheme.primaryBackground,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    stateDescription = expandState
                }
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
                contentDescription = if (isExpanded) {
                    HistoryItemStrings.CollapseNote
                } else {
                    HistoryItemStrings.ExpandNote
                },
                onClick = { onToggleExpand() },
                modifier = Modifier.rotate(rotation),
            )
        }

        // Expandable note — shows the saved note or an add-note prompt. The trailing icon is a
        // "+" when no note exists (add) and the boxed pencil once a note is present (edit) (MOB-1163).
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(
                    start = MeTheme.spacing.sm,
                    end = MeTheme.spacing.sm,
                    top = MeTheme.spacing.sm,
                    bottom = MeTheme.spacing.sm,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    // Empty state centres the placeholder + "+" affordance; an existing note stays
                    // left-aligned with the pencil pinned to the end (MOB-1163).
                    horizontalArrangement = if (hasNote) Arrangement.Start else Arrangement.Center,
                ) {
                    if (hasNote) {
                        ExpandableNoteText(
                            note = item.entryNote.orEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = HistoryItemStrings.NoNoteYet,
                            style = MeTheme.typography.subHeading2,
                            color = MeTheme.colorScheme.textSubheading,
                        )
                    }
                    AppIcon(
                        id = if (hasNote) AppIcons.Default.EditPencil else AppIcons.Default.Plus,
                        contentDescription = if (hasNote) {
                            HistoryItemStrings.EditNoteContentDescription
                        } else {
                            HistoryItemStrings.AddNoteContentDescription
                        },
                        onClick = { onEditEntry() },
                        modifier = Modifier
                            .padding(start = MeTheme.spacing.sm)
                            .testTag(TestTags.History.EditNoteButton),
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

private const val MAX_NOTE_LINES = 2
// Chars reserved at the end of line 2 so ".. more" fits after the truncated note.
private const val MORE_SUFFIX_RESERVE = 7

/**
 * Note text clamped to [MAX_NOTE_LINES] lines. When the note overflows, the tail is replaced with
 * ".. more" (a tappable link); tapping it expands to the full note and drops the link. Matches
 * Figma node 32794-207552 (MOB-1499).
 */
@Composable
private fun ExpandableNoteText(
    note: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(note) { mutableStateOf(false) }
    var truncated by remember(note) { mutableStateOf<String?>(null) }
    val bodyStyle = MeTheme.typography.subHeading2
    val bodyColor = MeTheme.colorScheme.textBody
    val linkStyle = SpanStyle(color = MeTheme.colorScheme.primaryAction, fontWeight = FontWeight.SemiBold)

    if (expanded || truncated == null) {
        // Full note when expanded; otherwise a 2-line pass to detect overflow and compute the cut.
        Text(
            text = note,
            style = bodyStyle,
            color = bodyColor,
            maxLines = if (expanded) Int.MAX_VALUE else MAX_NOTE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            onTextLayout = { result ->
                if (!expanded && truncated == null && result.hasVisualOverflow) {
                    val lastVisible = result.getLineEnd(MAX_NOTE_LINES - 1, visibleEnd = true)
                    val cut = (lastVisible - MORE_SUFFIX_RESERVE).coerceIn(0, note.length)
                    truncated = note.substring(0, cut).trimEnd()
                }
            },
        )
    } else {
        // Collapsed + overflowing: clamped note followed by an inline ".. more" link.
        val annotated = buildAnnotatedString {
            append(truncated)
            append(".. ")
            withStyle(linkStyle) { append(HistoryItemStrings.More) }
        }
        Text(
            text = annotated,
            style = bodyStyle,
            color = bodyColor,
            maxLines = MAX_NOTE_LINES,
            overflow = TextOverflow.Ellipsis,
            // Expose the expander to TalkBack as a button with a clear action label. (MOB-1499)
            modifier = modifier.clickable(
                onClickLabel = HistoryItemStrings.ShowFullNote,
                role = Role.Button,
            ) { expanded = true },
        )
    }
}
