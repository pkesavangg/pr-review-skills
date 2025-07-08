package com.greatergoods.meapp.features.scaleDisplayMetrics.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.scaleDisplayMetrics.components.DisplayMetricsScreen
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsIntent
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsState
import com.greatergoods.meapp.features.scaleDisplayMetrics.strings.ScaleDisplayMetricsStrings
import com.greatergoods.meapp.features.scaleDisplayMetrics.viewmodel.ScaleDisplayMetricsViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun ScaleDisplayMetricsScreen(scaleId: String) {
  val viewModel: ScaleDisplayMetricsViewModel =
    hiltViewModel<ScaleDisplayMetricsViewModel, ScaleDisplayMetricsViewModel.Factory>(
      creationCallback = { factory -> factory.create(scaleId) },
    )
  val state by viewModel.state.collectAsState()

  BackHandler {
    viewModel.handleIntent(ScaleDisplayMetricsIntent.Back)
  }

  ScaleDisplayMetricsScreenContent(state, viewModel::handleIntent)
}

@Composable
fun ScaleDisplayMetricsScreenContent(
  state: ScaleDisplayMetricsState,
  handleIntent: (ScaleDisplayMetricsIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()

  AppScaffold(
    title = ScaleDisplayMetricsStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
    actions = {
      if (state.hasChanges) {
        AppText(
          text = ScaleDisplayMetricsStrings.Save,
          textType = TextType.ListTitle1,
          color = colorScheme.primaryAction,
          modifier =
            Modifier
              .padding(end = spacing.md)
              .clickable { handleIntent(ScaleDisplayMetricsIntent.Save) },
        )
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(vertical = spacing.md, horizontal = spacing.sm),
    ) {
      // Description
      AppText(
        text = ScaleDisplayMetricsStrings.Description,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.md),
      )

      // Display Metrics Component
      DisplayMetricsScreen(
        currentMetrics = state.enabledMetrics,
        onMetricsChanged = { enabledMetrics ->
          handleIntent(ScaleDisplayMetricsIntent.UpdateMetrics(enabledMetrics))
        },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@PreviewTheme
@Composable
fun ScaleDisplayMetricsScreenPreview() {
  val dummyDevice =
    Device(
      id = "1",
      accountId = "1",
      peripheralIdentifier = null,
      nickname = "My Smart Scale",
      sku = "0412",
      mac = null,
      password = null,
      isDeleted = false,
      deviceName = "AccuCheck Verve Smart Scale",
      deviceType = null,
      broadcastId = null,
      broadcastIdString = null,
      userNumber = null,
      protocolType = null,
      createdAt = "June 27, 2023",
      lastModified = null,
      isSynced = false,
      isConnected = true,
      wifiMac = "greatergoods1",
      isWifiConfigured = true,
      token = null,
      scaleType = "Bluetooth/Wi-Fi",
      bodyComp = true,
      displayName = null,
      displayMetrics =
        listOf(
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
      shouldFactoryReset = false,
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
      timeFormat = null,
      tzOffset = null,
      wifiFotaScheduleTime = null,
      prefsUpdatedAt = null,
      modelNumber = null,
      serialNumber = null,
      firmwareRevision = null,
      hardwareRevision = null,
      softwareRevision = null,
      manufacturerName = null,
      systemId = null,
      latestVersion = null,
      hasNumericUsers = null,
      isWeighOnlyModeEnabledByOthers = false,
      hasServerID = true,
    )
  val dummyState =
    ScaleDisplayMetricsState(
      scale = dummyDevice,
      enabledMetrics = listOf("bmi", "bodyFatPercent", "musclePercent", "bodyWaterPercent"),
      hasChanges = true,
    )

  MeAppTheme {
    ScaleDisplayMetricsScreenContent(state = dummyState, handleIntent = {})
  }
}
