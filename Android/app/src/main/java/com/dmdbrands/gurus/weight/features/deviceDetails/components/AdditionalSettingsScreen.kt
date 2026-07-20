package com.dmdbrands.gurus.weight.features.deviceDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Additional Settings Screen - similar to Angular's AdditionalScaleSettingsPageComponent
 * Shows advanced scale settings and actions
 */
@Composable
fun AdditionalSettingsScreen(
  state: DeviceDetailsState,
  handleIntent: (DeviceDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  val device = state.scale
  val isConnected = device?.connectionStatus == com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus.CONNECTED
  AppScaffold(
    title = DeviceDetailsStrings.OtherSettings,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        onClose()
      }
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = spacing.md, horizontal = spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
      DeviceInformationSection(state)
      DeviceFeaturesSection(state, isConnected, handleIntent)
    }
  }
}

@Composable
private fun DeviceInformationSection(state: DeviceDetailsState) {
  val device = state.scale
  SettingsSection(
    title = DeviceDetailsStrings.DeviceDetails,
    items = listOf(
      SettingsItem(
        title = DeviceDetailsStrings.Manufacturer,
        type = SettingsItemType.TextOnly(state.deviceInfo?.manufacturerName ?: DeviceDetailsStrings.Unknown),
      ),
      SettingsItem(
        title = DeviceDetailsStrings.DeviceName,
        type = SettingsItemType.TextOnly(device?.device?.deviceName ?: DeviceDetailsStrings.Unknown),
      ),
      SettingsItem(
        title = DeviceDetailsStrings.MacAddress,
        type = SettingsItemType.TextOnly(device?.device?.macAddress ?: DeviceDetailsStrings.Unknown),
      ),
      SettingsItem(
        title = DeviceDetailsStrings.BroadcastId,
        type = SettingsItemType.TextOnly(device?.device?.broadcastId ?: DeviceDetailsStrings.Unknown),
      ),
      SettingsItem(
        title = DeviceDetailsStrings.FirmwareRevision,
        type = SettingsItemType.TextOnly(state.deviceInfo?.firmwareRevision ?: DeviceDetailsStrings.Unknown),
      ),
      SettingsItem(
        title = DeviceDetailsStrings.BatteryLevel,
        type = SettingsItemType.TextOnly(
          "${state.deviceInfo?.batteryLevel ?: DeviceDetailsStrings.Unknown}%",
        ),
      ),
    ),
  )
}

@Composable
private fun DeviceFeaturesSection(
  state: DeviceDetailsState,
  isConnected: Boolean,
  handleIntent: (DeviceDetailsIntent) -> Unit,
) {
  SettingsSection(
    title = DeviceDetailsStrings.DeviceFeatures,
    items = buildList {
      addAll(deviceAnimationItems(state, isConnected, handleIntent))
      addAll(deviceMaintenanceItems(state, isConnected, handleIntent))
    },
  )
}

private fun deviceAnimationItems(
  state: DeviceDetailsState,
  isConnected: Boolean,
  handleIntent: (DeviceDetailsIntent) -> Unit,
): List<SettingsItem> =
  listOf(
    SettingsItem(
      title = DeviceDetailsStrings.EnableStartAnimation,
      type = SettingsItemType.Toggle(
        checked = state.isStartAnimationEnabled,
        onCheckedChange = { enabled ->
          handleIntent(DeviceDetailsIntent.ToggleScaleAnimation(true, enabled))
        },
      ),
    ),
    SettingsItem(
      title = DeviceDetailsStrings.EnableEndAnimation,
      type = SettingsItemType.Toggle(
        checked = state.isEndAnimationEnabled,
        onCheckedChange = { enabled ->
          handleIntent(DeviceDetailsIntent.ToggleScaleAnimation(false, enabled))
        },
      ),
    ),
    SettingsItem(
      title = DeviceDetailsStrings.TimeFormat,
      type = SettingsItemType.Dropdown(
        state.currentTimeFormat ?: DeviceDetailsStrings.TimeFormat12H,
      ),
      enabled = isConnected,
      onClick = {
        handleIntent(DeviceDetailsIntent.ShowTimeFormatDialog)
      },
    ),
  )

private fun deviceMaintenanceItems(
  state: DeviceDetailsState,
  isConnected: Boolean,
  handleIntent: (DeviceDetailsIntent) -> Unit,
): List<SettingsItem> =
  listOf(
    SettingsItem(
      title = DeviceDetailsStrings.ResetFirmware,
      type = SettingsItemType.Action(),
      color = SettingColorType.Danger,
      enabled = isConnected,
      onClick = {
        handleIntent(DeviceDetailsIntent.ResetFirmware)
      },
    ),
    SettingsItem(
      title = DeviceDetailsStrings.FactoryReset,
      type = SettingsItemType.Action(),
      color = SettingColorType.Danger,
      enabled = isConnected,
      onClick = {
        handleIntent(DeviceDetailsIntent.RestoreFactorySettings)
      },
    ),
    SettingsItem(
      title = DeviceDetailsStrings.DownloadLogs,
      type = SettingsItemType.Action(),
      enabled = isConnected,
      onClick = {
        handleIntent(DeviceDetailsIntent.DownloadLogs)
      },
    ),
    SettingsItem(
      title = DeviceDetailsStrings.ClearScaleData,
      type = SettingsItemType.Dropdown(
        when (state.currentClearDataSelection) {
          "ALL" -> DeviceDetailsStrings.All
          "WIFI" -> DeviceDetailsStrings.WiFi
          "SETTINGS" -> DeviceDetailsStrings.Settings
          "HISTORY" -> DeviceDetailsStrings.History
          "ACCOUNT" -> DeviceDetailsStrings.Account
          else -> DeviceDetailsStrings.NotSet
        },
      ),
      color = SettingColorType.Danger,
      enabled = isConnected,
      onClick = {
        handleIntent(DeviceDetailsIntent.ShowClearDataDialog)
      },
    ),
  )

@PreviewTheme
@Composable
fun AdditionalSettingsScreenPreview() {
  val dummyState = DeviceDetailsState(
    scale = null,
    scaleNameForm = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(
      com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceNameDialogFormControls.create(),
    ),
  )
  AdditionalSettingsScreen(
    state = dummyState,
    handleIntent = {},
    onClose = {},
  )
}
