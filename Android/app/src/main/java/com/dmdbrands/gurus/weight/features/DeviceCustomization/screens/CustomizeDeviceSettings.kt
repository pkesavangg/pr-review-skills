package com.dmdbrands.gurus.weight.features.DeviceCustomization.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.features.DeviceCustomization.components.CustomizationLayout
import com.dmdbrands.gurus.weight.features.DeviceCustomization.components.CustomizationSettingsItem
import com.dmdbrands.gurus.weight.features.DeviceCustomization.strings.CustomizeSettingsStrings
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper.DeviceMetricsHelper
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Screens.DeviceMetricsSettingScreen
import com.dmdbrands.gurus.weight.features.DeviceModeSettings.screens.DeviceModeSettingsScreen
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.strings.DeviceFormStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.DeviceSetup.model.CustomizeSettingsCard
import com.dmdbrands.gurus.weight.features.DeviceSetup.model.CustomizeSettingsList
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Local, transient UI state for the customize-settings flow. State lives here (created once via
 * [rememberCustomizeSettingsStates]) and is threaded down to the pager sections so the individual
 * selections persist across page navigation within the pager.
 */
private class CustomizeSettingsStates(
  val deviceMetrics: MutableState<List<String>>,
  val isAllBodyMetrics: MutableState<Boolean>,
  val isHeartRateOn: MutableState<Boolean>,
  val visitedSteps: MutableState<Set<CustomizeSettings>>,
  val dashboardMetricKeys: MutableState<List<DashboardKey.Metric>>,
  val dashboardMilestoneKeys: MutableState<List<DashboardKey.Milestone>>,
  val localUsername: MutableState<String>,
  val hasSavedSettings: MutableState<Boolean>,
  val updatedPreference: MutableState<Preferences>,
)

@Composable
fun CustomizeDeviceSettings(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  state: BtWifiScaleSetupState,
  userList: List<GGBTUser> = emptyList(),
  discoveredScale: Device? = null,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { CustomizeSettings.entries.size })
  val scrollState = rememberScrollState()
  val states = rememberCustomizeSettingsStates(state, discoveredScale)

  val customizeSettings = remember(states.visitedSteps.value) {
    CustomizeSettingsList.map { it.copy(isVisited = states.visitedSteps.value.contains(it.step)) }
  }

  val localUsernameFormControl = rememberLocalUsernameFormControl(state.usernameForm.username.value)

  // Sync reducer state to local state when navigating back from error (reducer state is true but
  // local might be false) — preserves hasSavedSettings across the UPDATE_SETTINGS "Try again" path.
  LaunchedEffect(state.hasSavedSettings) {
    if (state.hasSavedSettings && !states.hasSavedSettings.value) {
      states.hasSavedSettings.value = true
    }
  }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  val hasUnsavedChanges = rememberCustomizeHasUnsavedChanges(
    currentPage = pagerState.currentPage,
    state = state,
    localUsername = localUsernameFormControl.value,
    states = states,
  )

  LaunchedEffect(state.scrollToRootPage) {
    if (state.scrollToRootPage) {
      // Clear before the suspending scroll so the flag is reset even if recomposition
      // re-keys this effect mid-suspend.
      onIntent(BtWifiScaleSetupIntent.ClearScrollToRootPage)
      pagerState.scrollToPage(0)
    }
  }

  // Covers the case where the user navigates away mid-save-delay: ensures the flag
  // doesn't survive in ViewModel state and re-trigger an unwanted scroll on re-entry.
  DisposableEffect(Unit) {
    onDispose { onIntent(BtWifiScaleSetupIntent.ClearScrollToRootPage) }
  }

  CustomizeSettingsPager(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    state = state,
    userList = userList,
    scope = scope,
    pagerState = pagerState,
    scrollState = scrollState,
    states = states,
    customizeSettings = customizeSettings,
    localUsernameFormControl = localUsernameFormControl,
    hasUnsavedChanges = hasUnsavedChanges,
    focusManager = focusManager,
    keyboardController = keyboardController,
    onIntent = onIntent,
  )
}

