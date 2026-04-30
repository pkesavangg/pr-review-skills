package com.dmdbrands.gurus.weight.features.ScaleCustomization.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun CustomizationLayout(
  title: String? = null,
  subtitle: String? = null,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize().padding(horizontal = spacing.sm),
  ) {
    title?.let {
      AppText(
        text = title,
        textType = TextType.ListTitle2,
        modifier = Modifier.padding(bottom = spacing.xs),
      )
    }
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
