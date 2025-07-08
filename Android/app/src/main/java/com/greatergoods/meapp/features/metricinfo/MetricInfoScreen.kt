package com.greatergoods.meapp.features.metricinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.SegmentButtonSize
import com.greatergoods.meapp.features.common.components.SegmentButtonType
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoInfoSection
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoResourcesSection
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoValueSection
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoStrings
import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Main entry point for the Metric Info screen. Handles ViewModel injection and state collection.
 *
 * @param viewModel The ViewModel for the screen.
 */
@Composable
fun MetricInfoScreen(viewModel: MetricInfoViewModel = viewModel()) {
    val selectedSegment = viewModel.selectedSegment.collectAsState().value
    val metricValue = viewModel.metricValue.collectAsState().value
    val metricUnit = viewModel.metricUnit.collectAsState().value
    val metricKeys = MetricKey.entries.toList().filter { it != MetricKey.UNRECOGNIZED }

    MetricInfoScreenContent(
        selectedSegment = selectedSegment,
        metricValue = metricValue,
        metricUnit = metricUnit,
        metricKeys = metricKeys,
        onSelectSegment = { viewModel.selectSegment(it) },
    )
}

/**
 * Content composable for the Metric Info screen. Displays metric details, info, and resources.
 *
 * @param selectedSegment The currently selected metric segment.
 * @param metricValue The value of the selected metric.
 * @param metricUnit The unit of the selected metric.
 * @param metricKeys The list of available metric keys.
 * @param onSelectSegment Callback when a segment is selected.
 */
@Composable
fun MetricInfoScreenContent(
    selectedSegment: MetricKey,
    metricValue: String,
    metricUnit: String,
    metricKeys: List<MetricKey>,
    onSelectSegment: (MetricKey) -> Unit,
) {
    AppScaffold(
        title = MetricInfoStrings.AppBarTitle,
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.primaryBackground,
    ) { modifier ->
        Column(
            modifier = modifier
                .padding(horizontal = MeTheme.spacing.sm),
        ) {
            SegmentButtonGroup(
                data = metricKeys,
                selectedData = selectedSegment,
                key = MetricKey::name,
                size = SegmentButtonSize.Medium,
                type = SegmentButtonType.Scrollable,
                onSelected = onSelectSegment,
            )
            MetricInfoValueSection(value = metricValue, unit = metricUnit, date = "")
            MetricInfoInfoSection()
            MetricInfoResourcesSection()
        }
    }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoScreenLight() {
    MeAppTheme {
        MetricInfoScreenContent(
            selectedSegment = MetricKey.entries.first { it != MetricKey.UNRECOGNIZED },
            metricValue = "72.5",
            metricUnit = "kg",
            metricKeys = MetricKey.entries.toList().filter { it != MetricKey.UNRECOGNIZED },
            onSelectSegment = {},
        )
    }
}
