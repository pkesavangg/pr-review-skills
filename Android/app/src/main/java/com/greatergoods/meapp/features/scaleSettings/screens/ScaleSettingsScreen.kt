package com.greatergoods.meapp.features.scaleSettings.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SettingsSection
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.ScaleUtility
import com.greatergoods.meapp.features.common.model.SettingColorType
import com.greatergoods.meapp.features.common.model.SettingsItem
import com.greatergoods.meapp.features.common.model.SettingsItemType
import com.greatergoods.meapp.features.scaleSettings.reducer.ScaleSettingsIntent
import com.greatergoods.meapp.features.scaleSettings.reducer.ScaleSettingsState
import com.greatergoods.meapp.features.scaleSettings.strings.ScaleSettingsStrings
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * ScaleSettings screen composable. Displays scale settings and handles user interactions.
 */
@Composable
fun ScaleSettingsScreen(broadcastId: String) {
    val viewModel: ScaleSettingsViewModel =
        hiltViewModel<ScaleSettingsViewModel, ScaleSettingsViewModel.Factory>(
            creationCallback = { factory ->
                factory.create(broadcastId)
            },
        )
    val state by viewModel.state.collectAsState()

    BackHandler {
        viewModel.handleIntent(ScaleSettingsIntent.Back)
    }

    ScaleSettingsScreenContent(state, viewModel::handleIntent)
}

@Composable
fun ScaleSettingsScreenContent(
    state: ScaleSettingsState,
    handleIntent: (ScaleSettingsIntent) -> Unit,
) {
    val device = state.scale
    MeAppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.secondaryBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.md, horizontal = spacing.sm),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md, bottom = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = AppIcons.Default.Close),
                    contentDescription = ScaleSettingsStrings.Close,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clickable { handleIntent(ScaleSettingsIntent.Back) },
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                AppText(
                    text = device?.deviceName ?: "",
                    textType = TextType.Title,
                    modifier = Modifier.weight(1f),
                )
            }
            // Scale Image
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter =
                        painterResource(
                            id =
                                device?.sku?.let { ScaleUtility.scaleImageResource(it) }
                                    ?: AppIcons.Default.ScalePlaceholder,
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                )
            }
            // Settings Section
            SettingsSection(
                title = ScaleSettingsStrings.Settings,
                items =
                    listOf(
                        SettingsItem(
                            title = ScaleSettingsStrings.Mode,
                            type = SettingsItemType.TextOnly(ScaleSettingsStrings.AllBodyMetrics),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.DisplayMetrics,
                            type = SettingsItemType.TextOnly(""),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.Users,
                            type = SettingsItemType.TextOnly(device?.displayName ?: ""),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.ScaleName,
                            type = SettingsItemType.TextOnly(device?.nickname ?: device?.deviceName ?: ""),
                        ),
                    ),
            )
            // Connection Section
            SettingsSection(
                title = ScaleSettingsStrings.Connection,
                items =
                    listOf(
                        SettingsItem(
                            title = ScaleSettingsStrings.Bluetooth,
                            type = SettingsItemType.TextOnly(ScaleSettingsStrings.Connected),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.WiFi,
                            type = SettingsItemType.TextOnly(device?.wifiMac ?: ""),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.WiFiMacAddress,
                            type = SettingsItemType.TextOnly(device?.wifiMac ?: ""),
                        ),
                    ),
            )
            // Support Section
            SettingsSection(
                title = ScaleSettingsStrings.Support,
                items =
                    listOf(
                        SettingsItem(
                            title = ScaleSettingsStrings.ScaleType,
                            type = SettingsItemType.TextOnly(device?.scaleType ?: ScaleSettingsStrings.BluetoothWiFi),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.Sku,
                            type = SettingsItemType.TextOnly(device?.sku ?: ""),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.DatePaired,
                            type = SettingsItemType.TextOnly(device?.createdAt ?: ""),
                        ),
                        SettingsItem(
                            title = ScaleSettingsStrings.ProductGuide,
                            type = SettingsItemType.Action(),
                            onClick = { handleIntent(ScaleSettingsIntent.OpenProductGuide) },
                        ),
                    ),
            )
            // Delete Scale Button (danger action, outside cards)
            Spacer(modifier = Modifier.height(spacing.md))
            SettingsSection(
                items =
                    listOf(
                        SettingsItem(
                            title = ScaleSettingsStrings.DeleteScale,
                            type = SettingsItemType.None,
                            color = SettingColorType.Danger,
                            onClick = { handleIntent(ScaleSettingsIntent.DeleteScale) },
                        ),
                    ),
            )
        }
    }
}

@PreviewTheme
@Composable
fun ScaleSettingsScreenPreview() {
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
        )
    val dummyState = ScaleSettingsState(scale = dummyDevice)
    ScaleSettingsScreenContent(state = dummyState, handleIntent = {})
}
