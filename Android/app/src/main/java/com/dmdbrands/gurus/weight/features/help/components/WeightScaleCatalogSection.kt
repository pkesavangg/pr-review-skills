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
import com.dmdbrands.gurus.weight.features.common.components.AppScaleCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSegmentType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.help.strings.HelpScreenStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Weight Scale catalog with SegmentButtonGroup filter (All/Bluetooth/Wifi/AppSync).
 */
@Composable
fun WeightScaleCatalogSection(
  onScaleSelected: (ScaleInfo) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var selectedType by remember { mutableStateOf(ScaleSegmentType.All) }

  val filteredScales = remember(selectedType) {
    when (selectedType) {
      ScaleSegmentType.All -> SCALES
      ScaleSegmentType.AppSync -> SCALES.filter { it.setupType == ScaleSetupType.AppSync }
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

  Column(modifier = Modifier.fillMaxWidth()) {
    HorizontalDivider(thickness = 0.5.dp, color = MeTheme.colorScheme.utility)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
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

    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        SegmentButtonGroup(
          data = listOf(
            ScaleSegmentType.All,
            ScaleSegmentType.Bluetooth,
            ScaleSegmentType.Wifi,
            ScaleSegmentType.AppSync,
          ),
          key = ScaleSegmentType::name,
          selectedData = selectedType,
          onSelected = { selectedType = it },
          size = SegmentButtonSize.Small,
          type = SegmentButtonType.Scrollable,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
        )
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm),
          shape = RoundedCornerShape(MeTheme.borderRadius.md),
          colors = CardDefaults.cardColors(
            containerColor = MeTheme.colorScheme.primaryBackground,
          ),
        ) {
          Column {
            filteredScales.forEach { scale ->
              AppScaleCard(
                scale = scale,
                isSavedScale = false,
                onClick = onScaleSelected,
              )
            }
          }
        }
      }
    }
  }
}
