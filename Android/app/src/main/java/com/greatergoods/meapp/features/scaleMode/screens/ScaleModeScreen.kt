package com.greatergoods.meapp.features.scaleMode.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.ScaleModeSettings.screens.ScaleModeSettingsScreen
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppNote
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeIntent
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeState
import com.greatergoods.meapp.features.scaleMode.strings.ScaleModeStrings
import com.greatergoods.meapp.features.scaleMode.viewmodel.ScaleModeViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun ScaleModeScreen(scaleId: String) {
  val viewModel: ScaleModeViewModel =
    hiltViewModel<ScaleModeViewModel, ScaleModeViewModel.Factory>(
      creationCallback = { factory -> factory.create(scaleId) },
    )
  val state by viewModel.state.collectAsState()

  BackHandler {
    viewModel.handleIntent(ScaleModeIntent.Back)
  }

  ScaleModeScreenContent(state, viewModel::handleIntent)
}

@Composable
fun ScaleModeScreenContent(
  state: ScaleModeState,
  handleIntent: (ScaleModeIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val isAllBodyMetrics = state.isAllBodyMetrics


  AppScaffold(
    title = ScaleModeStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
    actions = {
      if (state.hasModeChanged) {
        AppText(
          text = ScaleModeStrings.Save,
          textType = TextType.ListTitle1,
          color = colorScheme.primaryAction,
          modifier =
            Modifier
              .padding(end = spacing.md)
              .clickable { handleIntent(ScaleModeIntent.Save) },
        )
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(vertical = spacing.md, horizontal = spacing.sm),
    ) {
      if (state.scale?.isWeighOnlyModeEnabledByOthers == true && isAllBodyMetrics) {
        AppNote(
          title = ScaleModeStrings.WeightOnlyModeNotes.Title,
          message = ScaleModeStrings.WeightOnlyModeNotes.Message,
          icon = AppIcons.Default.WeightOnlyMode
        )
      }
      ScaleModeSettingsScreen(
        isAllBodyMetrics = isAllBodyMetrics,
        isHeartRateOn = state.isHeartRateOn,
        onModeSelected = { isAllBodyMetrics ->
          handleIntent(ScaleModeIntent.SetMode(isAllBodyMetrics, true))
        },
        onHeartRateToggle = { isHeartRateOn -> handleIntent(ScaleModeIntent.SetHeartRate(isHeartRateOn, true)) },
        onBioimpedanceClick = { handleIntent(ScaleModeIntent.OpenBiaModal) },
      )
    }

  }
}

@PreviewTheme
@Composable
fun ScaleModeScreenPreview() {
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
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
    ),
  )
  val dummyState =
    ScaleModeState(
      scale = dummyDevice,
      isAllBodyMetrics = true,
      isHeartRateOn = false,
    )
  ScaleModeScreenContent(state = dummyState, handleIntent = {})
}
