package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeTheme

enum class MeasurementType {
    WEIGHT,
    BLOOD_PRESSURE,
    BABY,
    PULSE,
}

fun measurementAccentColor(type: MeasurementType): Color = when (type) {
    MeasurementType.WEIGHT -> SnapshotColors.Weight
    MeasurementType.BLOOD_PRESSURE -> SnapshotColors.BloodPressure
    MeasurementType.BABY -> SnapshotColors.Baby
    MeasurementType.PULSE -> SnapshotColors.BloodPressure
}

private val TOKEN_PATTERN = Regex("[\\d.]+|[a-zA-Z]+")

/**
 * Builds an [AnnotatedString] from a measurement string like "146.8 lbs" or "14 lbs 6 oz".
 *
 * - Numbers → [valueSpan] (accent color, value size)
 * - Letters/units → [unitSpan] (muted color, unit size)
 * - Separators (`/`, `·`, `-`, spaces) → [separatorSpan] (muted color, value size)
 */
fun buildMeasurementText(
    text: String,
    valueSpan: SpanStyle,
    unitSpan: SpanStyle,
    separatorSpan: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    TOKEN_PATTERN.findAll(text).forEach { match ->
        // Text between tokens (separators like "/", "·", spaces)
        if (match.range.first > lastEnd) {
            withStyle(separatorSpan) { append(text.substring(lastEnd, match.range.first)) }
        }
        // Numbers or letters
        val isNumeric = match.value.first().isDigit() || match.value.first() == '.'
        withStyle(if (isNumeric) valueSpan else unitSpan) { append(match.value) }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        withStyle(separatorSpan) { append(text.substring(lastEnd)) }
    }
}

/**
 * Composable wrapper that resolves theme tokens and caches the result with [remember].
 */
@Composable
fun rememberMeasurementText(
    text: String,
    type: MeasurementType,
    valueStyle: TextStyle = MeTheme.typography.heading2,
    unitStyle: TextStyle = MeTheme.typography.subHeading2,
    unitColor: Color = MeTheme.colorScheme.textSubheading,
): AnnotatedString {
    val accentColor = measurementAccentColor(type)
    val valueSpan = valueStyle.toSpanStyle().copy(color = accentColor)
    val unitSpan = unitStyle.toSpanStyle().copy(color = unitColor)
    val separatorSpan = unitStyle.toSpanStyle().copy(color = unitColor, fontSize = valueStyle.fontSize)
    return remember(text, accentColor, valueSpan, unitSpan, separatorSpan) {
        buildMeasurementText(text, valueSpan, unitSpan, separatorSpan)
    }
}

/** Overload accepting an explicit accent color instead of [MeasurementType]. */
@Composable
fun rememberMeasurementText(
    text: String,
    accentColor: Color,
    valueStyle: TextStyle = MeTheme.typography.heading2,
    unitStyle: TextStyle = MeTheme.typography.subHeading2,
    unitColor: Color = MeTheme.colorScheme.textSubheading,
): AnnotatedString {
    val valueSpan = valueStyle.toSpanStyle().copy(color = accentColor)
    val unitSpan = unitStyle.toSpanStyle().copy(color = unitColor)
    val separatorSpan = unitStyle.toSpanStyle().copy(color = unitColor, fontSize = valueStyle.fontSize)
    return remember(text, accentColor, valueSpan, unitSpan, separatorSpan) {
        buildMeasurementText(text, valueSpan, unitSpan, separatorSpan)
    }
}
