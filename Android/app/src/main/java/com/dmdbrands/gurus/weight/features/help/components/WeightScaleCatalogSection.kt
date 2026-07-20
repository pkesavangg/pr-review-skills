package com.dmdbrands.gurus.weight.features.help.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSegmentType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.WEIGHT_SCALES
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.features.help.strings.HelpScreenStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Weight Scale catalog with SegmentButtonGroup filter (All/Bluetooth/Wifi/AppSync).
 */
@Composable
fun WeightScaleCatalogSection(
  onScaleSelected: (DeviceModelInfo) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var selectedType by remember { mutableStateOf(DeviceSegmentType.All) }
  val filteredScales = remember(selectedType) { filterWeightScales(selectedType) }

  Column(modifier = Modifier.fillMaxWidth()) {
    HorizontalDivider(thickness = 0.5.dp, color = MeTheme.colorScheme.utility)
    WeightScaleHeader(expanded = expanded, onToggle = { expanded = !expanded })
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      WeightScaleExpandedContent(
        selectedType = selectedType,
        onTypeSelected = { selectedType = it },
        filteredScales = filteredScales,
        onScaleSelected = onScaleSelected,
      )
    }
  }
}

private fun filterWeightScales(selectedType: DeviceSegmentType): List<DeviceModelInfo> =
  when (selectedType) {
    DeviceSegmentType.All -> WEIGHT_SCALES
    DeviceSegmentType.AppSync -> WEIGHT_SCALES.filter { it.setupType == DeviceSetupType.AppSync }
    DeviceSegmentType.Bluetooth -> WEIGHT_SCALES.filter {
      it.setupType == DeviceSetupType.Bluetooth ||
        it.setupType == DeviceSetupType.Lcbt ||
        it.setupType == DeviceSetupType.BtWifiR4
    }
    DeviceSegmentType.Wifi -> WEIGHT_SCALES.filter {
      it.setupType == DeviceSetupType.Wifi ||
        it.setupType == DeviceSetupType.EspTouchWifi ||
        it.setupType == DeviceSetupType.BtWifiR4
    }
  }

@Composable
private fun WeightScaleHeader(
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onToggle() }
      .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    ) {
      AppIcon(
        id = AppIcons.Setup.WeightScale,
        contentDescription = HelpScreenStrings.WeightScale,
        tintColor = MeTheme.colorScheme.wgPrimary,
        modifier = Modifier.size(24.dp),
        onClick = null,
      )
      AppText(
        text = HelpScreenStrings.WeightScale,
        textType = TextType.ListTitle1,
      )
    }
    AppIcon(
      id = AppIcons.Default.ChevronDown,
      contentDescription = if (expanded) "Collapse" else "Expand",
      modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
      onClick = null,
    )
  }
}

@Composable
private fun WeightScaleExpandedContent(
  selectedType: DeviceSegmentType,
  onTypeSelected: (DeviceSegmentType) -> Unit,
  filteredScales: List<DeviceModelInfo>,
  onScaleSelected: (DeviceModelInfo) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    SegmentButtonGroup(
      data = listOf(
        DeviceSegmentType.All,
        DeviceSegmentType.Bluetooth,
        DeviceSegmentType.Wifi,
        DeviceSegmentType.AppSync,
      ),
      key = DeviceSegmentType::name,
      selectedData = selectedType,
      onSelected = onTypeSelected,
      size = SegmentButtonSize.Small,
      type = SegmentButtonType.Scrollable,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
    )
    WeightScaleCard(filteredScales = filteredScales, onScaleSelected = onScaleSelected)
  }
}

@Composable
private fun WeightScaleCard(
  filteredScales: List<DeviceModelInfo>,
  onScaleSelected: (DeviceModelInfo) -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm),
    shape = RoundedCornerShape(MeTheme.borderRadius.lg),
    colors = CardDefaults.cardColors(
      containerColor = MeTheme.colorScheme.primaryBackground,
    ),
  ) {
    Column {
      filteredScales.forEach { scale ->
        AppDeviceCard(
          scale = scale,
          isSavedScale = false,
          // Full product name wraps across lines (matches the Figma card structure).
          wrapSubtitle = true,
          // White card rows on the #F6F4F1 (secondary) screen background, per the design.
          containerColor = MeTheme.colorScheme.primaryBackground,
          onClick = onScaleSelected,
        )
      }
    }
  }
}