@Composable
private fun rememberCustomizeSettingsStates(
  state: BtWifiScaleSetupState,
  discoveredScale: Device?,
): CustomizeSettingsStates {
  // Initialize scale metrics with discovered scale's display metrics if available.
  val deviceMetrics = remember {
    mutableStateOf(discoveredScale?.preferences?.displayMetrics ?: state.deviceMetrics)
  }
  val isAllBodyMetrics = remember { mutableStateOf(state.isAllBodyMetrics) }
  val isHeartRateOn = remember { mutableStateOf(state.isHeartRateOn) }
  // Initialize from state to preserve when returning from UPDATE_SETTINGS "Try again".
  val visitedSteps = remember(state.visitedCustomizeSteps) {
    mutableStateOf(state.visitedCustomizeSteps)
  }
  val dashboardMetricKeys = remember(state.dashboardKeys) {
    mutableStateOf(state.dashboardKeys.filterIsInstance<DashboardKey.Metric>())
  }
  val dashboardMilestoneKeys = remember(state.dashboardKeys) {
    mutableStateOf(state.dashboardKeys.filterIsInstance<DashboardKey.Milestone>())
  }
  val localUsername = remember { mutableStateOf(state.usernameForm.username.value) }
  // Preserve hasSavedSettings when navigating back from error.
  val hasSavedSettings = remember { mutableStateOf(state.hasSavedSettings) }
  val updatedPreference = remember {
    mutableStateOf(DeviceMetricsHelper.getDefaultPreference(state.usernameForm.username.value))
  }
  return CustomizeSettingsStates(
    deviceMetrics = deviceMetrics,
    isAllBodyMetrics = isAllBodyMetrics,
    isHeartRateOn = isHeartRateOn,
    visitedSteps = visitedSteps,
    dashboardMetricKeys = dashboardMetricKeys,
    dashboardMilestoneKeys = dashboardMilestoneKeys,
    localUsername = localUsername,
    hasSavedSettings = hasSavedSettings,
    updatedPreference = updatedPreference,
  )
}

@Composable
private fun rememberLocalUsernameFormControl(initialValue: String): FormControl<String> =
  remember {
    FormControl.create(
      initialValue = initialValue,
      validators = listOf(
        FormValidations.required(),
        FormValidations.noWhiteSpace(),
        FormValidations.maxLength(20),
        FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
      ),
    )
  }

@Composable
private fun rememberCustomizeHasUnsavedChanges(
  currentPage: Int,
  state: BtWifiScaleSetupState,
  localUsername: String,
  states: CustomizeSettingsStates,
): Boolean = remember(
  currentPage,
  localUsername,
  states.isAllBodyMetrics.value,
  states.isHeartRateOn.value,
  states.deviceMetrics.value,
  states.dashboardMetricKeys.value,
  states.dashboardMilestoneKeys.value,
  state.usernameForm.username.value,
  state.isAllBodyMetrics,
  state.isHeartRateOn,
  state.deviceMetrics,
  state.dashboardKeys,
) {
  when (currentPage) {
    CustomizeSettings.DEVICE_USERNAME.ordinal ->
      localUsername != state.usernameForm.username.value

    CustomizeSettings.SCALE_MODE.ordinal ->
      states.isAllBodyMetrics.value != state.isAllBodyMetrics ||
        states.isHeartRateOn.value != state.isHeartRateOn

    CustomizeSettings.SCALE_METRICS.ordinal ->
      states.deviceMetrics.value != state.deviceMetrics

    CustomizeSettings.DASHBOARD_METRICS.ordinal -> {
      val currentMetricKeys = state.dashboardKeys.filterIsInstance<DashboardKey.Metric>()
      val currentMilestoneKeys = state.dashboardKeys.filterIsInstance<DashboardKey.Milestone>()
      states.dashboardMetricKeys.value != currentMetricKeys ||
        states.dashboardMilestoneKeys.value != currentMilestoneKeys
    }

    else -> false
  }
}

