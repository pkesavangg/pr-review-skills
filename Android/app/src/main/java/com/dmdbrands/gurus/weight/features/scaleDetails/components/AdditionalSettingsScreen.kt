package com.dmdbrands.gurus.weight.features.scaleDetails.components

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
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsState
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.ScaleDetailsStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Additional Settings Screen - similar to Angular's AdditionalScaleSettingsPageComponent
 * Shows advanced scale settings and actions
 */
@Composable
fun AdditionalSettingsScreen(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  val device = state.scale
  val isConnected = device?.connectionStatus == com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus.CONNECTED
  AppScaffold(
    title = ScaleDetailsStrings.OtherSettings,
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

      // Scale Information Section
      SettingsSection(
        title = ScaleDetailsStrings.ScaleDetails,
        items = listOf(
          SettingsItem(
            title = ScaleDetailsStrings.Manufacturer,
            type = SettingsItemType.TextOnly(state.deviceInfo?.manufacturerName ?: ScaleDetailsStrings.Unknown),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.DeviceName,
            type = SettingsItemType.TextOnly(device?.device?.deviceName ?: ScaleDetailsStrings.Unknown),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.MacAddress,
            type = SettingsItemType.TextOnly(device?.device?.macAddress ?: ScaleDetailsStrings.Unknown),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.BroadcastId,
            type = SettingsItemType.TextOnly(device?.device?.broadcastId ?: ScaleDetailsStrings.Unknown),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.FirmwareRevision,
            type = SettingsItemType.TextOnly(state.deviceInfo?.firmwareRevision ?: ScaleDetailsStrings.Unknown),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.BatteryLevel,
            type = SettingsItemType.TextOnly(
              "${state.deviceInfo?.batteryLevel ?: ScaleDetailsStrings.Unknown}%",
            ),
          ),
        ),
      )

      SettingsSection(
        title = ScaleDetailsStrings.ScaleFeatures,
        items = listOf(
          SettingsItem(
            title = ScaleDetailsStrings.EnableStartAnimation,
            type = SettingsItemType.Toggle(
              checked = state.isStartAnimationEnabled,
              onCheckedChange = { enabled ->
                handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(true, enabled))
              },
            ),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.EnableEndAnimation,
            type = SettingsItemType.Toggle(
              checked = state.isEndAnimationEnabled,
              onCheckedChange = { enabled ->
                handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(false, enabled))
              },
            ),
          ),
          SettingsItem(
            title = ScaleDetailsStrings.TimeFormat,
            type = SettingsItemType.Dropdown(
              state.currentTimeFormat ?: ScaleDetailsStrings.TimeFormat12H,
            ),
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.ShowTimeFormatDialog)
            },
          ),
          SettingsItem(
            title = ScaleDetailsStrings.ResetFirmware,
            type = SettingsItemType.Action(),
            color = SettingColorType.Danger,
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.ResetFirmware)
            },
          ),
          SettingsItem(
            title = ScaleDetailsStrings.FactoryReset,
            type = SettingsItemType.Action(),
            color = SettingColorType.Danger,
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.RestoreFactorySettings)
            },
          ),
          SettingsItem(
            title = ScaleDetailsStrings.DownloadLogs,
            type = SettingsItemType.Action(),
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.DownloadLogs)
            },
          ),
          SettingsItem(
            title = ScaleDetailsStrings.ClearScaleData,
            type = SettingsItemType.Dropdown(
              when (state.currentClearDataSelection) {
                "ALL" -> ScaleDetailsStrings.All
                "WIFI" -> ScaleDetailsStrings.WiFi
                "SETTINGS" -> ScaleDetailsStrings.Settings
                "HISTORY" -> ScaleDetailsStrings.History
                "ACCOUNT" -> ScaleDetailsStrings.Account
                else -> ScaleDetailsStrings.NotSet
              },
            ),
            color = SettingColorType.Danger,
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.ShowClearDataDialog)
            },
          ),
        ),
      )
    }
  }
}

@PreviewTheme
@Composable
fun AdditionalSettingsScreenPreview() {
  val dummyState = ScaleDetailsState(
    scale = null,
    scaleNameForm = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(
      com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleNameDialogFormControls.create(),
    ),
  )
  AdditionalSettingsScreen(
    state = dummyState,
    handleIntent = {},
    onClose = {},
  )
}
