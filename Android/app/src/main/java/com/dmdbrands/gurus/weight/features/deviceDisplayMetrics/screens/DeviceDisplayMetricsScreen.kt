package com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Screens.DeviceMetricsSettingScreen
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ScrollAmountMultiplier
import com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.components.DeviceMetricsNotes
import com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.reducer.DeviceDisplayMetricsIntent
import com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.reducer.DeviceDisplayMetricsState
import com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.strings.DeviceDisplayMetricsStrings
import com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.viewmodel.DeviceDisplayMetricsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.mainAxisViewportSize
import sh.calvin.reorderable.rememberScroller

@Composable
fun DeviceDisplayMetricsScreen(scaleId: String) {
  val viewModel: DeviceDisplayMetricsViewModel =
    hiltViewModel<DeviceDisplayMetricsViewModel, DeviceDisplayMetricsViewModel.Factory>(
      creationCallback = { factory -> factory.create(scaleId) },
    )
  val state by viewModel.state.collectAsStateWithLifecycle()

  BackHandler {
    viewModel.handleIntent(DeviceDisplayMetricsIntent.Back)
  }

  DeviceDisplayMetricsScreenContent(state, viewModel::handleIntent)
}

@Composable
fun DeviceDisplayMetricsScreenContent(
  state: DeviceDisplayMetricsState,
  handleIntent: (DeviceDisplayMetricsIntent) -> Unit,
) {
  val lazyListState = rememberLazyListState()
  AppScaffold(
    title = DeviceDisplayMetricsStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, contentDescription = DeviceDisplayMetricsStrings.accCloseLabel) {
        handleIntent(DeviceDisplayMetricsIntent.Back)
      }
    },
    actions = {
      DeviceDisplayMetricsSaveAction(state.hasUpdated, handleIntent)
    },
  ) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(vertical = spacing.md, horizontal = spacing.sm),
      state = lazyListState,
    ) {
      item {
        DeviceDisplayMetricsHeader(state, handleIntent)
      }
      item {
        DeviceDisplayMetricsList(state, lazyListState, handleIntent)
      }
    }
  }
}

@Composable
private fun DeviceDisplayMetricsSaveAction(
  hasUpdated: Boolean,
  handleIntent: (DeviceDisplayMetricsIntent) -> Unit,
) {
  if (hasUpdated) {
    AppText(
      text = DeviceDisplayMetricsStrings.Save,
      textType = TextType.ListTitle1,
      color = colorScheme.primaryAction,
      modifier =
        Modifier
          .padding(end = spacing.md)
          // TalkBack: this clickable text acts as a button, so expose the Button role.
          .semantics { role = Role.Button }
          .clickable { handleIntent(DeviceDisplayMetricsIntent.Save) },
    )
  }
}

@Composable
private fun DeviceDisplayMetricsHeader(
  state: DeviceDisplayMetricsState,
  handleIntent: (DeviceDisplayMetricsIntent) -> Unit,
) {
  // Notes
  state.scale?.let { scale ->
    DeviceMetricsNotes(
      scale = scale,
      onUpdateScaleMode = {
        handleIntent(DeviceDisplayMetricsIntent.UpdateScaleMode)
      },
    )
  }

  // Description
  AppText(
    text = DeviceDisplayMetricsStrings.Description,
    textType = TextType.Body,
    modifier = Modifier.padding(bottom = spacing.md),
  )
}

@Composable
private fun DeviceDisplayMetricsList(
  state: DeviceDisplayMetricsState,
  lazyListState: LazyListState,
  handleIntent: (DeviceDisplayMetricsIntent) -> Unit,
) {
  // Display Metrics Component
  state.scale?.let { scale ->
    DeviceMetricsSettingScreen(
      currentMetrics = scale.preferences?.displayMetrics ?: emptyList(),
      scrollState = lazyListState,
      onMetricsChanged = { enabledMetrics ->
        handleIntent(DeviceDisplayMetricsIntent.UpdateMetrics(enabledMetrics))
      },
      includeHeartRate = scale.preferences?.shouldMeasurePulse == true,
      showAllMetrics = scale.preferences?.shouldMeasureImpedance == true,
    )
  }
}

@PreviewTheme
@Composable
fun DeviceDisplayMetricsScreenPreview() {
  val dummyDevice = Device(
    id = "1",
    device = com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail(
      deviceName = "AccuCheck Verve Smart Scale",
      macAddress = "greatergoods1",
      identifier = "identifier1",
    ),
    connectionStatus = com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus.CONNECTED,
    alreadyPaired = true,
    userNumber = 1,
    isWeighOnlyModeEnabledByOthers = true, // Add this to show the AppNote in preview
    preferences = com.dmdbrands.gurus.weight.domain.model.storage.Preferences(
      displayMetrics = listOf(
        "bmi",
        "bodyFatPercent",
        "musclePercent",
        "bodyWaterPercent",
        "heartRate",
        "bonePercent",
        "visceralFatLevel",
        "subcutaneousFatPercent",
        "proteinPercent",
        "skeletalMusclePercent",
        "bmr",
        "metabolicAge",
        "goalProgress",
        "dailyAverage",
        "weeklyAverage",
        "monthlyAverage",
      ),
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
    ),
  )
  val dummyState =
    DeviceDisplayMetricsState(
      scale = dummyDevice,
      enabledMetrics = listOf("bmi", "bodyFatPercent", "musclePercent", "bodyWaterPercent").toImmutableList(),
      hasUpdated = true,
    )

  MeAppTheme {
    DeviceDisplayMetricsScreenContent(state = dummyState, handleIntent = {})
  }
}