@Composable
private fun CustomizeSettingsPager(
  modifier: Modifier,
  title: String,
  subtitle: String,
  state: BtWifiScaleSetupState,
  userList: List<GGBTUser>,
  scope: CoroutineScope,
  pagerState: PagerState,
  scrollState: ScrollState,
  states: CustomizeSettingsStates,
  customizeSettings: List<CustomizeSettingsCard>,
  localUsernameFormControl: FormControl<String>,
  hasUnsavedChanges: Boolean,
  focusManager: FocusManager,
  keyboardController: SoftwareKeyboardController?,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  HorizontalPagerWithBottomNavigation(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(state = scrollState)
      .padding(vertical = spacing.md)
      .testTag(TestTags.DeviceCustomization.ScreenRoot),
    steps = CustomizeSettings.entries,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    pagerState = pagerState,
    shouldCenterMiddleContent = true,
    leadingContent = {
      CustomizeBackButton(currentPage = pagerState.currentPage) {
        scope.launch {
          resetCustomizePageToSaved(
            currentPage = pagerState.currentPage,
            state = state,
            states = states,
            localUsernameFormControl = localUsernameFormControl,
            focusManager = focusManager,
            keyboardController = keyboardController,
          )
          pagerState.scrollToPage(0)
        }
      }
    },
    trailingContent = {
      CustomizeTrailingButton(
        pagerState = pagerState,
        state = state,
        states = states,
        hasUnsavedChanges = hasUnsavedChanges,
        localUsernameFormControl = localUsernameFormControl,
        focusManager = focusManager,
        keyboardController = keyboardController,
        onIntent = onIntent,
      )
    },
  ) { item ->
    CustomizeSettingsPage(
      item = item,
      modifier = modifier,
      title = title,
      subtitle = subtitle,
      state = state,
      userList = userList,
      scope = scope,
      pagerState = pagerState,
      scrollState = scrollState,
      states = states,
      customizeSettings = customizeSettings,
      localUsernameFormControl = localUsernameFormControl,
      focusManager = focusManager,
      keyboardController = keyboardController,
      onIntent = onIntent,
    )
  }
}

@Composable
private fun CustomizeBackButton(
  currentPage: Int,
  onBack: () -> Unit,
) {
  AppButton(
    type = ButtonType.TextPrimary,
    modifier = Modifier.testTag(TestTags.DeviceCustomization.BackButton),
    label = DeviceSetupStrings.backButton,
    size = ButtonSize.Small,
    // Disable back button when on main settings screen (NONE)
    enabled = currentPage != CustomizeSettings.NONE.ordinal,
    onClick = onBack,
  )
}

@Composable
private fun CustomizeTrailingButton(
  pagerState: PagerState,
  state: BtWifiScaleSetupState,
  states: CustomizeSettingsStates,
  hasUnsavedChanges: Boolean,
  localUsernameFormControl: FormControl<String>,
  focusManager: FocusManager,
  keyboardController: SoftwareKeyboardController?,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val currentPage = pagerState.currentPage
  if (currentPage == CustomizeSettings.NONE.ordinal) {
    CustomizePrimaryButton(
      label = DeviceSetupStrings.nextButton,
      enabled = !(
        currentPage == CustomizeSettings.DEVICE_USERNAME.ordinal &&
          !localUsernameFormControl.isValueValid()
        ),
      testTag = TestTags.DeviceCustomization.NextButton,
      onClick = { handleCustomizeNextClick(state, states, onIntent) },
    )
  } else {
    CustomizePrimaryButton(
      label = DeviceSetupStrings.saveButton,
      enabled = !state.isSaving && hasUnsavedChanges &&
        (currentPage != CustomizeSettings.DEVICE_USERNAME.ordinal || localUsernameFormControl.isValueValid()),
      testTag = TestTags.DeviceCustomization.SaveButton,
      onClick = {
        handleCustomizeSaveClick(currentPage, state, states, localUsernameFormControl.value, onIntent) {
          focusManager.clearFocus()
          keyboardController?.hide()
        }
      },
    )
  }
}

