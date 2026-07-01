package com.dmdbrands.gurus.weight.features.DeviceCustomization.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.DeviceSetup.model.CustomizeSettingsCard
import com.dmdbrands.gurus.weight.features.DeviceSetup.model.CustomizeSettingsList
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Composable that displays a single Customization Settings Item item.
 *
 * @param settings Customize Settings Card details
 * @param onClick Callback when the item is clicked
 */
@Composable
fun CustomizationSettingsItem(
  settings: CustomizeSettingsCard,
  onClick: (selectedSettings: CustomizeSettings) -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick(settings.step) },
    shape = RoundedCornerShape(borderRadius.sm),
    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryBackground),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(spacing.sm),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
      )
      {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
          AppIcon(
            id = settings.iconId,
            contentDescription = "Metric Card",
            modifier = Modifier.size(24.dp),
          )

          AppText(
            text = settings.title,
            textType = TextType.ListTitle1,
          )
        }
        Row {
          AppText(
            text = settings.subtitle,
            textType = TextType.Body,
          )
        }
      }

      AppIcon(
        id = if (!settings.isVisited) AppIcons.Default.RightCaret else AppIcons.Selection.CircleSelected,
        contentDescription = "Right caret",
        modifier = if (!settings.isVisited) Modifier.size(24.dp) else Modifier.size(18.dp),
        onClick = null
      )
    }
  }
}

@PreviewTheme()
@Composable
fun CustomizationSettingsItemPreview() {
  MeAppTheme {
    Column {
      CustomizationSettingsItem(
        settings = CustomizeSettingsList.first(),
        onClick = {}
      )
    }
  }
}
