package com.greatergoods.meapp.features.scaleDisplayMetrics.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.scaleDisplayMetrics.components.ScaleMetricsNotes
import com.greatergoods.meapp.features.ScaleMetricsSetting.Screens.ScaleMetricsSettingScreen
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
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
      if (state.hasUpdated) {
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
    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(vertical = spacing.md, horizontal = spacing.sm),
    ) {
      item {
        // Notes
        state.scale?.let { scale ->
          ScaleMetricsNotes(
            scale = scale,
            onUpdateScaleMode = {
              handleIntent(ScaleDisplayMetricsIntent.UpdateScaleMode)
            },
          )
        }

        // Description
        AppText(
          text = ScaleDisplayMetricsStrings.Description,
          textType = TextType.Body,
          modifier = Modifier.padding(bottom = spacing.md),
        )
      }

        item {
        // Display Metrics Component
        state.scale?.let { scale ->
          ScaleMetricsSettingScreen(
            currentMetrics = scale.preferences?.displayMetrics ?: emptyList(),
            onMetricsChanged = { enabledMetrics ->
              handleIntent(ScaleDisplayMetricsIntent.UpdateMetrics(enabledMetrics))
            },
          )
        }
      }
    }
  }
}

@PreviewTheme
@Composable
fun ScaleDisplayMetricsScreenPreview() {
  val dummyDevice = Device(
    id = "1",
    device = com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail(
      deviceName = "AccuCheck Verve Smart Scale",
      macAddress = "greatergoods1",
      identifier = "identifier1",
    ),
    connectionStatus = com.greatergoods.meapp.domain.model.storage.BLEStatus.CONNECTED,
    alreadyPaired = true,
    userNumber = 1,
    preferences = com.greatergoods.meapp.domain.model.storage.Preferences(
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
    ScaleDisplayMetricsState(
      scale = dummyDevice,
      enabledMetrics = listOf("bmi", "bodyFatPercent", "musclePercent", "bodyWaterPercent"),
      hasUpdated = true,
    )

  MeAppTheme {
    ScaleDisplayMetricsScreenContent(state = dummyState, handleIntent = {})
  }
}