@Composable
private fun CustomizePrimaryButton(
  label: String,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  AppButton(
    type = ButtonType.PrimaryFilled,
    modifier = Modifier.testTag(testTag),
    label = label,
    size = ButtonSize.Small,
    enabled = enabled,
    onClick = onClick,
  )
}

private fun handleCustomizeNextClick(
  state: BtWifiScaleSetupState,
  states: CustomizeSettingsStates,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  if (states.visitedSteps.value.isNotEmpty() && states.hasSavedSettings.value) {
    val combinedDashboardKeys =
      (states.dashboardMetricKeys.value + states.dashboardMilestoneKeys.value).distinct()
    onIntent(BtWifiScaleSetupIntent.SetHasSavedSettings(true))
    onIntent(BtWifiScaleSetupIntent.Next)
    onIntent(
      BtWifiScaleSetupIntent.UpdateSettings(
        dashboardKeys = combinedDashboardKeys,
        // Source the customizable preferences from the persisted reducer state rather than
        // the local `updatedPreference`, which is re-seeded to all-metrics defaults whenever
        // this composable is recomposed-from-scratch (e.g. across the saving-loader step).
        // Relying on local state silently dropped the user's metric/scale-mode selections,
        // so the scale displayed every metric regardless of what was toggled off (MOB-398).
        preferences = states.updatedPreference.value.copy(
          id = state.scaleId,
          displayMetrics = state.deviceMetrics,
          shouldMeasureImpedance = state.isAllBodyMetrics,
          shouldMeasurePulse = state.isHeartRateOn,
        ),
      ),
    )
  } else {
    onIntent(BtWifiScaleSetupIntent.Next)
  }
}

private fun handleCustomizeSaveClick(
  currentPage: Int,
  state: BtWifiScaleSetupState,
  states: CustomizeSettingsStates,
  usernameValue: String,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
  clearFocusAndHideKeyboard: () -> Unit,
) {
  states.hasSavedSettings.value = true
  // Update reducer state immediately so it's preserved if navigation back occurs
  onIntent(BtWifiScaleSetupIntent.SetHasSavedSettings(true))

  when (currentPage) {
    CustomizeSettings.SCALE_METRICS.ordinal ->
      onIntent(BtWifiScaleSetupIntent.SetScaleMetrics(deviceMetrics = states.deviceMetrics.value))

    CustomizeSettings.DASHBOARD_METRICS.ordinal ->
      onIntent(
        BtWifiScaleSetupIntent.SetDashboardKeys(
          states.dashboardMetricKeys.value + states.dashboardMilestoneKeys.value,
        ),
      )

    CustomizeSettings.SCALE_MODE.ordinal -> commitScaleModePreference(states, onIntent)

    CustomizeSettings.DEVICE_USERNAME.ordinal -> {
      // Save username to reducer state
      onIntent(BtWifiScaleSetupIntent.UpdateUsernameForm(usernameValue))
      clearFocusAndHideKeyboard()
    }

    else -> {}
  }
  onIntent(BtWifiScaleSetupIntent.ShowSavingLoader)
}

private fun commitScaleModePreference(
  states: CustomizeSettingsStates,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  states.updatedPreference.value = states.updatedPreference.value.copy(
    shouldMeasureImpedance = states.isAllBodyMetrics.value,
    shouldMeasurePulse = states.isHeartRateOn.value,
  )
  onIntent(
    BtWifiScaleSetupIntent.SetScaleModePreference(
      isAllBodyMetrics = states.isAllBodyMetrics.value,
      isHeartRateOn = states.isHeartRateOn.value,
    ),
  )
}

