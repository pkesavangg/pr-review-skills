package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.WifiSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.WifiScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WifiMacAddress
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay

@Composable
fun WifiScaleSetupScreen(
  sku: String,
  viewModel: WifiScaleSetupViewModel =
    hiltViewModel<WifiScaleSetupViewModel, WifiScaleSetupViewModel.Factory> { factory ->
      factory.create(sku)
    },
) {
  val state by viewModel.state.collectAsState()
  WifiScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun WifiScaleSetupScreenContent(
  state: WifiScaleSetupState,
  onIntent: (WifiScaleSetupIntent) -> Unit,
) {
  val pagerState = rememberPagerState { state.steps.size }
  val isAnimating = remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  // Sync ViewModel state to Pager state
  LaunchedEffect(state.currentStep) {
    if (!isAnimating.value && pagerState.currentPage != state.currentStepIndex) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(state.currentStepIndex)
      } finally {
        delay(100)
        isAnimating.value = false
      }
    }
  }
  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(WifiScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(WifiScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        AppButton(
          type = ButtonType.TextPrimary,
          label = ScaleSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = !state.isFirstStep,
          onClick = { onIntent(WifiScaleSetupIntent.Back) },
        )
      },
      middleContent = {
        // Skip button can be added here when needed for other steps
      },
      trailingContent = {
        AppButton(
          type = ButtonType.PrimaryFilled,
          label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
          size = ButtonSize.Small,
          enabled = !state.isLoading,
          onClick = {
            focusManager.clearFocus()
            onIntent(WifiScaleSetupIntent.Next)
          },
        )
      },
      pageContent = { step ->
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(MeTheme.spacing.md),
        ) {
          when (step) {
            WifiScaleSetupStep.SCALE_INFO -> {
              ScaleInfo(
                sku = state.sku,
                buttonText = ScaleSetupStrings.ScaleInfo.WifiScaleButtonText,
                onButtonClick = { onIntent(WifiScaleSetupIntent.OnGetScaleMacAddress(true)) },
              )
            }

            WifiScaleSetupStep.SETUP_FINISHED -> {
              if(state.isApMode) {
                WifiMacAddress(
                  title = WifiSetupStrings.setupFinished.MacTitle,
                  macAddress = "",
                  onCopyMacAddress = {
                    onIntent(WifiScaleSetupIntent.OnCopyMacAddress(it))
                  },
                )
              } else {
                SetupContent(
                  title = WifiSetupStrings.setupFinished.Title,
                  subtitle = WifiSetupStrings.setupFinished.Message,
                  setupFinished = true
                )
              }
            WifiScaleSetupStep.PERMISSIONS -> {
              ScalePermissions(
                sku = state.sku,
                permissions = state.permissions,
                onRequestPermission = { },
              )
            }

            WifiScaleSetupStep.WIFI_PASSWORD -> {
              SetupForm(
                formControl = state.wifiPasswordForm.password,
                title = WifiScaleSetupStrings.NetworkFormSlide.Title,
                label = WifiScaleSetupStrings.NetworkFormSlide.Subtitle,
                subtitle = BtWifiScaleSetupStrings.WifiPassword.Subtitle,
                subtitleAnnotatedText = state.wifiPasswordForm.ssid.value,
                hasToggle = true,
                toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
                toggleChecked = state.wifiPasswordForm.noPasswordNetwork.value,
                onToggleChanged = {
                  state.wifiPasswordForm.noPasswordNetwork.onValueChange(it)
                  state.wifiPasswordForm.password.reset()
                },
                noteMessage = WifiScaleSetupStrings.Note.NetworkMessage,
              )
            }
            // TODO: Add other steps as needed
            else -> {
              // Placeholder for other steps
            }
          }
        }
      },
    )
  }
}

@PreviewTheme()
@Composable
fun WifiScaleSetupPreview() {
  MeAppTheme {
    WifiScaleSetupScreenContent(
      state =
        WifiScaleSetupState(
          sku = "0412",
        ),
      onIntent = {},
    )
  }
}
