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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.dmdbrands.gurus.weight.features.ScaleCustomization.components.CustomizationLayout
import com.dmdbrands.gurus.weight.features.ScaleCustomization.components.CustomizationSettingsItem
import com.dmdbrands.gurus.weight.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Screens.ScaleMetricsSettingScreen
import com.dmdbrands.gurus.weight.features.ScaleModeSettings.screens.ScaleModeSettingsScreen
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.strings.ScaleFormStrings
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
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
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
  discoveredScale: com.dmdbrands.gurus.weight.domain.model.storage.Device? = null,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { CustomizeSettings.entries.size })

  // Initialize scale metrics with discovered scale's display metrics if available
  var scaleMetrics by remember { mutableStateOf(discoveredScale?.preferences?.displayMetrics ?: state.scaleMetrics) }
  var isAllBodyMetrics by remember { mutableStateOf(state.isAllBodyMetrics) }
  var isHeartRateOn by remember { mutableStateOf(state.isHeartRateOn) }
  var scaleUsername by remember { mutableStateOf(state.usernameForm.username.value) }
  var visitedSteps: Set<CustomizeSettings> by remember { mutableStateOf(emptySet()) }
  val hasSavedSettings = remember { mutableStateOf(false) }
  val customizeSettings = remember(visitedSteps) {
    CustomizeSettingsList.map { it.copy(isVisited = visitedSteps.contains(it.step)) }
  }

  var dashboardMetricKeys: List<DashboardKey.Metric> by remember(state.dashboardKeys) {
    mutableStateOf(state.dashboardKeys.filterIsInstance<DashboardKey.Metric>())
  }
  var dashboardMilestoneKeys: List<DashboardKey.Milestone> by remember(state.dashboardKeys) {
    mutableStateOf(state.dashboardKeys.filterIsInstance<DashboardKey.Milestone>())
  }
  var combinedDashboardKeys: List<DashboardKey>? by remember(state.dashboardKeys) { mutableStateOf(null) }

  // Local state for username form
  var localUsername by remember { mutableStateOf(state.usernameForm.username.value) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  // Create a local form control for username
  val localUsernameFormControl = remember {
    FormControl.create(
      initialValue = state.usernameForm.username.value,
      validators = listOf(
        FormValidations.required(),
      ),
    )
  }

  // Use discovered scale preferences if available, otherwise fall back to default
  val initialPreference =  ScaleMetricsHelper.getDefaultPreference(state.usernameForm.username.value)
  var updatedPreference by remember { mutableStateOf(initialPreference) }

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
              when (pagerState.currentPage) {
                CustomizeSettings.SCALE_USERNAME.ordinal -> {
                  // Reset both local state and form control to last saved state
                  localUsername = state.usernameForm.username.value
                  localUsernameFormControl.setValue(state.usernameForm.username.value)
                  focusManager.clearFocus()
                  keyboardController?.hide()
                }

                CustomizeSettings.SCALE_MODE.ordinal -> {
                  // Reset scale mode to last saved state
                  isAllBodyMetrics = state.isAllBodyMetrics
                  isHeartRateOn = state.isHeartRateOn
                }

                CustomizeSettings.SCALE_METRICS.ordinal -> {
                  // Reset scale metrics to last saved state
                  scaleMetrics = state.scaleMetrics
                }
              }
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
            enabled = !(pagerState.currentPage == CustomizeSettings.SCALE_USERNAME.ordinal && !localUsernameFormControl.isValueValid()),
            onClick = {
              if (visitedSteps.isNotEmpty() && hasSavedSettings.value) {
                onIntent(BtWifiScaleSetupIntent.SetHasSavedSettings(true))
                onIntent(
                  BtWifiScaleSetupIntent.UpdateSettings(
                    dashboardKeys = combinedDashboardKeys,
                    preferences = updatedPreference.copy(
                      id = state.scaleId
                    ),
                  ),
                )
              } else {
                onIntent(
                  BtWifiScaleSetupIntent.Next,
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
              hasSavedSettings.value = true
              scope.launch {

                when (pagerState.currentPage) {
                  CustomizeSettings.SCALE_METRICS.ordinal -> {
                    onIntent(BtWifiScaleSetupIntent.SetScaleMetrics(scaleMetrics = scaleMetrics))
                  }

                  CustomizeSettings.DASHBOARD_METRICS.ordinal -> {
                    combinedDashboardKeys = (dashboardMetricKeys + dashboardMilestoneKeys)
                    combinedDashboardKeys?.let {
                      onIntent(BtWifiScaleSetupIntent.SetDashboardKeys(combinedDashboardKeys!!))
                    }
                  }

                  CustomizeSettings.SCALE_MODE.ordinal -> {
                    updatedPreference = updatedPreference
                      .copy(shouldMeasureImpedance = isAllBodyMetrics, shouldMeasurePulse = isHeartRateOn)
                    onIntent(
                      BtWifiScaleSetupIntent.SetScaleModePreference(
                        isAllBodyMetrics = isAllBodyMetrics,
                        isHeartRateOn = isHeartRateOn,
                      ),
                    )
                  }

                  CustomizeSettings.SCALE_USERNAME.ordinal -> {
                    // Save username to reducer state
                    onIntent(BtWifiScaleSetupIntent.UpdateUsernameForm(localUsernameFormControl.value))
                    focusManager.clearFocus()
                    keyboardController?.hide()
                  }

                  else -> {}
                }
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
          val keys = (combinedDashboardKeys ?: (dashboardMetricKeys + dashboardMilestoneKeys)).distinct()
          DashboardMetrics(
            metricData = emptyList(),
            visibleKeys = keys,
            inEditMode = true,
            isFromSetup = true,
            onMetricsChanged = {
              dashboardMetricKeys = it.filterIsInstance<DashboardKey.Metric>()
            },
          )
          HorizontalDivider(
            color = MeTheme.colorScheme.utility,
            modifier = Modifier.padding(horizontal = spacing.lg),
          )
          DashboardMilestone(
            progress = state.goalProgress,
            inEditMode = true,
            visibleKeys = keys,
            isFromSetup = true,
            onMilestonesChanged = {
              dashboardMilestoneKeys = it.filterIsInstance<DashboardKey.Milestone>()
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
            isAllBodyMetrics = isAllBodyMetrics,
            isHeartRateOn = isHeartRateOn,
            onModeSelected = { newAllBodyMetrics ->
              // Only update local state, don't update reducer state until save
              isAllBodyMetrics = newAllBodyMetrics
            },
            onHeartRateToggle = { newHeartRateState ->
              // Only update local state, don't update reducer state until save
              isHeartRateOn = newHeartRateState
            },
            onBioimpedanceClick = {
              // Handle bioimpedance modal - can be implemented if needed
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
              // Only update local state, don't update reducer state until save
              updatedPreference = updatedPreference.copy(displayMetrics = metrics)
              scaleMetrics = metrics
            },
          )
        }
      }

      CustomizeSettings.SCALE_USERNAME -> {
        visitedSteps = visitedSteps + (CustomizeSettings.SCALE_USERNAME)
        CustomizationLayout {
          SetupForm(
            formControl = localUsernameFormControl,
            title = ScaleFormStrings.UserNameTitle,
            subtitle = ScaleFormStrings.UserNameSubtitle,
            label = ScaleFormStrings.UserNameLabel,
            inputType = AppInputType.TEXT,
            supportingImage = AppIcons.Setup.UserNameScale,
            enableScroll = false,
            userList = userList,
            onImeAction = {
              focusManager.clearFocus()
              keyboardController?.hide()
              scope.launch {
                updatedPreference = updatedPreference
                  .copy(shouldMeasureImpedance = isAllBodyMetrics, shouldMeasurePulse = isHeartRateOn)
                onIntent(
                  BtWifiScaleSetupIntent.SetScaleModePreference(
                    isAllBodyMetrics = isAllBodyMetrics,
                    isHeartRateOn = isHeartRateOn,
                  ),
                )
                pagerState.scrollToPage(0)
              }
            },
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
        discoveredScale = null,
      ) {}
    }
  }
}
