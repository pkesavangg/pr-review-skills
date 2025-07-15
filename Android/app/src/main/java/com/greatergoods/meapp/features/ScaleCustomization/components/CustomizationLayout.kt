package com.greatergoods.meapp.features.ScaleCustomization.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun CustomizationLayout(
  title: String,
  subtitle: String? = null,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize(),
  ) {
    AppText(
      text = title,
      textType = TextType.ListTitle2,
      modifier = Modifier.padding(bottom = spacing.xs),
    )
    subtitle?.let {
      AppText(
        text = subtitle,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.lg),
      )
    }

    content()

  }
}

@PreviewTheme()
@Composable
fun CustomizationLayoutPreview() {
  MeAppTheme {
    CustomizationLayout(
      title = CustomizeSettingsStrings.DashboardMetrics.Title,
      subtitle = CustomizeSettingsStrings.DashboardMetrics.Subtitle,
      content = {},
    )
  }
}
