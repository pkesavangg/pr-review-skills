package com.greatergoods.meapp.features.ScaleCustomization.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleCustomization.components.CustomizationSettingsItem
import com.greatergoods.meapp.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.greatergoods.meapp.features.ScaleMetricsSetting.Screens.ScaleMetricsSettingScreen
import com.greatergoods.meapp.features.ScaleModeSettings.screens.ScaleModeSettingsScreen
import com.greatergoods.meapp.features.ScaleSetup.components.SetupForm
import com.greatergoods.meapp.features.ScaleSetup.components.strings.ScaleFormStrings
import com.greatergoods.meapp.features.ScaleSetup.enums.CustomizeSettings
import com.greatergoods.meapp.features.ScaleSetup.model.CustomizeSettingsList
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.dashboard.components.DashboardMetrics
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun CustomizeScaleSettings(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  state: BtWifiScaleSetupState
) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { CustomizeSettings.entries.size.toInt() })
  var scaleMetrics by remember { mutableStateOf(ScaleMetricsHelper.getAllMetrics()) }
  HorizontalPagerWithBottomNavigation(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    steps = CustomizeSettings.entries,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    pagerState = pagerState,
    shouldCenterMiddleContent = true,
    leadingContent =
      {
        AppButton(
          type = ButtonType.TextPrimary,
          label = ScaleSetupStrings.backButton,
          size = ButtonSize.Small,
          onClick = {
            scope.launch {
              pagerState.animateScrollToPage(0)
            }
          },
        )
      },
    trailingContent =
      {
        AppButton(
          type = ButtonType.PrimaryFilled,
          label = "Save",
          size = ButtonSize.Small,
          onClick = {

          },
        )
      },
  ) { item ->
    when (item) {
      CustomizeSettings.NONE -> {
        InitializeCustomizeScaleSettings(
          modifier = modifier,
          title = title,
          subtitle = subtitle,
          onSelectSettings = {
            scope.launch {
              pagerState.scrollToPage(it.ordinal)
            }
          },
        )
      }

      CustomizeSettings.DASHBOARD_METRICS -> {
        DashboardMetrics(
          metricData = emptyList(),
          visibleKeys = state.dashboardKeys,
          inEditMode = true,
        )
      }

      CustomizeSettings.SCALE_METRICS -> {
        ScaleMetricsSettingScreen(
          currentMetrics = scaleMetrics,
          onMetricsChanged = { metrics ->
            scaleMetrics = metrics
          },
        )
      }

      CustomizeSettings.SCALE_MODE -> {
        ScaleModeSettingsScreen(
          isAllBodyMetrics = true,
          isHeartRateOn = true,
          onModeSelected = {},
          onHeartRateToggle = {},
          onBioimpedanceClick = {},
        )
      }

      CustomizeSettings.SCALE_USERNAME -> {
        SetupForm(
          formControl = state.usernameForm.username,
          title = ScaleFormStrings.UserNameTitle,
          subtitle = ScaleFormStrings.UserNameSubtitle,
          label = ScaleFormStrings.UserNameLabel,
          inputType = AppInputType.TEXT,
          hasToggle = false,
          supportingImage = AppIcons.Setup.UserNameScale, // Placeholder
          supportingButtonLabel = "Restore Account",
          onSupportingButtonClick = {},
          supportText = "Last active June 10, 2019",
        )
      }
    }
  }
}

@Composable
fun InitializeCustomizeScaleSettings(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  onSelectSettings: (selectedSettings: CustomizeSettings) -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxSize(),
  ) {
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
        state = BtWifiScaleSetupState(),
      )
    }
  }
}
