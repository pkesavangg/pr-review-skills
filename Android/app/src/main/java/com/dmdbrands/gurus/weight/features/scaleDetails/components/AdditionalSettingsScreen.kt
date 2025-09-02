package com.dmdbrands.gurus.weight.features.scaleDetails.components

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
        title = "Scale Details",
        items = listOf(
          SettingsItem(
            title = "Manufacturer",
            type = SettingsItemType.TextOnly(state.deviceInfo?.manufacturerName ?: "Unknown"),
          ),
          SettingsItem(
            title = "Device Name",
            type = SettingsItemType.TextOnly(device?.device?.deviceName ?: "Unknown"),
          ),
          SettingsItem(
            title = "MAC Address",
            type = SettingsItemType.TextOnly(device?.device?.macAddress ?: "Unknown"),
          ),
          SettingsItem(
            title = "Broadcast ID",
            type = SettingsItemType.TextOnly(device?.device?.broadcastId ?: "Unknown"),
          ),
          SettingsItem(
            title = "Firmware Revision",
            type = SettingsItemType.TextOnly(state.deviceInfo?.firmwareRevision ?: "Unknown"),
          ),
          SettingsItem(
            title = "Battery Level",
            type = SettingsItemType.TextOnly(
              "${state.deviceInfo?.batteryLevel ?: "Unknown"}%",
            ),
          ),
        ),
      )

      SettingsSection(
        title = "Scale Features",
        items = listOf(
          SettingsItem(
            title = "Enable Start Animation",
            type = SettingsItemType.Toggle(
              checked = state.isStartAnimationEnabled,
              onCheckedChange = { enabled ->
                handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(true, enabled))
              },
            ),
          ),
          SettingsItem(
            title = "Enable End Animation",
            type = SettingsItemType.Toggle(
              checked = state.isEndAnimationEnabled,
              onCheckedChange = { enabled ->
                handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(false, enabled))
              },
            ),
          ),
          SettingsItem(
            title = "Time Format",
            type = SettingsItemType.Dropdown(
              state.currentTimeFormat ?: "12H",
            ),
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.ShowTimeFormatDialog)
            },
          ),
          SettingsItem(
            title = "Reset Firmware",
            type = SettingsItemType.Action(),
            color = SettingColorType.Danger,
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.ResetFirmware)
            },
          ),
          SettingsItem(
            title = "Factory Reset",
            type = SettingsItemType.Action(),
            color = SettingColorType.Danger,
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.RestoreFactorySettings)
            },
          ),
          SettingsItem(
            title = "Download Logs",
            type = SettingsItemType.Action(),
            enabled = isConnected,
            onClick = {
              handleIntent(ScaleDetailsIntent.DownloadLogs)
            },
          ),
          SettingsItem(
            title = "Clear Scale Data",
            type = SettingsItemType.Dropdown(
              when (state.currentClearDataSelection) {
                "ALL" -> "All"
                "WIFI" -> "Wi-Fi"
                "SETTINGS" -> "Settings"
                "HISTORY" -> "History"
                "ACCOUNT" -> "Account"
                else -> "Not Set"
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
