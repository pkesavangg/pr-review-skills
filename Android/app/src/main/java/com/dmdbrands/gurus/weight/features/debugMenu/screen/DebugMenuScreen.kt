package com.dmdbrands.gurus.weight.features.debugMenu.screen

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.CardAlignmentType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuIntent
import com.dmdbrands.gurus.weight.features.debugMenu.strings.DebugMenuStrings
import com.dmdbrands.gurus.weight.features.debugMenu.viewmodel.DebugMenuViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import android.app.Activity

/**
 * Debug menu screen composable. Displays debug information and troubleshooting options.
 * Based on Angular cs-menu.page implementation.
 */
@Composable
fun DebugMenuScreen() {
  val viewModel: DebugMenuViewModel = hiltViewModel()
  val state by viewModel.state.collectAsState()
  val windowSize = LocalWindowInfo.current.containerSize
  val isTablet =
    with(LocalDensity.current) {
      windowSize.width.toDp() > 600.dp
    }

  BackHandler {
    viewModel.handleIntent(DebugMenuIntent.OnBack)
  }
  val cardAlignment = if (isTablet) CardAlignmentType.TopCenter else CardAlignmentType.TopStart
  CompositionLocalProvider(LocalCardAlignment provides cardAlignment){
      DebugMenuContent(
        state = state,
        handleIntent = viewModel::handleIntent,
      )
  }
}

@Composable
private fun DebugMenuContent(
  state: com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuState,
  handleIntent: (DebugMenuIntent) -> Unit
) {
  AppScaffold(
    title = DebugMenuStrings.PageTitle,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        handleIntent(DebugMenuIntent.OnBack)
      }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = spacing.sm),
      verticalArrangement = Arrangement.Top,
    ) {
      Spacer(modifier = Modifier.padding(top = spacing.md))
      // Caution Section
      CautionSection()
      Spacer(modifier = Modifier.height(spacing.lg))
      // App Information Section
      AppInformationSection(state)
      // App Troubleshooting Section
      AppTroubleshootingSection(handleIntent)
      // Scale Troubleshooting Section (only show if scales are available)
      if (state.hasScales) {
        ScaleTroubleshootingSection(
          isSendScaleLogEnabled = state.isSendScaleLogEnabled,
          handleIntent = handleIntent,
        )
      }
      Spacer(modifier = Modifier.height(spacing.lg))
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
private fun AppInformationSection(state: com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuState) {
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
        type = SettingsItemType.TextOnly(state.apiUrl.removePrefix("https:/").take(9)),
        onClick = {},
      ),
      SettingsItem(
        title = DebugMenuStrings.AppInfo.Time,
        type = SettingsItemType.TextOnly(state.currentDateTime),
        onClick = {},
      ),
      SettingsItem(
        title = DebugMenuStrings.AppInfo.Timezone,
        type = SettingsItemType.TextOnly(
          if (state.timezone.isNotEmpty()) {
            "${state.timezoneOffset} ${DebugMenuStrings.AppInfo.Minutes}\n${state.timezone}"
          } else {
            "${state.timezoneOffset} ${DebugMenuStrings.AppInfo.Minutes}"
          }
        ),
        maxLines = 2,
        onClick = {},
      ),
    ),
  )
}

@Composable
private fun AppTroubleshootingSection(handleIntent: (DebugMenuIntent) -> Unit) {
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
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
          AppLog.i(TAG, "Clear data clicked")
          handleIntent(
            DebugMenuIntent.ClearAllData {
              AppLog.i(TAG, "Activity finish callback triggered!")
              try {
                // Properly finish the activity after clearing data
                activity?.finish()
              } catch (e: Exception) {
                AppLog.e(TAG, "Error finishing activity", e)
              }
            },
          )
        },
      ),
      SettingsItem(
        title = DebugMenuStrings.Actions.ShowAppRate,
        type = SettingsItemType.None,
        onClick = {
          handleIntent(DebugMenuIntent.ShowAppReviewWithActivity(context as Activity))
        },
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
        enabled = isSendScaleLogEnabled,
        onClick = { handleIntent(DebugMenuIntent.SendScaleLogs) },
      ),
    ),
  )
}

@PreviewTheme
@Composable
private fun DebugMenuScreenPreview() {
  MeAppTheme {
    val previewState = com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuState(
      appVersion = "5.0.1",
      isNative = true,
      isAndroid = true,
      apiUrl = "https://api.weightgurus.com/v3/",
      currentDateTime = "Dec 15, 2:30 PM",
      timezone = "Asia/Calcutta",
      timezoneOffset = "330",
      hasScales = true,
      isSendScaleLogEnabled = true,
    )
  }
}

private const val TAG = "DebugMenuScreen"
