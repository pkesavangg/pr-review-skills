package com.greatergoods.meapp.features.scaleMode.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.components.AnnotationPosition
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.AppToggle
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
import com.greatergoods.meapp.theme.MeTheme.borderRadius
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
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
    val isAllBodyMetrics = state.isAllBodyMetrics
    val isHeartRateOn = state.isHeartRateOn
    val modeOptions =
        listOf(
            SegmentButtonData(0, ScaleModeStrings.AllBodyMetrics),
            SegmentButtonData(1, ScaleModeStrings.WeightOnly),
        )
    val selectedMode = if (isAllBodyMetrics) modeOptions[0] else modeOptions[1]

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
            if (state.hasModeChanged) {
                AppText(
                    text = ScaleModeStrings.Save,
                    textType = TextType.ListTitle1,
                    color = colorScheme.primaryAction,
                    modifier =
                        Modifier
                            .padding(end = spacing.md)
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
                    .padding(vertical = spacing.md, horizontal = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Description with link
            Column(verticalArrangement = Arrangement.Center) {
                AppText(
                    text = ScaleModeStrings.BioimpedanceDescription.format(ScaleModeStrings.BioimpedanceTitle),
                    annotatedText = ScaleModeStrings.BioimpedanceTitle,
                    annotationPosition = AnnotationPosition.Middle,
                    spanStyle =
                        SpanStyle(
                            color = colorScheme.primaryAction,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    textType = TextType.Body,
                    onAnnotationClick = { handleIntent(ScaleModeIntent.OpenBiaModal) },
                )
            }
            // Mode selector
            SegmentButtonGroup(
                data = modeOptions,
                key = SegmentButtonData::label,
                selectedData = selectedMode,
                onSelected = {
                    handleIntent(ScaleModeIntent.SetMode(it.id == 0, true))
                },
                size = SegmentButtonSize.Large,
                type = SegmentButtonType.Single,
            )
            if (isAllBodyMetrics) {
                // Heart Rate toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AppIcon(
                        id = AppIcons.Metrics.Pulse,
                        contentDescription = ScaleModeStrings.HeartRate(isHeartRateOn),
                        type = AppIconType.Default,
                    )
                    AppText(
                        text = ScaleModeStrings.HeartRate(isHeartRateOn),
                        textType = TextType.Body,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppToggle(
                        checked = isHeartRateOn,
                        onCheckedChange = { handleIntent(ScaleModeIntent.SetHeartRate(it, true)) },
                    )
                }
                AppText(
                    text = ScaleModeStrings.HeartRateDescription,
                    textType = TextType.SubHeading,
                )
                Surface(
                    color = colorScheme.inverseAction,
                    shape = RoundedCornerShape(borderRadius.sm),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 0.dp,
                ) {
                    AppText(
                        text = ScaleModeStrings.NoteMedical,
                        annotatedText = ScaleModeStrings.Note,
                        annotationPosition = AnnotationPosition.Start,
                        spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
                        textType = TextType.SubHeading,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            } else {
                // Weight Only Mode UI
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
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
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Image(
                        painter =
                            painterResource(
                                id = AppIcons.Default.BodyMetricsOffScale,
                            ),
                        contentDescription = null,
                    )
                }
                Surface(
                    color = colorScheme.inverseAction,
                    shape = RoundedCornerShape(borderRadius.sm),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 0.dp,
                ) {
                    AppText(
                        text = ScaleModeStrings.NoteOtherUsers,
                        annotatedText = ScaleModeStrings.Note,
                        annotationPosition = AnnotationPosition.Start,
                        spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
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