private fun resetCustomizePageToSaved(
  currentPage: Int,
  state: BtWifiScaleSetupState,
  states: CustomizeSettingsStates,
  localUsernameFormControl: FormControl<String>,
  focusManager: FocusManager,
  keyboardController: SoftwareKeyboardController?,
) {
  when (currentPage) {
    CustomizeSettings.DEVICE_USERNAME.ordinal -> {
      // Reset both local state and form control to last saved state
      states.localUsername.value = state.usernameForm.username.value
      localUsernameFormControl.setValue(state.usernameForm.username.value)
      focusManager.clearFocus()
      keyboardController?.hide()
    }

    CustomizeSettings.SCALE_MODE.ordinal -> {
      states.isAllBodyMetrics.value = state.isAllBodyMetrics
      states.isHeartRateOn.value = state.isHeartRateOn
    }

    CustomizeSettings.SCALE_METRICS.ordinal ->
      states.deviceMetrics.value = state.deviceMetrics

    CustomizeSettings.DASHBOARD_METRICS.ordinal -> {
      states.dashboardMetricKeys.value = state.dashboardKeys.filterIsInstance<DashboardKey.Metric>()
      states.dashboardMilestoneKeys.value = state.dashboardKeys.filterIsInstance<DashboardKey.Milestone>()
    }
  }
}

private fun markStepVisited(
  states: CustomizeSettingsStates,
  step: CustomizeSettings,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  states.visitedSteps.value = states.visitedSteps.value + step
  onIntent(BtWifiScaleSetupIntent.SetVisitedCustomizeSteps(states.visitedSteps.value))
}

@Composable
private fun CustomizeSettingsPage(
  item: CustomizeSettings,
  modifier: Modifier,
  title: String,
  subtitle: String,
  state: BtWifiScaleSetupState,
  userList: List<GGBTUser>,
  scope: CoroutineScope,
  pagerState: PagerState,
  scrollState: ScrollState,
  states: CustomizeSettingsStates,
  customizeSettings: List<CustomizeSettingsCard>,
  localUsernameFormControl: FormControl<String>,
  focusManager: FocusManager,
  keyboardController: SoftwareKeyboardController?,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  when (item) {
    CustomizeSettings.NONE ->
      InitializeCustomizeScaleSettings(
        customizeSettings = customizeSettings,
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        onSelectSettings = { scope.launch { pagerState.scrollToPage(it.value) } },
      )

    CustomizeSettings.DASHBOARD_METRICS -> {
      markStepVisited(states, CustomizeSettings.DASHBOARD_METRICS, onIntent)
      CustomizeDashboardMetricsPage(
        state = state,
        dashboardMetricKeys = states.dashboardMetricKeys.value,
        dashboardMilestoneKeys = states.dashboardMilestoneKeys.value,
        onMetricsChanged = { states.dashboardMetricKeys.value = it },
        onMilestonesChanged = { states.dashboardMilestoneKeys.value = it },
      )
    }

    CustomizeSettings.SCALE_METRICS -> {
      markStepVisited(states, CustomizeSettings.SCALE_METRICS, onIntent)
      CustomizeScaleMetricsPage(
        deviceMetrics = states.deviceMetrics.value,
        scrollState = scrollState,
        onMetricsChanged = { metrics ->
          // Only update local state, don't update reducer state until save
          states.updatedPreference.value = states.updatedPreference.value.copy(displayMetrics = metrics)
          states.deviceMetrics.value = metrics
        },
      )
    }

    CustomizeSettings.SCALE_MODE -> {
      markStepVisited(states, CustomizeSettings.SCALE_MODE, onIntent)
      CustomizeScaleModePage(
        isAllBodyMetrics = states.isAllBodyMetrics.value,
        isHeartRateOn = states.isHeartRateOn.value,
        onModeSelected = { states.isAllBodyMetrics.value = it },
        onHeartRateToggle = { states.isHeartRateOn.value = it },
        onBioimpedanceClick = { onIntent(BtWifiScaleSetupIntent.OpenBiaModal) },
      )
    }

    CustomizeSettings.DEVICE_USERNAME -> {
      markStepVisited(states, CustomizeSettings.DEVICE_USERNAME, onIntent)
      CustomizeDeviceUsernamePage(
        formControl = localUsernameFormControl,
        userList = userList,
        onImeAction = {
          focusManager.clearFocus()
          keyboardController?.hide()
          scope.launch {
            commitScaleModePreference(states, onIntent)
            pagerState.scrollToPage(0)
          }
        },
      )
    }
  }
}

