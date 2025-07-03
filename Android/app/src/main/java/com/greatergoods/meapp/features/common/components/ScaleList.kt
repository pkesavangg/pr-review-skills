package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.enums.ScaleSegmentType
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.model.ScaleInfo
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Composable for the scale segment selector (All, Bluetooth, Wifi) using SegmentButtonGroup.
 * Displays a filtered list of AppScaleCard based on the selected segment.
 *
 * @param onScaleSelected Callback when a scale is selected from the list.
 * @param modifier Modifier to be applied to the component.
 * @param enableScroll Whether the scale list should have its own vertical scroll. Set to false when used inside a scrollable parent.
 */
@Composable
fun ScaleList(
    onScaleSelected: (ScaleInfo) -> Unit,
    modifier: Modifier = Modifier,
    enableScroll: Boolean = true,
) {
    var selectedType by remember { mutableStateOf(ScaleSegmentType.All) }

    val filteredScales = remember(selectedType) {
        when (selectedType) {
            ScaleSegmentType.All -> SCALES
            ScaleSegmentType.Bluetooth -> SCALES.filter {
                it.setupType == ScaleSetupType.Bluetooth ||
                    it.setupType == ScaleSetupType.Lcbt ||
                    it.setupType == ScaleSetupType.BtWifiR4
            }

            ScaleSegmentType.Wifi -> SCALES.filter {
                it.setupType == ScaleSetupType.Wifi ||
                    it.setupType == ScaleSetupType.EspTouchWifi ||
                    it.setupType == ScaleSetupType.BtWifiR4
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm),
    ) {
        // Segment control
        SegmentButtonGroup(
            data = listOf(
                ScaleSegmentType.All,
                ScaleSegmentType.Bluetooth,
                ScaleSegmentType.Wifi,
            ),
            key = ScaleSegmentType::name,
            selectedData = selectedType,
            onSelected = { selectedType = it },
            size = SegmentButtonSize.Small,
            type = SegmentButtonType.Scrollable,
        )
        Spacer(modifier = Modifier.height(spacing.md))
        // List of filtered scales
    }
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        filteredScales.forEach { scale ->
            AppScaleCard(
                scale = scale,
                isSavedScale = false,
                onClick = onScaleSelected,
            )
        }
    }
}

/**
 * Preview for ScaleSegment composable with all segments.
 */
@PreviewTheme
@Composable
fun PreviewScaleSegment() {
    MeAppTheme {
        Column {
            ScaleList(
                onScaleSelected = {},
            )
        }
    }
}
