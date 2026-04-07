package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.DashboardSnapshotViewModel
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.SnapshotChartData
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

object SnapshotColors {
    val Weight = Color(0xFF1565C0)
    val BloodPressure = Color(0xFF458239)
    val Baby = Color(0xFF8841A4)
    val PercentileBand = Color(0xFFD0CCCA)

    // BP severity colors (from ggBluetoothNativeLibrary)
    val BpNormal = Color(0xFFA9D045)
    val BpElevated = Color(0xFFFFDD00)
    val BpHyperTension1 = Color(0xFFEB9927)
    val BpHyperTension2 = Color(0xFFE26203)
    val BpHyperSensitive = Color(0xFF84000A)
    val PulseNormal = Color(0xFF00B3E3)

    fun systolicColor(value: Int): Color = when {
        value > 180 -> BpHyperSensitive
        value > 139 -> BpHyperTension2
        value > 129 -> BpHyperTension1
        value > 119 -> BpElevated
        else -> BpNormal
    }

    fun diastolicColor(value: Int): Color = when {
        value > 120 -> BpHyperSensitive
        value > 89 -> BpHyperTension2
        value > 79 -> BpHyperTension1
        else -> BpNormal
    }

    fun pulseColor(value: Int): Color = when {
        value < 50 -> BpHyperSensitive
        value < 60 -> BpElevated
        value <= 100 -> PulseNormal
        value <= 120 -> BpHyperTension1
        else -> BpHyperSensitive
    }
}

@Composable
fun WeightSnapshotCard(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<DashboardSnapshotViewModel>()
    val state by viewModel.state.collectAsState()

    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        val chart = state.weight

        Text(
            text = DashboardSnapshotStrings.WeekAverage,
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )

        Row(verticalAlignment = Alignment.Bottom ,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm)
        ){
            Text(
                text = chart.label.ifEmpty { DashboardSnapshotStrings.PlaceholderDash },
                color = SnapshotColors.Weight,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = " ${state.weightUnit.label}",
                style = MeTheme.typography.subHeading2,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(bottom = MeTheme.spacing.xs),
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.startTimestamp != null && chart.endTimestamp != null) {
            SnapshotLineChart(
                modelProducer = viewModel.weightModelProducer,
                lineColor = SnapshotColors.Weight,
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
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
    val state by viewModel.state.collectAsState()
    val chart = state.bp

    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        Row {
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

        Row(verticalAlignment = Alignment.Bottom) {
            if (systolic != null && diastolic != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = SnapshotColors.systolicColor(systolic))) {
                            append("$systolic")
                        }
                        withStyle(SpanStyle(color = MeTheme.colorScheme.textSubheading)) {
                            append("/")
                        }
                        withStyle(SpanStyle(color = SnapshotColors.diastolicColor(diastolic))) {
                            append("$diastolic")
                        }
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            } else {
                Text(
                    text = DashboardSnapshotStrings.PlaceholderDash,
                    color = SnapshotColors.BloodPressure,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = chart.secondaryLabel.ifEmpty { DashboardSnapshotStrings.PlaceholderDash },
                color = if (pulse != null) SnapshotColors.pulseColor(pulse) else MeTheme.colorScheme.textSubheading,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.startTimestamp != null && chart.endTimestamp != null) {
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
    val state by viewModel.state.collectAsState()
    val chart = state.baby[product.profile.id] ?: SnapshotChartData()

    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        Text(
            text = "${product.profile.name}'s ${DashboardSnapshotStrings.Weight}",
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))

        if (chart.label.isNotEmpty() && chart.label != "—") {
            // Parse "8 lbs 14.4 oz" → numbers large, units small inline
            Text(
                text = buildAnnotatedString {
                    val parts = chart.label.split(" ")
                    parts.forEachIndexed { i, part ->
                        if (i > 0) append(" ")
                        if (part.toDoubleOrNull() != null || part.toIntOrNull() != null) {
                            withStyle(SpanStyle(fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)) {
                                append(part)
                            }
                        } else {
                            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)) {
                                append(part)
                            }
                        }
                    }
                },
                color = SnapshotColors.Baby,
            )
        } else {
            Text(
                text = DashboardSnapshotStrings.PlaceholderDash,
                color = SnapshotColors.Baby,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.startTimestamp != null && chart.endTimestamp != null) {
            SnapshotLineChart(
                modelProducer = viewModel.getBabyModelProducer(product.profile.id),
                lineColor = SnapshotColors.Baby,
                secondaryLayerColor = if (chart.hasPercentile) SnapshotColors.PercentileBand else null,
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTheme
@Composable
private fun BpSnapshotCardPreview() {
    MeAppTheme {
        BpSnapshotCard(viewModel = hiltViewModel(), onTap = {})
    }
}
