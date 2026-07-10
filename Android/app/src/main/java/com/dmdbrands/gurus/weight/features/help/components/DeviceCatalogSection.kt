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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Expandable device catalog section with icon, title, and chevron.
 */
@Composable
fun DeviceCatalogSection(
  title: String,
  iconId: Int,
  iconTint: Color,
  devices: List<DeviceModelInfo>,
  onDeviceSelected: (DeviceModelInfo) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

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
          id = iconId,
          contentDescription = title,
          tintColor = iconTint,
          modifier = Modifier.size(24.dp),
          onClick = null,
        )
        AppText(
          text = title,
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
          devices.forEach { device ->
            AppDeviceCard(
              scale = device,
              isSavedScale = false,
              onClick = onDeviceSelected,
            )
          }
        }
      }
    }
  }
}
