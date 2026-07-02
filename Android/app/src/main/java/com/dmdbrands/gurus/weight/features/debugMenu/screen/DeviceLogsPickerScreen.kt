package com.dmdbrands.gurus.weight.features.debugMenu.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceCard
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuIntent
import com.dmdbrands.gurus.weight.features.debugMenu.strings.DebugMenuStrings
import com.dmdbrands.gurus.weight.features.debugMenu.viewmodel.DebugMenuViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Screen that lists paired BtWifiR4 scales (e.g. 0412); user selects one to send scale log.
 * Uses [DebugMenuViewModel] (same as Debug Menu). Back goes to Debug screen; item click sends log like singular scale.
 * List uses [AppDeviceCard] like [com.dmdbrands.gurus.weight.features.addDevice.screens.AddDeviceScreen].
 */
@Composable
fun DeviceLogsPickerScreen(
  viewModel: DebugMenuViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  BackHandler { viewModel.handleIntent(DebugMenuIntent.OnBack) }

  DeviceLogsPickerContent(
    scaleInfos = state.scaleListScaleInfo,
    devices = state.scaleList,
    onScaleClick = { device -> viewModel.handleIntent(DebugMenuIntent.SendScaleLogForScale(device)) },
    onBackToDebug = { viewModel.handleIntent(DebugMenuIntent.OnBack) },
  )
}

@Composable
private fun DeviceLogsPickerContent(
  scaleInfos: List<DeviceModelInfo>,
  devices: List<Device>,
  onScaleClick: (Device) -> Unit,
  onBackToDebug: () -> Unit,
) {
  AppScaffold(
    title = DebugMenuStrings.Actions.SendScaleLog,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) { onBackToDebug() }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .then(scaffoldModifier)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = spacing.sm),
    ) {
      scaleInfos.forEachIndexed { index, scaleInfo ->
        AppDeviceCard(
          scale = scaleInfo,
          isSavedScale = true,
          enabled = scaleInfo.isConnected == true,
          onClick = { if (index < devices.size) onScaleClick(devices[index]) },
        )
      }
    }
  }
}
