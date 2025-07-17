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
import com.greatergoods.meapp.features.ScaleCustomization.components.CustomizationLayout
import com.greatergoods.meapp.features.ScaleCustomization.components.CustomizationSettingsItem
import com.greatergoods.meapp.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.greatergoods.meapp.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.greatergoods.meapp.features.ScaleMetricsSetting.Screens.ScaleMetricsSettingScreen
import com.greatergoods.meapp.features.ScaleModeSettings.screens.ScaleModeSettingsScreen
import com.greatergoods.meapp.features.ScaleSetup.components.SetupForm
import com.greatergoods.meapp.features.ScaleSetup.components.strings.ScaleFormStrings
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.ScaleSetup.enums.CustomizeSettings
import com.greatergoods.meapp.features.ScaleSetup.model.CustomizeSettingsCard
import com.greatergoods.meapp.features.ScaleSetup.model.CustomizeSettingsList
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
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
import com.greatergoods.meapp.features.common.model.DashboardKey
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
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { CustomizeSettings.entries.size.toInt() })
  var scaleMetrics by remember { mutableStateOf(ScaleMetricsHelper.getAllMetrics()) }

  var visitedSteps: Set<CustomizeSettings> by remember { mutableStateOf(emptySet()) }

  val customizeSettings = remember(visitedSteps) {
    CustomizeSettingsList.map { it.copy(isVisited = visitedSteps.contains(it.step)) }
  }

  var dashboardKeys: List<DashboardKey>? by remember { mutableStateOf(null) }
  val defaultPreference = ScaleMetricsHelper.getDefaultPreference(state.usernameForm.username.value)
  var updatedPreference by remember { mutableStateOf(defaultPreference) }
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
        if (pagerState.currentPage == CustomizeSettings.NONE.ordinal) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            onClick = {
              if (visitedSteps.isEmpty()) {
                onIntent(
                  BtWifiScaleSetupIntent.UpdateSettings(
                    dashboardKeys = dashboardKeys,
                    preferences = updatedPreference.copy(
                      displayName = state.usernameForm.username.value,
                    ),
                  ),
                )
                onIntent(
                  BtWifiScaleSetupIntent.Next,
                )
              } else {
                onIntent(
                  BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.STEP_ON),
                )
              }
            },
          )
        } else {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = ScaleSetupStrings.saveButton,
            size = ButtonSize.Small,
            onClick = {
              scope.launch {
                pagerState.animateScrollToPage(0)
              }
            },
          )
        }
      },
  ) { item ->
    when (item) {
      CustomizeSettings.NONE -> {
        InitializeCustomizeScaleSettings(
          customizeSettings = customizeSettings,
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
        visitedSteps = visitedSteps + (CustomizeSettings.DASHBOARD_METRICS)
        CustomizationLayout(
          title = CustomizeSettingsStrings.DashboardMetrics.Title,
          subtitle = CustomizeSettingsStrings.DashboardMetrics.Subtitle,
        ) {
          DashboardMetrics(
            metricData = emptyList(),
            visibleKeys = state.dashboardKeys,
            inEditMode = false,
            onMetricsChanged = {
              dashboardKeys = it
            },
          )
        }
      }

      CustomizeSettings.SCALE_METRICS -> {
        visitedSteps = visitedSteps + (CustomizeSettings.SCALE_METRICS)
        CustomizationLayout(
          title = CustomizeSettingsStrings.ScaleDisplayMetrics.Title,
          subtitle = CustomizeSettingsStrings.ScaleDisplayMetrics.Subtitle,
        ) {
          ScaleMetricsSettingScreen(
            currentMetrics = scaleMetrics,
            onMetricsChanged = { metrics ->
              updatedPreference = updatedPreference.copy(displayMetrics = metrics)
              scaleMetrics = metrics
            },
          )
        }
      }

      CustomizeSettings.SCALE_MODE -> {
        visitedSteps = visitedSteps + (CustomizeSettings.SCALE_MODE)
        CustomizationLayout(
          title = CustomizeSettingsStrings.ScaleMode.Title,
        ) {
          ScaleModeSettingsScreen(
            isAllBodyMetrics = true,
            isHeartRateOn = true,
            onModeSelected = {
              updatedPreference = updatedPreference.copy(shouldMeasureImpedance = it)
            },
            onHeartRateToggle = {
              updatedPreference = updatedPreference.copy(shouldMeasurePulse = it)
            },
            onBioimpedanceClick = {
            },
          )
        }
      }

      CustomizeSettings.SCALE_USERNAME -> {
        visitedSteps = visitedSteps + (CustomizeSettings.SCALE_USERNAME)
        SetupForm(
          formControl = state.usernameForm.username,
          title = ScaleFormStrings.UserNameTitle,
          subtitle = ScaleFormStrings.UserNameSubtitle,
          label = ScaleFormStrings.UserNameLabel,
          inputType = AppInputType.TEXT,
          supportingImage = AppIcons.Setup.UserNameScale, // Placeholder
        )
      }
    }
  }
}

@Composable
fun InitializeCustomizeScaleSettings(
  modifier: Modifier = Modifier,
  customizeSettings: List<CustomizeSettingsCard> = CustomizeSettingsList,
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
      customizeSettings.forEach {
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
      ) {}
    }
  }
}
