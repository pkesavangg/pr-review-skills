package com.greatergoods.meapp.features.scaleMode.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.AppToggle
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonData
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.SegmentButtonSize
import com.greatergoods.meapp.features.common.components.SegmentButtonType
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeIntent
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeState
import com.greatergoods.meapp.features.scaleMode.strings.ScaleModeStrings
import com.greatergoods.meapp.features.scaleMode.viewmodel.ScaleModeViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.token.LocalBorderRadius
import com.greatergoods.meapp.theme.token.LocalSpacing
import com.greatergoods.meapp.theme.token.LocalTypography
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
    val uriHandler = LocalUriHandler.current
    val isAllBodyMetrics = state.isAllBodyMetrics
    val isHeartRateOn = state.isHeartRateOn
    val scale = state.scale
    val modeOptions =
        listOf(
            SegmentButtonData(0, ScaleModeStrings.AllBodyMetrics),
            SegmentButtonData(1, ScaleModeStrings.WeightOnly),
        )
    val selectedMode = if (isAllBodyMetrics) modeOptions[0] else modeOptions[1]

    val typography = LocalTypography.current
    val spacing = LocalSpacing.current
    val borderRadius = LocalBorderRadius.current

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
            if (!isAllBodyMetrics) {
                AppText(
                    text = ScaleModeStrings.Save,
                    textType = TextType.Title,
                    color = MeTheme.colorScheme.primaryAction,
                    modifier =
                        Modifier
                            .padding(end = MeTheme.spacing.md)
                            .clickable { handleIntent(ScaleModeIntent.Save) },
                )
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(MeTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.lg),
        ) {
            // Description with link
            Row(verticalAlignment = Alignment.CenterVertically) {
                val descParts = ScaleModeStrings.BioimpedanceDescription.split(ScaleModeStrings.BioimpedanceTitle)
                AppText(
                    text = descParts[0],
                    textType = TextType.Body,
                )
                AppButton(
                    label = ScaleModeStrings.BioimpedanceTitle,
                    type = ButtonType.TextPrimary,
                    size = ButtonSize.Small,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    onClick = {
                        uriHandler.openUri("https://en.wikipedia.org/wiki/Bioelectrical_impedance_analysis")
                    },
                )
                if (descParts.size > 1) {
                    AppText(
                        text = descParts[1],
                        textType = TextType.Body,
                    )
                }
            }
            // Mode selector
            SegmentButtonGroup(
                data = modeOptions,
                key = SegmentButtonData::label,
                selectedData = selectedMode,
                onSelected = {
                    handleIntent(ScaleModeIntent.SetMode(it.id == 0))
                },
                size = SegmentButtonSize.Large,
                type = SegmentButtonType.Single,
            )
            if (isAllBodyMetrics) {
                // Heart Rate toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    AppIcon(
                        id = AppIcons.Metrics.Pulse,
                        contentDescription = ScaleModeStrings.HeartRate,
                        type = AppIconType.Default,
                    )
                    AppText(
                        text = "${ScaleModeStrings.HeartRate} ${if (isHeartRateOn) ScaleModeStrings.HeartRateOn else ScaleModeStrings.HeartRateOff}",
                        textType = TextType.Body,
                        modifier = Modifier.weight(1f),
                    )
                    AppToggle(
                        checked = isHeartRateOn,
                        onCheckedChange = { handleIntent(ScaleModeIntent.SetHeartRate(it)) },
                    )
                }
                AppText(
                    text = ScaleModeStrings.HeartRateDescription,
                    textType = TextType.SubHeading,
                )
                Surface(
                    color = MeTheme.colorScheme.inverseAction, // TODO: Replace with color token if needed
                    shape = RoundedCornerShape(borderRadius.md),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 0.dp,
                ) {
                    AppText(
                        text = ScaleModeStrings.NoteMedical,
                        textType = TextType.SubHeading,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            } else {
                // Weight Only Mode UI
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AppIcon(
                        id = AppIcons.Default.WeightOnlyMode,
                        contentDescription = "Weight Only Mode",
                        type = AppIconType.Default,
                    )
                    AppText(
                        text = ScaleModeStrings.WeightOnlyIndicator,
                        textType = TextType.SubHeading,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = spacing.lg),
                ) {
                    // Placeholder for scale image with indicator overlay
                    AppIcon(
                        id = AppIcons.Default.ScalePlaceholder,
                        contentDescription = "Scale",
                        modifier = Modifier.size(180.dp),
                        type = AppIconType.Default,
                    )
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .background(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(borderRadius.pill),
                                ).padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppText(
                            text = "0.0 lb",
                            textType = TextType.ListTitle1,
                            color = Color.White,
                        )
                        AppText(
                            text = ScaleModeStrings.BodyMetricsOff,
                            textType = TextType.SubHeading,
                            color = Color.White,
                        )
                    }
                }
                Surface(
                    color = MeTheme.colorScheme.inverseAction, // TODO: Replace with color token if needed
                    shape = RoundedCornerShape(borderRadius.md),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 0.dp,
                ) {
                    AppText(
                        text = ScaleModeStrings.NoteOtherUsers,
                        textType = TextType.SubHeading,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun ScaleModeScreenPreview() {
    val dummyDevice =
        Device(
            id = "1",
            accountId = "1",
            peripheralIdentifier = null,
            nickname = "My Smart Scale",
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
            shouldMeasureImpedance = true,
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
    val dummyState =
        ScaleModeState(
            scale = dummyDevice,
            isAllBodyMetrics = true,
            isHeartRateOn = false,
        )
    ScaleModeScreenContent(state = dummyState, handleIntent = {})
}
