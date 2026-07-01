package com.dmdbrands.gurus.weight.features.deviceDetails.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.strings.DeviceMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceImage
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.DeviceImageSize
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.Enums.DeviceSettingSteps
import com.dmdbrands.gurus.weight.features.deviceDetails.components.AdditionalSettingsScreen
import com.dmdbrands.gurus.weight.features.deviceDetails.components.BluetoothPermissionScreen
import com.dmdbrands.gurus.weight.features.deviceDetails.components.SoftwareUpdateScreen
import com.dmdbrands.gurus.weight.features.deviceDetails.components.WifiMacAddressScreen
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent.SetSettingsScreenStep
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceNameDialogFormControls
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel.DeviceDetailsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch

/**
 * DeviceDetails screen composable. Displays scale details and handles user interactions.
 */
@Composable
fun DeviceDetailsScreen(scaleId: String) {
  val viewModel: DeviceDetailsViewModel =
    hiltViewModel<DeviceDetailsViewModel, DeviceDetailsViewModel.Factory>(
      creationCallback = { factory ->
        factory.create(scaleId)
      },
    )
  val state by viewModel.state.collectAsStateWithLifecycle()

  BackHandler {
    viewModel.handleIntent(DeviceDetailsIntent.Back)
  }

  DeviceDetailsScreenContent(state, viewModel::handleIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreenContent(
  state: DeviceDetailsState,
  handleIntent: (DeviceDetailsIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val device = state.scale
  val scaleName = device?.nickname
  val scaleSetupType =
    device?.deviceType?.let { DeviceSetupType.fromString(it) } ?: DeviceSetupType.Bluetooth
  val isWifiSetup = scaleSetupType == DeviceSetupType.Wifi || scaleSetupType == DeviceSetupType.EspTouchWifi
  val isBpm = DeviceHelper.isBpmDevice(device?.getSKU())
  val showUserNumber = (isWifiSetup || scaleSetupType == DeviceSetupType.Bluetooth) && state.scale?.userNumber != null
  val isConnected = device?.connectionStatus == BLEStatus.CONNECTED
  val scaleMode =
    if (device?.preferences?.shouldMeasureImpedance == true) {
      DeviceDetailsStrings.AllBodyMetrics
    } else {
      DeviceDetailsStrings.WeightOnly
    }
  val isR4Scale = scaleSetupType == DeviceSetupType.BtWifiR4
  val canEnableTestingFeatures = state.enableTestingFeatures


  AppScaffold(
    title = scaleName,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, contentDescription = DeviceDetailsStrings.accCloseLabel) {
        coroutineScope.launch {
          backStack.removeLast()
        }
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
      // Scale Image - map SKU for display (e.g., 0022 -> 0383)
      AppDeviceImage(
        sku = DeviceHelper.mapSkuForDisplay(device?.getSKU() ?: ""),
        modifier = Modifier.fillMaxWidth(),
        scaleImageSize = DeviceImageSize.Large,
      )
      Spacer(modifier = Modifier.height(spacing.xl))
      Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (state.scale?.isWeighOnlyModeEnabledByOthers == true && state.scale?.connectionStatus == BLEStatus.CONNECTED) {
          AppNote(
            message = DeviceMetricsSettingStrings.WeightOnlyNotes.Message,
            icon = AppIcons.Default.WeightOnlyMode,
            buttonText = DeviceMetricsSettingStrings.WeightOnlyNotes.EnableBodyMetrics,
            onButtonClick = {
              handleIntent(DeviceDetailsIntent.ShowEnableBodyMetricsAlert)
            },
          )
        }
        // Show SetupIncomplete note if Wi-Fi is not configured AND no SSID is connected
        val isWifiConfigured = state.scale?.device?.isWifiConfigured == true || !state.connectedSSID.isNullOrEmpty()
        if (!isWifiConfigured && state.scale?.connectionStatus == BLEStatus.CONNECTED && scaleSetupType == DeviceSetupType.BtWifiR4) {
          AppNote(
            message = DeviceDetailsStrings.SetupIncomplete,
            icon = AppIcons.Default.Exclamation,
            buttonText = DeviceDetailsStrings.SetupWifi,
            iconType = AppIconType.Danger,
            onButtonClick = {
              handleIntent(DeviceDetailsIntent.OpenWiFiSetup)
            },
          )
        }
        Spacer(modifier = Modifier.height(spacing.md))
      }

      // Settings Section - Show different items based on setup type
      SettingsSection(
        title = DeviceDetailsStrings.Settings,
        items =
          buildList {
            if (scaleSetupType == DeviceSetupType.BtWifiR4) {
              add(
                SettingsItem(
                  title = DeviceDetailsStrings.Mode,
                  type = SettingsItemType.Action(scaleMode),
                  onClick = {
                    handleIntent(DeviceDetailsIntent.OpenScaleMode)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = DeviceDetailsStrings.DisplayMetrics,
                  type = SettingsItemType.Action(""),
                  onClick = {
                    handleIntent(DeviceDetailsIntent.OpenScaleDisplayMetrics)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = DeviceDetailsStrings.Users,
                  type = SettingsItemType.Action(device?.preferences?.displayName ?: ""),
                  enabled = isConnected,
                  onClick = {
                    handleIntent(DeviceDetailsIntent.OpenScaleUsers)
                  },
                ),
              )
            }
            add(
              SettingsItem(
                title = DeviceDetailsStrings.DeviceName,
                type =
                  SettingsItemType.TextOnly(
                    scaleName ?: "", // Display truncated name to match SDK limit
                  ),
                onClick = {
                  handleIntent(DeviceDetailsIntent.ShowScaleNameModal)
                },
              ),
            )
            if (showUserNumber) {
              val userLabel = if (isBpm) {
                val scaleInfo = DeviceDataHelper.findScaleInfoBySku(device.getSKU())
                DeviceDataHelper.formatUserDisplay(
                  scaleInfo?.hasNumericUsers ?: true,
                  device.userNumber,
                )
              } else {
                "U${device.userNumber}"
              }
              if (userLabel.isNotEmpty()) {
                add(
                  SettingsItem(
                    title = DeviceDetailsStrings.userNumberLabel(device.getSKU()),
                    type = SettingsItemType.TextOnly(userLabel),
                  ),
                )
              }
            }
          },
      )

      // Connection Section - Show different items based on setup type
      if (!isWifiSetup && scaleSetupType != DeviceSetupType.AppSync) {
        SettingsSection(
          title = DeviceDetailsStrings.Connection,
          items =
            buildList {
              add(
                SettingsItem(
                  title = DeviceDetailsStrings.Bluetooth,
                  type =
                    SettingsItemType.Action(
                      if (isConnected) DeviceDetailsStrings.Connected else AppListStrings.NotConnected,
                    ),
                  onClick = {
                    handleIntent(SetSettingsScreenStep(DeviceSettingSteps.BLUETOOTH_SETTINGS))
                  },
                ),
              )
              if (scaleSetupType == DeviceSetupType.BtWifiR4) {
                // Wi-Fi is considered configured if we have isWifiConfigured=true OR if we have a connected SSID
                val isWifiConfigured = device?.device?.isWifiConfigured == true || !state.connectedSSID.isNullOrEmpty()
                add(
                  SettingsItem(
                    title = DeviceDetailsStrings.WiFi,
                    type = SettingsItemType.Action(state.connectedSSID),
                    enabled = isConnected,
                    onClick = {
                      handleIntent(
                        DeviceDetailsIntent.OpenWiFiSetup,
                      )
                    },
                  ),
                )
                add(
                  SettingsItem(
                    title = DeviceDetailsStrings.WiFiMacAddress,
                    type = SettingsItemType.Action(),
                    enabled = isConnected,

                    onClick = {
                      handleIntent(SetSettingsScreenStep(DeviceSettingSteps.WIFI_MAC_ADDRESS))
                    },
                  ),
                )
              }
            },
        )
      }

      // Support Section
      SettingsSection(
        title = DeviceDetailsStrings.Support,
        items =
          listOf(
            SettingsItem(
              title = DeviceDetailsStrings.ScaleType,
              type =
                SettingsItemType.CustomIcon(
                  text = DeviceSetupType.toLabel(device?.deviceType),
                  icon = {
                    AppIcon(
                      id = DeviceDataHelper.scaleTypeIcon(scaleSetupType),
                      contentDescription = DeviceSetupType.toLabel(device?.deviceType),
                      type = AppIconType.Primary,
                    )
                  },
                ),
            ),
            SettingsItem(
              title = DeviceDetailsStrings.Sku,
              type = SettingsItemType.TextOnly(DeviceHelper.mapSkuForDisplay(device?.getSKU() ?: "")),
            ),
            SettingsItem(
              title = DeviceDetailsStrings.DatePaired,
              type = SettingsItemType.TextDate(state.scale?.createdAt ?: ""), // Not available in GGDevice
            ),
            SettingsItem(
              title = DeviceDetailsStrings.ProductGuide,
              type = SettingsItemType.Action(),
              onClick = { handleIntent(DeviceDetailsIntent.OpenProductGuide) },
            ),
          ),
      )
      // Delete Scale Button (danger action, outside cards)
      SettingsSection(
        items =
          listOf(
            SettingsItem(
              title = DeviceDetailsStrings.DeleteLabel,
              type = SettingsItemType.None,
              color = SettingColorType.Danger,
              onClick = { handleIntent(DeviceDetailsIntent.DeleteScale) },
            ),
          ),
      )
      // Testing Features Section (similar to Angular implementation)
      if (isR4Scale && canEnableTestingFeatures) {
        SettingsSection(
          title = DeviceDetailsStrings.Others,
          items = listOf(
            SettingsItem(
              title = DeviceDetailsStrings.DeviceMac,
              type = SettingsItemType.TextOnly(device?.device?.macAddress ?: "Unknown"),
            ),
            SettingsItem(
              title = DeviceDetailsStrings.SoftwareUpdate,
              type = SettingsItemType.Action(),
              enabled = isConnected,
              color = if (isConnected) SettingColorType.Default else SettingColorType.Tertiary,
              onClick = {
                if (!isConnected) {
                  return@SettingsItem
                }
                handleIntent(SetSettingsScreenStep(DeviceSettingSteps.SOFTWARE_UPDATE))
              },
            ),
            SettingsItem(
              title = DeviceDetailsStrings.OtherSettings,
              type = SettingsItemType.Action(),
              enabled = isConnected,
              color = if (isConnected) SettingColorType.Default else SettingColorType.Tertiary,
              onClick = {
                if (!isConnected) {
                  return@SettingsItem
                }
                handleIntent(SetSettingsScreenStep(DeviceSettingSteps.ADDITIONAL_SETTINGS))
              },
            ),
            SettingsItem(
              title = DeviceDetailsStrings.SessionImpedance,
              type = SettingsItemType.Toggle(
                checked = state.isSessionImpedanceEnabled,
                onCheckedChange = { enabled ->
                  if (!isConnected) {
                    return@Toggle
                  }
                  handleIntent(DeviceDetailsIntent.ToggleSessionImpedance(enabled))
                },
              ),
              color = if (isConnected) SettingColorType.Default else SettingColorType.Tertiary,
              enabled = isConnected,
            ),
          ),
        )
      }
    }
  }

  AnimatedContent(
    targetState = state.settingsScreenStep,
    label = "SettingsStepTransition",
    transitionSpec = {
      EnterTransition.None togetherWith ExitTransition.None
    },
  ) { step ->
    when (step) {
      DeviceSettingSteps.BLUETOOTH_SETTINGS ->
        BluetoothPermissionScreen(state, handleIntent) {
          handleIntent(SetSettingsScreenStep(DeviceSettingSteps.NONE))
        }

      DeviceSettingSteps.WIFI_MAC_ADDRESS ->
        WifiMacAddressScreen(state, handleIntent) {
          handleIntent(SetSettingsScreenStep(DeviceSettingSteps.NONE))
        }

      DeviceSettingSteps.SOFTWARE_UPDATE ->
        SoftwareUpdateScreen(state, handleIntent) {
          handleIntent(SetSettingsScreenStep(DeviceSettingSteps.NONE))
        }

      DeviceSettingSteps.ADDITIONAL_SETTINGS ->
        AdditionalSettingsScreen(state, handleIntent) {
          handleIntent(SetSettingsScreenStep(DeviceSettingSteps.NONE))
        }

      DeviceSettingSteps.NONE -> Unit
    }
  }
}

@PreviewTheme
@Composable
fun DeviceDetailsScreenPreview() {
  val dummyDevice = Device(
    id = "1",
    device = com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail(
      deviceName = "AccuCheck Verve Smart Scale",
      macAddress = "greatergoods1",
      identifier = "identifier1",
    ),
    connectionStatus = BLEStatus.CONNECTED,
    alreadyPaired = true,
    userNumber = 1,
    preferences = com.dmdbrands.gurus.weight.domain.model.storage.Preferences(
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
    ),
  )
  val dummyScaleNameForm = FormGroup(DeviceNameDialogFormControls.create())
  val dummyState = DeviceDetailsState(scale = dummyDevice, scaleNameForm = dummyScaleNameForm)
  DeviceDetailsScreenContent(state = dummyState, handleIntent = {})
}
