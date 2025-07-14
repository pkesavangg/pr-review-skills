package com.greatergoods.meapp.features.ScaleSetup.screens

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.ScaleSetup.components.CustomizeScaleSettings
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleInfo
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupHeader
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupLoader
import com.greatergoods.meapp.features.ScaleSetup.components.WifiSelection
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.viewmodel.BtWifiScaleSetupViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.ConnectionState
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun BtWifiScaleSetupScreen(
  sku: String,
  viewModel: BtWifiScaleSetupViewModel =
    hiltViewModel<BtWifiScaleSetupViewModel, BtWifiScaleSetupViewModel.Factory> { factory ->
      factory.create(sku)
    },
) {
  val state by viewModel.state.collectAsState()
  BtWifiScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BtWifiScaleSetupScreenContent(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.steps.size }
  val isAnimating = remember { mutableStateOf(false) }
  BtWifiScaleSetupStrings

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.currentStep) {
    if (!isAnimating.value && pagerState.currentPage != state.currentStepIndex) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(state.currentStepIndex)
      } finally {
        withFrameNanos { }
        isAnimating.value = false
      }
    }
  }
  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(BtWifiScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(BtWifiScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      shouldCenterMiddleContent = true,
      leadingContent = when (state.currentStep) {
        BtWifiSetupStep.SCALE_INFO,
        BtWifiSetupStep.WIFI_PASSWORD -> {
          {
            AppButton(
              type = ButtonType.TextPrimary,
              label = ScaleSetupStrings.backButton,
              size = ButtonSize.Small,
              enabled = !state.isFirstStep,
              onClick = { onIntent(BtWifiScaleSetupIntent.Back) },
            )
          }
        }
        else -> null
      },
      middleContent = when (state.currentStep) {
        BtWifiSetupStep.SCALE_CONNECTED -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = ScaleSetupStrings.FinishButton,
              size = ButtonSize.Small,
              onClick = {
                focusManager.clearFocus()
                onIntent(BtWifiScaleSetupIntent.Next)
              },
            )
          }
        }
        BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
          {
            AppButton(
              type = ButtonType.TextTertiary,
              label = ScaleSetupStrings.SetupButtons.Skip,
              size = ButtonSize.Small,
              onClick = {
                focusManager.clearFocus()
                onIntent(BtWifiScaleSetupIntent.Skip)
              },
            )
          }
        } // No skip button on wakeup step
        else -> null // No skip button for other steps yet
      },
      trailingContent = when (state.currentStep) {
        BtWifiSetupStep.SCALE_INFO,
        BtWifiSetupStep.WIFI_PASSWORD -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = state.nextButtonText,
              size = ButtonSize.Small,
              enabled = !state.isLoading && state.canProceedToNext,
              onClick = {
                focusManager.clearFocus()
                onIntent(BtWifiScaleSetupIntent.Next)
              },
            )
          }
        }
        else -> null
      },
      pageContent = { step ->
        when (step) {
          BtWifiSetupStep.SCALE_INFO -> {
            ScaleInfo(sku = state.sku)
          }
          BtWifiSetupStep.WAKEUP -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.WakeupScale.Title(state.currentStepConnectionState),
              subtitle = BtWifiScaleSetupStrings.WakeupScale.Subtitle(state.currentStepConnectionState),
              errorCode = state.errorCode,
              scaleImageSku = if (state.currentStepConnectionState == ConnectionState.Error)
                state.sku else null,
              showIndicationOnly = state.currentStepConnectionState != ConnectionState.Error,
              primaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
            )
          }
          BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.ConnectingBluetooth.Title(state.currentStepConnectionState),
              scaleImageSku = state.sku,
              primaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
            )
          }
          BtWifiSetupStep.GATHERING_NETWORK -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.GatheringNetwork.Title(state.currentStepConnectionState),
              scaleImageSku = state.sku,
              primaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
            )
          }

          BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
            WifiSelection(
              wifiList = state.wifiList,
              title = BtWifiScaleSetupStrings.WifiList.Title,
              subtitle = BtWifiScaleSetupStrings.WifiList.Subtitle,
              configuredSSID = null,
              onSelect = {
                state.wifiPasswordForm.ssid.onValueChange(it)
                onIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                onIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.WIFI_PASSWORD))
              },
              onRefresh = {
                onIntent(BtWifiScaleSetupIntent.RefreshNetworks)
              },
            )
          }

          BtWifiSetupStep.WIFI_PASSWORD -> {
            SetupForm(
              formControl = state.wifiPasswordForm.password,
              title = BtWifiScaleSetupStrings.WifiPassword.Title,
              label = BtWifiScaleSetupStrings.WifiPassword.PasswordLabel,
              subtitle = BtWifiScaleSetupStrings.WifiPassword.Subtitle,
              subtitleAnnotatedText = state.wifiPasswordForm.ssid.value,
              hasToggle = true,
              toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
              toggleChecked = state.wifiPasswordForm.noPasswordNetwork.value,
              onToggleChanged = {
                state.wifiPasswordForm.noPasswordNetwork.onValueChange(it)
                state.wifiPasswordForm.password.reset()
                onIntent(BtWifiScaleSetupIntent.HandlePasswordNetworkStatus)
              },
            )
          }

          BtWifiSetupStep.CONNECTING_WIFI -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.ConnectingWifi.Title(state.currentStepConnectionState),
              scaleImageSku = state.sku,
              primaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
            )
          }

          BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
            CustomizeScaleSettings(
              title = BtWifiScaleSetupStrings.CustomizeSettings.Title,
              subtitle = BtWifiScaleSetupStrings.CustomizeSettings.Subtitle,
              onSelectSettings = { it },
            )
          }

          BtWifiSetupStep.STEP_ON -> {
            ScaleSetupLoader(
              title = BtWifiScaleSetupStrings.StepOn.Title,
              subtitle = BtWifiScaleSetupStrings.StepOn.Subtitle,
              setupImage = AppIcons.Setup.StepOnGif,
              isGifImage = true,
            )
          }

          BtWifiSetupStep.MEASUREMENT -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.CollectingMeasurement.Title(state.currentStepConnectionState),
              showIndicationOnly = true,
              primaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState == ConnectionState.Error) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
            )
          }
          BtWifiSetupStep.SCALE_CONNECTED -> {
            ScaleSetupLoader(
              title = BtWifiScaleSetupStrings.ScaleConnected.Title,
              subtitle = BtWifiScaleSetupStrings.ScaleConnected.Subtitle,
              setupImage = AppIcons.Setup.Accuchecked,
              contentButtonText = BtWifiScaleSetupStrings.ScaleConnected.WhatsThisButton,
              contentButtonClick = { onIntent(BtWifiScaleSetupIntent.OpenAccucheckModal) },
            )
          }
          else -> {
            // Placeholder for other steps
          }
        }
      },
    )
  }
}

@PreviewTheme()
@Composable
fun BtWifiScaleSetupPreview() {
  MeAppTheme {
    BtWifiScaleSetupScreenContent(
      state =
        BtWifiScaleSetupState(
          sku = "0412",
        ),
      onIntent = {},
    )
  }
}
