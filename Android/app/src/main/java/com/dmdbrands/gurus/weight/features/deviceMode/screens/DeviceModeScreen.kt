package com.dmdbrands.gurus.weight.features.deviceMode.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.dmdbrands.gurus.weight.features.DeviceModeSettings.screens.DeviceModeSettingsScreen
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.deviceMode.reducer.DeviceModeIntent
import com.dmdbrands.gurus.weight.features.deviceMode.reducer.DeviceModeState
import com.dmdbrands.gurus.weight.features.deviceMode.strings.DeviceModeStrings
import com.dmdbrands.gurus.weight.features.deviceMode.viewmodel.DeviceModeViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun DeviceModeScreen(scaleId: String) {
  val viewModel: DeviceModeViewModel =
    hiltViewModel<DeviceModeViewModel, DeviceModeViewModel.Factory>(
      creationCallback = { factory -> factory.create(scaleId) },
    )
  val state by viewModel.state.collectAsStateWithLifecycle()

  BackHandler {
    viewModel.handleIntent(DeviceModeIntent.Back)
  }

  DeviceModeScreenContent(state, viewModel::handleIntent)
}

@Composable
fun DeviceModeScreenContent(
  state: DeviceModeState,
  handleIntent: (DeviceModeIntent) -> Unit,
) {
  val isAllBodyMetrics = state.isAllBodyMetrics

  AppScaffold(
    title = DeviceModeStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, contentDescription = DeviceModeStrings.accCloseLabel) {
        handleIntent(DeviceModeIntent.Back)
      }
    },
    actions = {
      if (state.hasModeChanged) {
        AppText(
          text = DeviceModeStrings.Save,
          textType = TextType.ListTitle1,
          color = colorScheme.primaryAction,
          modifier =
            Modifier
              .padding(end = spacing.md)
              // TalkBack: this clickable text acts as a button, so expose the Button role.
              .semantics { role = Role.Button }
              .clickable { handleIntent(DeviceModeIntent.Save) },
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
          title = DeviceModeStrings.WeightOnlyModeNotes.Title,
          message = DeviceModeStrings.WeightOnlyModeNotes.Message,
          icon = AppIcons.Default.WeightOnlyMode,
          modifier = Modifier.padding(bottom = spacing.sm),
        )
      }
      DeviceModeSettingsScreen(
        isAllBodyMetrics = isAllBodyMetrics,
        isHeartRateOn = state.isHeartRateOn,
        onModeSelected = { isAllBodyMetrics ->
          handleIntent(DeviceModeIntent.SetMode(isAllBodyMetrics, true))
        },
        onHeartRateToggle = { isHeartRateOn -> handleIntent(DeviceModeIntent.SetHeartRate(isHeartRateOn, true)) },
        onBioimpedanceClick = { handleIntent(DeviceModeIntent.OpenBiaModal) },
      )
    }

  }
}

@PreviewTheme
@Composable
fun DeviceModeScreenPreview() {
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
    preferences = com.dmdbrands.gurus.weight.domain.model.storage.Preferences(
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
    ),
  )
  val dummyState =
    DeviceModeState(
      scale = dummyDevice,
      isAllBodyMetrics = true,
      isHeartRateOn = false,
    )
  DeviceModeScreenContent(state = dummyState, handleIntent = {})
}
