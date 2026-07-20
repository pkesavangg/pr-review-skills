package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyDashboardGraph
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.DashboardSnapshotViewModel
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.SnapshotChartData
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/** Shared big-value text style for snapshot cards (Figma-specified 48sp ExtraBold). */
private val SnapshotValueStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)

/** Label font size for units/axis text inside snapshot cards (Figma). */
private val SnapshotLabelFontSize = 14.sp

// TODO(MA-XXXX): Migrate to MeAppTheme color tokens when design tokens are mapped
object SnapshotColors {
    val Weight = Color(0xFF1565C0)
    val BloodPressure = Color(0xFF458239)
    val Baby = Color(0xFF8841A4)
    val PercentileBand = Color(0xFFD0CCCA)
    val GoalBadge = Color(0xFF4F8A3F)

    // BP severity colors (from ggBluetoothNativeLibrary)
    val BpNormal = Color(0xFFA9D045)
    val BpElevated = Color(0xFFFFDD00)
    val BpHyperTension1 = Color(0xFFEB9927)
    val BpHyperTension2 = Color(0xFFE26203)
    val BpHypertensiveCrisis = Color(0xFF84000A)
    val PulseNormal = Color(0xFF00B3E3)

    fun systolicColor(value: Int): Color = when {
        value > 180 -> BpHypertensiveCrisis
        value > 139 -> BpHyperTension2
        value > 129 -> BpHyperTension1
        value > 119 -> BpElevated
        else -> BpNormal
    }

    fun diastolicColor(value: Int): Color = when {
        value > 120 -> BpHypertensiveCrisis
        value > 89 -> BpHyperTension2
        value > 79 -> BpHyperTension1
        else -> BpNormal
    }

    fun pulseColor(value: Int): Color = when {
        value < 50 -> BpHypertensiveCrisis
        value < 60 -> BpElevated
        value <= 100 -> PulseNormal
        value <= 120 -> BpHyperTension1
        else -> BpHypertensiveCrisis
    }
}

