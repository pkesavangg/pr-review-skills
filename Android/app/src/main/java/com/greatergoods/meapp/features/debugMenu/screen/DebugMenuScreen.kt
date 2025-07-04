package com.greatergoods.meapp.features.debugMenu.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SettingsSection
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.model.SettingColorType
import com.greatergoods.meapp.features.common.model.SettingsItem
import com.greatergoods.meapp.features.common.model.SettingsItemType
import com.greatergoods.meapp.features.debugMenu.model.DebugMenuIntent
import com.greatergoods.meapp.features.debugMenu.strings.DebugMenuStrings
import com.greatergoods.meapp.features.debugMenu.viewmodel.DebugMenuViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

/**
 * Debug menu screen composable. Displays debug information and troubleshooting options.
 * Based on Angular cs-menu.page implementation.
 */
@Composable
fun DebugMenuScreen() {
    val viewModel: DebugMenuViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    BackHandler {
        viewModel.handleIntent(DebugMenuIntent.OnBack)
    }

    DebugMenuContent(
        state = state,
        handleIntent = viewModel::handleIntent,
    )
}

@Composable
private fun DebugMenuContent(
    state: com.greatergoods.meapp.features.debugMenu.model.DebugMenuState,
    handleIntent: (DebugMenuIntent) -> Unit
) {
    AppScaffold(
        title = DebugMenuStrings.PageTitle,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                handleIntent(DebugMenuIntent.OnBack)
            }
        },
        containerColor = colorScheme.secondaryBackground,
        appBarColor = colorScheme.secondaryBackground,
    ) { scaffoldModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.padding(top = spacing.md))
            // Caution Section
            CautionSection()
            Spacer(modifier = Modifier.height(spacing.lg))
            // App Information Section
            AppInformationSection(state)
            Spacer(modifier = Modifier.height(spacing.lg))
            // App Troubleshooting Section
            AppTroubleshootingSection(handleIntent)
            // Scale Troubleshooting Section (only show if scales are available)
            if (state.hasScales) {
                Spacer(modifier = Modifier.height(spacing.lg))
                ScaleTroubleshootingSection(
                    isSendScaleLogEnabled = state.isSendScaleLogEnabled,
                    handleIntent = handleIntent,
                )
            }
            Spacer(modifier = Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun CautionSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = DebugMenuStrings.CautionTitle,
            textType = TextType.Title,
            color = colorScheme.textError,
        )
        Spacer(modifier = Modifier.padding(top = spacing.x3s))
        AppText(
            text = DebugMenuStrings.CautionDescription,
            textType = TextType.Body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppInformationSection(state: com.greatergoods.meapp.features.debugMenu.model.DebugMenuState) {
    SettingsSection(
        title = DebugMenuStrings.SectionHeaders.AppInformation,
        items = listOf(
            SettingsItem(
                title = DebugMenuStrings.AppInfo.AppVersion,
                type = SettingsItemType.TextOnly(state.appVersion),
                onClick = {},
            ),
            SettingsItem(
                title = DebugMenuStrings.AppInfo.NativeModules,
                type = SettingsItemType.TextOnly(if (state.isNative) "Yes" else "No"),
                onClick = {},
            ),
            SettingsItem(
                title = DebugMenuStrings.AppInfo.ComponentVersion,
                type = SettingsItemType.TextOnly(if (state.isAndroid) "Android" else "iOS"),
                onClick = {},
            ),
            SettingsItem(
                title = DebugMenuStrings.AppInfo.Api,
                type = SettingsItemType.TextOnly(state.apiUrl.removePrefix("https://").take(9)),
                onClick = {},
            ),
            SettingsItem(
                title = DebugMenuStrings.AppInfo.Time,
                type = SettingsItemType.TextOnly(state.currentDateTime),
                onClick = {},
            ),
            SettingsItem(
                title = DebugMenuStrings.AppInfo.Timezone,
                type = SettingsItemType.TextOnly("${state.timezoneOffset} ${DebugMenuStrings.AppInfo.Minutes}"),
                onClick = {},
            ),
        ),
    )
}

@Composable
private fun AppTroubleshootingSection(handleIntent: (DebugMenuIntent) -> Unit) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    SettingsSection(
        title = DebugMenuStrings.SectionHeaders.AppTroubleshooting,
        items = listOf(
            SettingsItem(
                title = DebugMenuStrings.Actions.SendLog,
                type = SettingsItemType.None,
                onClick = { handleIntent(DebugMenuIntent.SendLogs) },
            ),
            SettingsItem(
                title = DebugMenuStrings.Actions.Resync,
                type = SettingsItemType.None,
                onClick = { handleIntent(DebugMenuIntent.ResyncEntries) },
            ),
            SettingsItem(
                title = DebugMenuStrings.Actions.ClearData,
                type = SettingsItemType.None,
                color = SettingColorType.Danger,
                onClick = {
                    handleIntent(
                        DebugMenuIntent.ClearAllData {
                            scope.launch {
                                activity?.finish()
                            }
                        },
                    )
                },
            ),
            SettingsItem(
                title = DebugMenuStrings.Actions.ShowAppRate,
                type = SettingsItemType.None,
                onClick = { handleIntent(DebugMenuIntent.SendLogs) },
            ),
        ),
    )
}

@Composable
private fun ScaleTroubleshootingSection(
    isSendScaleLogEnabled: Boolean,
    handleIntent: (DebugMenuIntent) -> Unit
) {
    SettingsSection(
        title = DebugMenuStrings.SectionHeaders.ScaleTroubleshooting,
        items = listOf(
            SettingsItem(
                title = DebugMenuStrings.Actions.SendScaleLog,
                type = SettingsItemType.None,
                onClick = {
                    if (isSendScaleLogEnabled) {
                        handleIntent(DebugMenuIntent.SendScaleLogs)
                    }
                },
            ),
        ),
    )
}

@PreviewTheme
@Composable
private fun DebugMenuScreenPreview() {
    MeAppTheme {
        val previewState = com.greatergoods.meapp.features.debugMenu.model.DebugMenuState(
            appVersion = "1.0.0",
            isNative = true,
            isAndroid = true,
            apiUrl = "https://api.weightgurus.com/v3/",
            currentDateTime = "Dec 15, 2024 at 2:30 PM",
            timezone = "Eastern Standard Time",
            timezoneOffset = "-300",
            hasScales = true,
            isSendScaleLogEnabled = true,
        )
        DebugMenuContent(
            state = previewState,
            handleIntent = {},
        )
    }
}
