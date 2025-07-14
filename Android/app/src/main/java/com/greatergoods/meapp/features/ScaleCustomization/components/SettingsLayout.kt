package com.greatergoods.meapp.features.ScaleCustomization.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupHeader
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PagerBottomAppBar
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun SettingsLayout(
  title: String,
  subtitle: String? = null,
  onBack: () -> Unit,
  onSave: () -> Unit,
  enableSave: Boolean = true,
  onExit: () -> Unit,
  onHelp: () -> Unit,
  content: @Composable () -> Unit,
) {

        Column(
          modifier = Modifier
            .fillMaxSize()
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
fun SettingsLayoutPreview() {
  MeAppTheme {
    SettingsLayout(
      title = CustomizeSettingsStrings.DashboardMetrics.Title,
      subtitle = CustomizeSettingsStrings.DashboardMetrics.Subtitle,
      onBack = {},
      onSave = {},
      onExit = {},
      onHelp = {},
      content = {}
    )
  }
}
