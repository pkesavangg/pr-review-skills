package com.dmdbrands.gurus.weight.features.scaleDetails.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.strings.ScaleMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppScaleImage
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageSize
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.features.scaleDetails.Enums.ScaleSettingSteps
import com.dmdbrands.gurus.weight.features.scaleDetails.components.BluetoothPermissionScreen
import com.dmdbrands.gurus.weight.features.scaleDetails.components.WifiMacAddressScreen
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent.SetSettingsScreenStep
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsState
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleNameDialogFormControls
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.ScaleDetailsStrings
import com.dmdbrands.gurus.weight.features.scaleDetails.viewmodel.ScaleDetailsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch

/**
 * ScaleDetails screen composable. Displays scale details and handles user interactions.
 */
@Composable
fun ScaleDetailsScreen(scaleId: String) {
  val viewModel: ScaleDetailsViewModel =
    hiltViewModel<ScaleDetailsViewModel, ScaleDetailsViewModel.Factory>(
      creationCallback = { factory ->
        factory.create(scaleId)
      },
    )
  val state by viewModel.state.collectAsState()

  BackHandler {
    viewModel.handleIntent(ScaleDetailsIntent.Back)
  }

  ScaleDetailsScreenContent(state, viewModel::handleIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleDetailsScreenContent(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val device = state.scale
  val scaleName = device?.nickname ?: device?.device?.deviceName ?: ""
  val scaleSetupType =
    device?.deviceType?.let { ScaleSetupType.fromString(it) } ?: ScaleSetupType.Bluetooth
  val isWifiSetup = scaleSetupType == ScaleSetupType.Wifi || scaleSetupType == ScaleSetupType.EspTouchWifi
  val showUserNumber = (isWifiSetup || scaleSetupType == ScaleSetupType.Bluetooth) && state.scale?.userNumber != null
  val isConnected = device?.connectionStatus == BLEStatus.CONNECTED
  val scaleMode =
    if (device?.preferences?.shouldMeasureImpedance == true) {
      ScaleDetailsStrings.AllBodyMetrics
    } else {
      ScaleDetailsStrings.WeightOnly
    }

  var bottomSheetVisible by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  if (bottomSheetVisible) {
    ModalBottomSheet(
      onDismissRequest = {
        coroutineScope.launch {
          sheetState.hide()
          bottomSheetVisible = false
          handleIntent(SetSettingsScreenStep(ScaleSettingSteps.NONE))
        }
      },
      containerColor = colorScheme.primaryBackground,
      modifier = Modifier.fillMaxSize(),
      dragHandle = null,
      sheetState = sheetState,
      sheetGesturesEnabled = false,
      properties = ModalBottomSheetProperties(
        shouldDismissOnBackPress = true,
        isAppearanceLightStatusBars = !isSystemInDarkTheme(),
        isAppearanceLightNavigationBars = !isSystemInDarkTheme(),
      ),
    ) {
      when (state.settingsScreenStep) {
        ScaleSettingSteps.BLUETOOTH_SETTINGS -> {
          BluetoothPermissionScreen(
            state = state,
            handleIntent = handleIntent,
            onClose = {
              coroutineScope.launch {
                sheetState.hide()
                bottomSheetVisible = false
                handleIntent(SetSettingsScreenStep(ScaleSettingSteps.NONE))
              }
            },
          )
        }

        ScaleSettingSteps.WIFI_MAC_ADDRESS -> {
          WifiMacAddressScreen(
            state = state,
            handleIntent = handleIntent,
            onClose = {
              coroutineScope.launch {
                sheetState.hide()
                bottomSheetVisible = false
                handleIntent(SetSettingsScreenStep(ScaleSettingSteps.NONE))
              }
            },
          )
        }

        ScaleSettingSteps.NONE -> {
          bottomSheetVisible = false
          handleIntent(SetSettingsScreenStep(ScaleSettingSteps.NONE))
        }
      }

    }
  }

  AppScaffold(
    title = scaleName,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
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
      // Scale Image
      AppScaleImage(
        sku = device?.getSKU() ?: "",
        modifier = Modifier.fillMaxWidth(),
        scaleImageSize = ScaleImageSize.Large,
      )
      Spacer(modifier = Modifier.height(spacing.xl))
      Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (device?.isWeighOnlyModeEnabledByOthers == true) {
          AppNote(
            message = ScaleMetricsSettingStrings.WeightOnlyNotes.Message,
            icon = AppIcons.Default.WeightOnlyMode,
            buttonText = ScaleMetricsSettingStrings.WeightOnlyNotes.EnableBodyMetrics,
            onButtonClick = {
              handleIntent(ScaleDetailsIntent.OpenScaleMode)
            },
          )
        }
        if (state.scale?.device?.isWifiConfigured == false && state.scale.connectionStatus == BLEStatus.CONNECTED && scaleSetupType == ScaleSetupType.BtWifiR4) {
          AppNote(
            message = ScaleDetailsStrings.SetupIncomplete,
            icon = AppIcons.Default.Exclamation,
            buttonText = ScaleDetailsStrings.SetupWifi,
            iconType = AppIconType.Danger,
            onButtonClick = {
              handleIntent(ScaleDetailsIntent.OpenWiFiSetup)
            },
          )
        }
        Spacer(modifier = Modifier.height(spacing.md))
      }

      // Settings Section - Show different items based on setup type
      SettingsSection(
        title = ScaleDetailsStrings.Settings,
        items =
          buildList {
            if (scaleSetupType == ScaleSetupType.BtWifiR4) {
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.Mode,
                  type = SettingsItemType.Action(scaleMode),
                  onClick = {
                    handleIntent(ScaleDetailsIntent.OpenScaleMode)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.DisplayMetrics,
                  type = SettingsItemType.Action(""),
                  onClick = {
                    handleIntent(ScaleDetailsIntent.OpenScaleDisplayMetrics)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.Users,
                  type = SettingsItemType.Action(device?.preferences?.displayName ?: ""),
                  enabled = isConnected,
                  onClick = {
                    handleIntent(ScaleDetailsIntent.OpenScaleUsers)
                  },
                ),
              )
            }
            add(
              SettingsItem(
                title = ScaleDetailsStrings.ScaleName,
                type =
                  SettingsItemType.TextOnly(
                    device?.nickname ?: "",
                  ),
                onClick = {
                  handleIntent(ScaleDetailsIntent.ShowScaleNameModal)
                },
              ),
            )
            if (showUserNumber) {
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.UserNumber,
                  type = SettingsItemType.TextOnly("U${device.userNumber}"),
                ),
              )
            }
          },
      )

      // Connection Section - Show different items based on setup type
      if (!isWifiSetup) {
        SettingsSection(
          title = ScaleDetailsStrings.Connection,
          items =
            buildList {
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.Bluetooth,
                  type =
                    SettingsItemType.Action(
                      if (isConnected) ScaleDetailsStrings.Connected else AppListStrings.NotConnected,
                    ),
                  onClick = {
                    handleIntent(SetSettingsScreenStep(ScaleSettingSteps.BLUETOOTH_SETTINGS))
                    bottomSheetVisible = true
                  },
                ),
              )
              if (scaleSetupType == ScaleSetupType.BtWifiR4) {
                add(
                  SettingsItem(
                    title = ScaleDetailsStrings.WiFi,
                    type = SettingsItemType.Action(state.connectedSSID),
                    enabled = device?.device?.isWifiConfigured ?: false,
                    onClick = {
                      handleIntent(
                        ScaleDetailsIntent.OpenWiFiSetup,
                      )
                    },
                  ),
                )
                add(
                  SettingsItem(
                    title = ScaleDetailsStrings.WiFiMacAddress,
                    type = SettingsItemType.Action(),
                    enabled = device?.device?.isWifiConfigured ?: false,
                    onClick = {
                      handleIntent(SetSettingsScreenStep(ScaleSettingSteps.WIFI_MAC_ADDRESS))
                      bottomSheetVisible = true
                    },
                  ),
                )
              }
            },
        )
      }

      // Support Section
      SettingsSection(
        title = ScaleDetailsStrings.Support,
        items =
          listOf(
            SettingsItem(
              title = ScaleDetailsStrings.ScaleType,
              type =
                SettingsItemType.CustomIcon(
                  text = ScaleSetupType.toLabel(device?.deviceType),
                  icon = {
                    AppIcon(
                      id = ScaleDataHelper.scaleTypeIcon(scaleSetupType),
                      contentDescription = ScaleSetupType.toLabel(device?.deviceType),
                      type = AppIconType.Primary,
                    )
                  },
                ),
            ),
            SettingsItem(
              title = ScaleDetailsStrings.Sku,
              type = SettingsItemType.TextOnly(device?.getSKU() ?: ""),
            ),
            SettingsItem(
              title = ScaleDetailsStrings.DatePaired,
              type = SettingsItemType.TextDate(state.scale?.createdAt ?: ""), // Not available in GGDevice
            ),
            SettingsItem(
              title = ScaleDetailsStrings.ProductGuide,
              type = SettingsItemType.Action(),
              onClick = { handleIntent(ScaleDetailsIntent.OpenProductGuide) },
            ),
          ),
      )
      // Delete Scale Button (danger action, outside cards)
      SettingsSection(
        items =
          listOf(
            SettingsItem(
              title = ScaleDetailsStrings.DeleteScale,
              type = SettingsItemType.None,
              color = SettingColorType.Danger,
              onClick = { handleIntent(ScaleDetailsIntent.DeleteScale) },
            ),
          ),
      )
    }
  }
}

@PreviewTheme
@Composable
fun ScaleDetailsScreenPreview() {
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
  val dummyScaleNameForm = FormGroup(ScaleNameDialogFormControls.create())
  val dummyState = ScaleDetailsState(scale = dummyDevice, scaleNameForm = dummyScaleNameForm)
  ScaleDetailsScreenContent(state = dummyState, handleIntent = {})
}
