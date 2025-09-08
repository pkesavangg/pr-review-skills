package com.dmdbrands.gurus.weight.features.ScaleCustomization.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.ScaleCustomization.components.CustomizationLayout
import com.dmdbrands.gurus.weight.features.ScaleCustomization.components.CustomizationSettingsItem
import com.dmdbrands.gurus.weight.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Screens.ScaleMetricsSettingScreen
import com.dmdbrands.gurus.weight.features.ScaleModeSettings.screens.ScaleModeSettingsScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.strings.ScaleFormStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.ScaleSetup.model.CustomizeSettingsCard
import com.dmdbrands.gurus.weight.features.ScaleSetup.model.CustomizeSettingsList
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardMetrics
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardMilestone
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import kotlinx.coroutines.launch

@Composable
fun CustomizeScaleSettings(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  state: BtWifiScaleSetupState,
  userList: List<GGBTUser> = emptyList(),
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { CustomizeSettings.entries.size.toInt() })
  var scaleMetrics by remember { mutableStateOf(ScaleMetricsHelper.getAllMetrics()) }

  var visitedSteps: Set<CustomizeSettings> by remember { mutableStateOf(emptySet()) }

  val customizeSettings = remember(visitedSteps) {
    CustomizeSettingsList.map { it.copy(isVisited = visitedSteps.contains(it.step)) }
  }

  var dashboardMetricKeys: List<DashboardKey>? by remember { mutableStateOf(null) }
  var dashboardMilestoneKeys: List<DashboardKey>? by remember { mutableStateOf(null) }
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
          // Disable back button when on main settings screen (NONE)
          enabled = pagerState.currentPage != CustomizeSettings.NONE.ordinal,
          onClick = {
            scope.launch {
              pagerState.scrollToPage(0)
            }
          },
        )
      },
    trailingContent =
      {
        if (pagerState.currentPage == CustomizeSettings.NONE.ordinal) {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            enabled = !(pagerState.currentPage == CustomizeSettings.SCALE_USERNAME.ordinal && state.usernameForm.username.isValueValid()),
            onClick = {
              if (visitedSteps.isNotEmpty()) {
                val combinedKeys: List<DashboardKey>? = when {
                  dashboardMetricKeys != null || dashboardMilestoneKeys != null -> buildList {
                    dashboardMetricKeys?.let { addAll(it) }
                    dashboardMilestoneKeys?.let { addAll(it) }
                  }

                  else -> null
                }

                onIntent(
                  BtWifiScaleSetupIntent.UpdateSettings(
                    dashboardKeys = combinedKeys,
                    preferences = updatedPreference,
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
                pagerState.scrollToPage(0)
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
              pagerState.scrollToPage(it.value)
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
            inEditMode = true,
            isFromSetup = true,
            onMetricsChanged = {
              dashboardMetricKeys = it
            },
          )
          HorizontalDivider(
            color = MeTheme.colorScheme.utility,
            modifier = Modifier.padding(horizontal = spacing.lg),
          )
          DashboardMilestone(
            progress = Progress(),
            inEditMode = true,
            visibleKeys = state.dashboardKeys,
            onMilestonesChanged = {
              dashboardMilestoneKeys = it
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
              // UPDATING DISPLAY METRICS ALONE
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
            isAllBodyMetrics = state.isAllBodyMetrics,
            isHeartRateOn = state.isHeartRateOn,
            onModeSelected = { newAllBodyMetrics ->
              // Update scale mode preference in state and updatedPreference
              onIntent(BtWifiScaleSetupIntent.SetAllBodyMetrics(newAllBodyMetrics))
              updatedPreference = updatedPreference.copy(shouldMeasureImpedance = newAllBodyMetrics)
            },
            onHeartRateToggle = { newHeartRateState ->
              // Update heart rate preference in state and updatedPreference
              onIntent(BtWifiScaleSetupIntent.SetHeartRateMode(newHeartRateState))
              updatedPreference = updatedPreference.copy(shouldMeasurePulse = newHeartRateState)
            },
            onBioimpedanceClick = {
              // Handle bioimpedance modal - can be implemented if needed
            },
          )
        }
      }

      CustomizeSettings.SCALE_USERNAME -> {
        visitedSteps = visitedSteps + (CustomizeSettings.SCALE_USERNAME)
        CustomizationLayout {
          SetupForm(
            formControl = state.usernameForm.username,
            title = ScaleFormStrings.UserNameTitle,
            subtitle = ScaleFormStrings.UserNameSubtitle,
            label = ScaleFormStrings.UserNameLabel,
            inputType = AppInputType.TEXT,
            supportingImage = AppIcons.Setup.UserNameScale,
            enableScroll = false,
            userList = userList,
          )
        }
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
