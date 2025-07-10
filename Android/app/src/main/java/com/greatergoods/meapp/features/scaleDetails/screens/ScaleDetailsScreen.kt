package com.greatergoods.meapp.features.scaleDetails.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppScaleImage
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleImageSize
import com.greatergoods.meapp.features.common.components.SettingsSection
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.helper.ScaleDataHelper
import com.greatergoods.meapp.features.common.model.SettingColorType
import com.greatergoods.meapp.features.common.model.SettingsItem
import com.greatergoods.meapp.features.common.model.SettingsItemType
import com.greatergoods.meapp.features.common.strings.AppListStrings
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleDetailsStrings
import com.greatergoods.meapp.features.scaleDetails.viewmodel.ScaleDetailsViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme.spacing
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

@Composable
fun ScaleDetailsScreenContent(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val device = state.scale
  val scaleName = device?.nickname ?: device?.deviceName ?: ""
  val scaleSetupType =
    device?.scaleType?.let { ScaleSetupType.fromString(it) } ?: ScaleSetupType.Bluetooth
  val isWifiSetup = scaleSetupType == ScaleSetupType.Wifi || scaleSetupType == ScaleSetupType.EspTouchWifi
  val isConnected = device?.isConnected ?: false
 val scaleMode =
        if (device?.shouldMeasureImpedance ==
            true
        ) {
            ScaleDetailsStrings.AllBodyMetrics
        } else {
            ScaleDetailsStrings.WeightOnly
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
      AppScaleImage(sku = device?.sku ?: "", modifier = Modifier.fillMaxWidth(), scaleImageSize = ScaleImageSize.Large)
      Spacer(modifier = Modifier.height(spacing.xl))
      // Settings Section - Show different items based on setup type
      SettingsSection(
        title = ScaleDetailsStrings.Settings,
        items =
          buildList {
            if (scaleSetupType == ScaleSetupType.BtWifiR4) {
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.Mode,
                  type = SettingsItemType.Action(ScaleDetailsStrings.AllBodyMetrics),
                  onClick = {
                    handleIntent(ScaleDetailsIntent.OpenScaleMode)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.DisplayMetrics,
                  type = SettingsItemType.Action(""),
                ),
              )
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.Users,
                  type = SettingsItemType.Action(device?.displayName ?: ""),
                  enabled = isConnected,
                ),
              )
            }
            add(
              SettingsItem(
                title = ScaleDetailsStrings.ScaleName,
                type =
                  SettingsItemType.TextOnly(
                    device?.nickname ?: device?.deviceName ?: "",
                  ),
              ),
            )
            if (isWifiSetup) {
              add(
                SettingsItem(
                  title = ScaleDetailsStrings.UserNumber,
                  type = SettingsItemType.TextOnly("U${device?.userNumber}"),
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
                ),
              )
              if (scaleSetupType == ScaleSetupType.BtWifiR4) {
                add(
                  SettingsItem(
                    title = ScaleDetailsStrings.WiFi,
                    type = SettingsItemType.Action(device?.wifiMac ?: ""),
                    enabled = device?.isWifiConfigured ?: false,
                  ),
                )
                add(
                  SettingsItem(
                    title = ScaleDetailsStrings.WiFiMacAddress,
                    type = SettingsItemType.Action(device?.wifiMac ?: ""),
                    enabled = device?.isWifiConfigured ?: false,
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
                  text = ScaleSetupType.toLabel(device?.scaleType),
                  icon = {
                    AppIcon(
                      id = ScaleDataHelper.scaleTypeIcon(scaleSetupType),
                      contentDescription = ScaleSetupType.toLabel(device?.scaleType),
                      type = AppIconType.Primary,
                    )
                  },
                ),
            ),
            SettingsItem(
              title = ScaleDetailsStrings.Sku,
              type = SettingsItemType.TextOnly(device?.sku ?: ""),
            ),
            SettingsItem(
              title = ScaleDetailsStrings.DatePaired,
              type = SettingsItemType.TextDate(device?.createdAt ?: ""),
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
    val dummyDevice =
        Device(
            id = "1",
            accountId = "1",
            peripheralIdentifier = null,
            nickname = null,
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
            hasServerID = true,
            isConnected = true,
            wifiMac = "greatergoods1",
            isWifiConfigured = true,
            token = null,
            scaleType = "Bluetooth/Wi-Fi",
            bodyComp = true,
            displayName = null,
            displayMetrics = null,
            shouldFactoryReset = false,
            shouldMeasureImpedance = false,
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
        )
    val dummyState = ScaleDetailsState(scale = dummyDevice)
    ScaleDetailsScreenContent(state = dummyState, handleIntent = {})
}