@Composable
private fun CustomizeDashboardMetricsPage(
  state: BtWifiScaleSetupState,
  dashboardMetricKeys: List<DashboardKey.Metric>,
  dashboardMilestoneKeys: List<DashboardKey.Milestone>,
  onMetricsChanged: (List<DashboardKey.Metric>) -> Unit,
  onMilestonesChanged: (List<DashboardKey.Milestone>) -> Unit,
) {
  CustomizationLayout(
    title = CustomizeSettingsStrings.DashboardMetrics.Title,
    subtitle = CustomizeSettingsStrings.DashboardMetrics.Subtitle,
  ) {
    val keys = (dashboardMetricKeys + dashboardMilestoneKeys).distinct()
    DashboardMetrics(
      metricData = emptyList(),
      visibleKeys = keys,
      inEditMode = true,
      isFromSetup = true,
      onMetricsChanged = { onMetricsChanged(it.filterIsInstance<DashboardKey.Metric>()) },
    )
    HorizontalDivider(
      color = MeTheme.colorScheme.utility,
      modifier = Modifier.padding(horizontal = spacing.lg),
    )
    DashboardMilestone(
      progress = state.goalProgress,
      latestWeight = state.latestWeight,
      inEditMode = true,
      visibleKeys = keys,
      isFromSetup = true,
      onMilestonesChanged = { onMilestonesChanged(it.filterIsInstance<DashboardKey.Milestone>()) },
      onNavigateToGoal = {},
    )
  }
}

@Composable
private fun CustomizeScaleMetricsPage(
  deviceMetrics: List<String>,
  scrollState: ScrollState,
  onMetricsChanged: (List<String>) -> Unit,
) {
  CustomizationLayout(
    title = CustomizeSettingsStrings.DeviceDisplayMetrics.Title,
    subtitle = CustomizeSettingsStrings.DeviceDisplayMetrics.Subtitle,
  ) {
    DeviceMetricsSettingScreen(
      currentMetrics = deviceMetrics,
      scrollState = scrollState,
      onMetricsChanged = onMetricsChanged,
    )
  }
}

@Composable
private fun CustomizeScaleModePage(
  isAllBodyMetrics: Boolean,
  isHeartRateOn: Boolean,
  onModeSelected: (Boolean) -> Unit,
  onHeartRateToggle: (Boolean) -> Unit,
  onBioimpedanceClick: () -> Unit,
) {
  CustomizationLayout(
    title = CustomizeSettingsStrings.DeviceMode.Title,
  ) {
    DeviceModeSettingsScreen(
      isAllBodyMetrics = isAllBodyMetrics,
      isHeartRateOn = isHeartRateOn,
      onModeSelected = onModeSelected,
      onHeartRateToggle = onHeartRateToggle,
      onBioimpedanceClick = onBioimpedanceClick,
    )
  }
}

@Composable
private fun CustomizeDeviceUsernamePage(
  formControl: FormControl<String>,
  userList: List<GGBTUser>,
  onImeAction: () -> Unit,
) {
  CustomizationLayout {
    SetupForm(
      formControl = formControl,
      title = DeviceFormStrings.UserNameTitle,
      subtitle = DeviceFormStrings.UserNameSubtitle,
      label = DeviceFormStrings.UserNameLabel,
      inputType = AppInputType.TEXT,
      supportingImage = AppIcons.Setup.UserNameScale,
      enableScroll = false,
      userList = userList,
      onImeAction = onImeAction,
    )
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
      .fillMaxSize()
      .padding(horizontal = spacing.sm),
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
      CustomizeDeviceSettings(
        title = "Customize your Settings",
        subtitle = "You can update settings at any time.",
        state = BtWifiScaleSetupState(),
        discoveredScale = null,
      ) {}
    }
  }
}
