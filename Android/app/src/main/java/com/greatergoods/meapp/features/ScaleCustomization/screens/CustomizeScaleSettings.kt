package com.greatergoods.meapp.features.ScaleCustomization.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleCustomization.components.CustomizationSettingsItem
import com.greatergoods.meapp.features.ScaleSetup.enums.CustomizeSettings
import com.greatergoods.meapp.features.ScaleSetup.model.CustomizeSettingsList
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun CustomizeScaleSettings(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  onSelectSettings: (selectedSettings: CustomizeSettings) -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = spacing.sm, vertical = spacing.md),
  ) {
    // Title and Subtitle Section
    Column(
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
      AppText(
        text = title,
        textType = TextType.ListTitle2,
      )

      AppText(
        text = subtitle,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.lg),
      )
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
      CustomizeSettingsList.forEach {
        CustomizationSettingsItem(
          settings = it,
          onClick = onSelectSettings,
        )
      }

    }
  }
}


@PreviewTheme()
@Composable
fun CustomizeScaleSettingsPreview() {
  MeAppTheme {
    Column {
      CustomizeScaleSettings(
        title = "Customize your Settings",
        subtitle = "You can update settings at any time.",
        onSelectSettings = {},
      )
    }
  }
}