@Composable
fun WeightSnapshotCard(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<DashboardSnapshotViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SnapshotCardContainer(modifier = modifier.testTag(TestTags.Dashboard.WeightCard), onClickLabel = DashboardSnapshotStrings.OpenWeightDashboard, onTap = onTap) {
        val chart = state.weight

        Text(
            text = if (chart.isEmpty) DashboardSnapshotStrings.NoEntries else DashboardSnapshotStrings.WeekAverage,
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )

        Row(verticalAlignment = Alignment.Bottom ,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm)
        ){
            Text(
                text = if (chart.isEmpty) DashboardSnapshotStrings.ZeroWeight else chart.label.ifEmpty { DashboardSnapshotStrings.PlaceholderDash },
                color = SnapshotColors.Weight,
                style = SnapshotValueStyle,
            )
            Text(
                text = " ${state.weightUnit.label}",
                style = MeTheme.typography.subHeading2,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(bottom = MeTheme.spacing.xs),
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.isEmpty) {
            EmptyDashboardGraph(
                modifier = Modifier
                    .fillMaxWidth()
                    // TalkBack: an empty chart is otherwise invisible — announce it.
                    .semantics { contentDescription = DashboardSnapshotStrings.accEmptyChartDescription },
            )
        } else if (chart.startTimestamp != null && chart.endTimestamp != null) {
            SnapshotLineChart(
                modelProducer = viewModel.weightModelProducer,
                lineColor = SnapshotColors.Weight,
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
                chartContentDescription = "${DashboardSnapshotStrings.accChartSummaryPrefix} " +
                    "${chart.label} ${state.weightUnit.label} ${DashboardSnapshotStrings.WeekAverage}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BpSnapshotCard(
    viewModel: DashboardSnapshotViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart = state.bp

    SnapshotCardContainer(modifier = modifier.testTag(TestTags.Dashboard.BpCard), onClickLabel = DashboardSnapshotStrings.OpenBpDashboard, onTap = onTap) {
        Row(modifier = Modifier.padding(horizontal = MeTheme.spacing.sm)) {
            Text(
                text = DashboardSnapshotStrings.Mmhg,
                style = MeTheme.typography.subHeading1,
                color = MeTheme.colorScheme.textSubheading,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DashboardSnapshotStrings.Pulse,
                style = MeTheme.typography.subHeading1,
                color = MeTheme.colorScheme.textSubheading,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))

        // Parse systolic/diastolic from label "120/80"
        val parts = chart.label.split("/")
        val systolic = parts.getOrNull(0)?.toIntOrNull()
        val diastolic = parts.getOrNull(1)?.toIntOrNull()
        val pulse = chart.secondaryLabel.toIntOrNull()

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        ) {
            if (chart.isEmpty) {
                Text(
                    text = "${DashboardSnapshotStrings.ZeroSystolic}/${DashboardSnapshotStrings.ZeroDiastolic}",
                    color = SnapshotColors.BloodPressure,
                    style = SnapshotValueStyle,
                )
            } else if (systolic != null && diastolic != null) {
                BpSystolicDiastolic(
                    systolic = systolic,
                    diastolic = diastolic,
                    style = SnapshotValueStyle,
                )
            } else {
                Text(
                    text = DashboardSnapshotStrings.PlaceholderDash,
                    color = SnapshotColors.BloodPressure,
                    style = SnapshotValueStyle,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (chart.isEmpty) DashboardSnapshotStrings.ZeroPulse else chart.secondaryLabel.ifEmpty { DashboardSnapshotStrings.PlaceholderDash },
                color = if (pulse != null) SnapshotColors.pulseColor(pulse) else MeTheme.colorScheme.textSubheading,
                style = SnapshotValueStyle,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.isEmpty) {
            EmptyDashboardGraph(
                modifier = Modifier
                    .fillMaxWidth()
                    // TalkBack: an empty chart is otherwise invisible — announce it.
                    .semantics { contentDescription = DashboardSnapshotStrings.accEmptyChartDescription },
            )
        } else if (chart.startTimestamp != null && chart.endTimestamp != null) {
            val chartLineColors = listOfNotNull(
                systolic?.let { SnapshotColors.systolicColor(it) },
                diastolic?.let { SnapshotColors.diastolicColor(it) },
                pulse?.let { SnapshotColors.pulseColor(it) },
            )

            SnapshotLineChart(
                modelProducer = viewModel.bpModelProducer,
                lineColor = SnapshotColors.BloodPressure,
                lineColors = chartLineColors.ifEmpty { null },
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
                chartContentDescription = "${DashboardSnapshotStrings.accChartSummaryPrefix} " +
                    "${chart.label} ${DashboardSnapshotStrings.Mmhg}, " +
                    "${chart.secondaryLabel} ${DashboardSnapshotStrings.Pulse}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BabySnapshotCard(
    product: ProductSelection.Baby,
    viewModel: DashboardSnapshotViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart = state.baby[product.profile.id] ?: SnapshotChartData()

    SnapshotCardContainer(modifier = modifier.testTag(TestTags.Dashboard.BabyCard), onClickLabel = DashboardSnapshotStrings.OpenBabyDashboard, onTap = onTap) {
        Text(
            text = if (chart.isEmpty) {
                DashboardSnapshotStrings.NoEntries
            } else {
                "${product.profile.name.lowercase()}'s ${DashboardSnapshotStrings.Weight}"
            },
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))

        val displayLabel = if (chart.isEmpty) {
            "${DashboardSnapshotStrings.ZeroBabyLbs} ${DashboardSnapshotStrings.Lbs} ${DashboardSnapshotStrings.ZeroBabyOz} ${DashboardSnapshotStrings.Oz}"
        } else {
            chart.label
        }

        if (displayLabel.isNotEmpty() && displayLabel != DashboardSnapshotStrings.PlaceholderDash) {
            // Parse "8 lbs 14.4 oz" → numbers large, units small inline
            Text(
                text = buildAnnotatedString {
                    val parts = displayLabel.split(" ")
                    parts.forEachIndexed { i, part ->
                        if (i > 0) append(" ")
                        if (part.toDoubleOrNull() != null || part.toIntOrNull() != null) {
                            withStyle(SpanStyle(fontSize = SnapshotValueStyle.fontSize, fontWeight = SnapshotValueStyle.fontWeight)) {
                                append(part)
                            }
                        } else {
                            withStyle(SpanStyle(fontSize = SnapshotLabelFontSize, fontWeight = FontWeight.Normal, color = MeTheme.colorScheme.textSubheading)) {
                                append(part)
                            }
                        }
                    }
                },
                color = SnapshotColors.Baby,
                modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
            )
        } else {
            Text(
                text = DashboardSnapshotStrings.PlaceholderDash,
                color = SnapshotColors.Baby,
                style = SnapshotValueStyle,
                modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.isEmpty) {
            EmptyDashboardGraph(
                modifier = Modifier
                    .fillMaxWidth()
                    // TalkBack: an empty chart is otherwise invisible — announce it.
                    .semantics { contentDescription = DashboardSnapshotStrings.accEmptyChartDescription },
            )
        } else if (chart.startTimestamp != null && chart.endTimestamp != null) {
            SnapshotLineChart(
                modelProducer = viewModel.getBabyModelProducer(product.profile.id),
                lineColor = SnapshotColors.Baby,
                secondaryLayerColor = if (chart.hasPercentile) SnapshotColors.PercentileBand else null,
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
                chartContentDescription = "${DashboardSnapshotStrings.accChartSummaryPrefix} " +
                    "${product.profile.name.lowercase()}'s ${DashboardSnapshotStrings.Weight} ${chart.label}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTheme
@Composable
private fun BpSnapshotCardPreview() {
    MeAppTheme {
        // Preview requires Hilt — use interactive preview or device preview
    }
}
