package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ErrorContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SelectButton
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.WifiItem
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.WifiScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WifiMacAddress
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonDisplayValue
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import kotlinx.coroutines.delay

@Composable
fun WifiScaleSetupScreen(
  sku: String,
  wifiSetupType: String = "first",
  scaleInfo: ScaleInfo? = null,
  viewModel: WifiScaleSetupViewModel =
    hiltViewModel<WifiScaleSetupViewModel, WifiScaleSetupViewModel.Factory> { factory ->
      factory.create(sku, wifiSetupType, scaleInfo)
    },
) {

  val lifecycleOwner = LocalLifecycleOwner.current
  // Observe lifecycle only once
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.onResume(lifecycleOwner)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }
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
  LaunchedEffect(state.currentStep) {
    if (state.currentStep == WifiScaleSetupStep.SWITCH_WIFI) {
      onIntent(WifiScaleSetupIntent.CheckScaleWifiConnection)
    }
  }

  val isNextButtonEnabledForStep: Boolean =
    when (state.currentStep) {
      WifiScaleSetupStep.SCALE_INFO ->
        true

      WifiScaleSetupStep.PERMISSIONS ->
        if (state.isGetMACSetup) {
          // MAC setup flow - check permissions only
          AppPermissionsHelper.areRequiredPermissionsEnabled(state.permissions, state.sku)
        } else {
          // Normal flow
          AppPermissionsHelper.areRequiredPermissionsEnabled(state.permissions, state.sku)
        }

      WifiScaleSetupStep.WIFI_PASSWORD ->
        if (state.permissionsSkipped) {
          // Permissions skipped flow - check form validation
          state.wifiPasswordForm.ssid.isValueValid() && state.wifiPasswordForm.password.isValueValid()
        } else {
          // Normal flow - check form validation
          state.wifiPasswordForm.ssid.isValueValid() && state.wifiPasswordForm.password.isValueValid()
        }

      WifiScaleSetupStep.SELECT_USER ->
        // Normal flow only - check if user is selected
        state.selectedUser != null

      WifiScaleSetupStep.ACTIVATE_SCALE ->
        // All flows - can proceed
        state.canProceedToNext

      WifiScaleSetupStep.WIFI_MODE ->
        if (state.isGetMACSetup) {
          // MAC setup flow - check if AP mode is selected
          state.selectedWifiMode == "apmode"
        } else {
          // Normal flow - check if mode is selected
          state.canProceedToNext
        }

      WifiScaleSetupStep.SWITCH_WIFI ->
        if (state.isGetMACSetup) {
          // MAC setup flow - check if connected to scale WiFi
          state.scaleNetworkForm.ssid.value.isNotEmpty()
        } else {
          // Normal flow - check if connected to scale WiFi
          state.canProceedToNext
        }

      WifiScaleSetupStep.MAC_ADDRESS ->
        if (state.isGetMACSetup) {
          // MAC setup flow - check if not showing error and connected to scale WiFi
          !state.showError && state.wifiStatus?.ssid?.contains("gg_SmartScale") == true
        } else {
          // Normal flow - check if not showing error
          !state.showError
        }

      WifiScaleSetupStep.SCALE_CONNECTED ->
        // Normal flow only - can proceed
        state.canProceedToNext

      WifiScaleSetupStep.STEP_ON ->
        // Normal flow only - can proceed
        state.canProceedToNext

      WifiScaleSetupStep.SETUP_FINISHED ->
        // All flows - can proceed
        state.canProceedToNext

      else -> state.canProceedToNext
    }
  val showSkipButton: Boolean =
    when (state.currentStep) {
      WifiScaleSetupStep.PERMISSIONS ->
        // Hide skip button for MAC setup, show for normal flow
        !state.isGetMACSetup

      else -> false
    }
  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(WifiScaleSetupIntent.ExitSetup(isSetupFinished = false)) },
    onHelp = { onIntent(WifiScaleSetupIntent.OpenHelp()) },
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
        // Show skip button only on permissions step and NOT in MAC setup mode
        if (showSkipButton) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.SetupButtons.Skip,
            size = ButtonSize.Small,
            enabled = !state.isLoading,
            onClick = { onIntent(WifiScaleSetupIntent.Skip) },
          )
        }
      },
      trailingContent = when (state.currentStep) {
        WifiScaleSetupStep.SCALE_INFO,
        WifiScaleSetupStep.PERMISSIONS,
        WifiScaleSetupStep.WIFI_PASSWORD,
        WifiScaleSetupStep.SELECT_USER,
        WifiScaleSetupStep.WIFI_MODE,
        WifiScaleSetupStep.ACTIVATE_SCALE,
        WifiScaleSetupStep.MAC_ADDRESS,
        WifiScaleSetupStep.ERROR_GUIDE,
        WifiScaleSetupStep.SCALE_COUNTS,
        WifiScaleSetupStep.SETUP_FINISHED,
        WifiScaleSetupStep.SWITCH_WIFI,
        WifiScaleSetupStep.STEP_ON,
        WifiScaleSetupStep.ERROR_CODE_SELECTED,
        WifiScaleSetupStep.TROUBLE_SHOOTING -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = if (state.isLastStep) ScaleSetupStrings.FinishButton else state.nextButtonText,
              size = ButtonSize.Small,
              enabled = !state.isLoading && isNextButtonEnabledForStep,
              onClick = {
                focusManager.clearFocus()
                if (state.isLastStep) {
                  onIntent(WifiScaleSetupIntent.ExitSetup(isSetupFinished = true))
                } else {
                  onIntent(WifiScaleSetupIntent.Next)
                }
              },
            )
          }
        }

        else -> null
      },
      pageContent = { step ->
        when (step) {
          WifiScaleSetupStep.SCALE_INFO -> {
            com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo(
              sku = state.sku,
              buttonText = ScaleSetupStrings.ScaleInfo.WifiScaleButtonText,
              onButtonClick = { onIntent(WifiScaleSetupIntent.OnGetScaleMacAddress()) },
            )
          }

          WifiScaleSetupStep.PERMISSIONS -> {
            ScalePermissions(
              sku = state.sku,
              permissions = state.permissions,
              onRequestPermission = { permissionType ->
                onIntent(WifiScaleSetupIntent.RequestPermission(permissionType))
              },
            )
          }

          WifiScaleSetupStep.WIFI_PASSWORD -> {
            SetupForm(
              wifiNameFormControl = state.wifiPasswordForm.ssid,
              formControl = state.wifiPasswordForm.password,
              title = WifiScaleSetupStrings.NetworkFormSlide.Title,
              subtitle = WifiScaleSetupStrings.NetworkFormSlide.Subtitle,
              label = WifiScaleSetupStrings.NetworkFormSlide.Password,
              secondaryLabel = WifiScaleSetupStrings.NetworkFormSlide.NetworkName,
              subtitleAnnotatedText = state.wifiPasswordForm.ssid.value,
              isWifiConnected = false,
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

          WifiScaleSetupStep.SELECT_USER -> {
            val userNumbers = (1..8).toList()
            val userButtons =
              SelectButtonHelper.createUserNumberButtons(userNumbers, selectedNumber = state.selectedUser)
            SelectButton(
              title = WifiScaleSetupStrings.ChooseUser.Title,
              subtitle = WifiScaleSetupStrings.ChooseUser.Message,
              selectButtonItems = userButtons,
              isSelectable = true,
              onItemSelected = { value ->
                onIntent(WifiScaleSetupIntent.SelectUser(value.toInt()))
                onIntent(WifiScaleSetupIntent.SetUserNumber(value.toInt()))
              },
            )
          }

          WifiScaleSetupStep.ACTIVATE_SCALE -> {
            SetupContent(
              title = WifiScaleSetupStrings.ActivateScaleSlide.Title,
              subtitle = WifiScaleSetupStrings.ActivateScaleSlide.Message,
              isGifImage = true,
              supportingImage = AppIcons.Setup.WifiPair,
            )
          }

          WifiScaleSetupStep.WIFI_MODE -> {
            // In MAC setup mode, show only AP Mode button and require user selection
            val wifiButtons = if (state.isGetMACSetup || state.permissionsSkipped) {
              listOf(
                SelectButtonItem(
                  id = "wifi_ap_mode",
                  displayValue = SelectButtonDisplayValue.Image(AppIcons.Setup.WifiAPMode),
                  emitValue = "apmode",
                  isSelected = state.selectedWifiMode == "apmode", // Use actual selection state
                ),
              )
            } else {
              SelectButtonHelper.createWifiModeButtons(selectedMode = state.selectedWifiMode)
            }

            SelectButton(
              title = WifiScaleSetupStrings.WifiMode.Title,
              subtitle = if (state.isGetMACSetup) {
                // Add subtitle for MAC setup to guide user
                "Please select AP mode to continue"
              } else {
                null
              },
              selectButtonItems = wifiButtons,
              isSelectable = true, // Always allow selection
              onItemSelected = { value ->
                onIntent(WifiScaleSetupIntent.SelectWifiMode(wifiMode = value))
              },
              noteMessage = if (state.isApMode) WifiScaleSetupStrings.WifiMode.ApNote else WifiScaleSetupStrings.WifiMode.CommonNote,
              supportingButtonLabel = WifiScaleSetupStrings.Note.NavigateToErrorSlide,
              onSupportingButtonClick = {
                // Navigate to error guide step
                onIntent(WifiScaleSetupIntent.NavigateToErrorGuide())
              },
            )
          }

          WifiScaleSetupStep.ERROR_GUIDE -> {
            // Show error selection buttons when no error code is selected
            val errorButtons =
              SelectButtonHelper.createDefaultErrorCodeButtons(selectedErrorCode = state.selectedErrorCode)
            SelectButton(
              title = WifiScaleSetupStrings.Error.Title,
              subtitle = WifiScaleSetupStrings.Error.Message,
              selectButtonItems = errorButtons,
              isSelectable = true,
              onItemSelected = { value ->
                onIntent(WifiScaleSetupIntent.SelectErrorCode(value))
              },
              supportingButtonLabel = ScaleSetupStrings.SetupButtons.SomethingElse,
              onSupportingButtonClick = {
                // Navigate to trouble shooting step
                onIntent(WifiScaleSetupIntent.NavigateToTroubleShooting())
              },
            )
          }

          WifiScaleSetupStep.TROUBLE_SHOOTING -> {
            SetupContent(
              title = WifiScaleSetupStrings.TroubleshootingSlide.Title,
              subtitle = WifiScaleSetupStrings.TroubleshootingSlide.Message,
            )
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
            ErrorContent(
              errorCode = state.selectedErrorCode.toString(),
            )
          }

          WifiScaleSetupStep.SCALE_COUNTS -> {
            SetupContent(
              title = WifiScaleSetupStrings.ScaleCount.Title,
              subtitle = WifiScaleSetupStrings.ScaleCount.Message,
              isGifImage = true,
              supportingImage = AppIcons.Setup.WifiCountOn,
            )
          }

          WifiScaleSetupStep.SWITCH_WIFI -> {
            SetupContent(
              title = WifiScaleSetupStrings.SwitchWifi.Title,
              subtitle = WifiScaleSetupStrings.SwitchWifi.Message.format(state.scaleWifiSsid),
              content = {
                if (state.permissionsSkipped) {
                  AppButton(
                    onClick = {
                      onIntent(WifiScaleSetupIntent.GoToWifiSettings)
                    },
                    label = "Go to wi-fi settings",
                    type = ButtonType.PrimaryFilled,
                    size = ButtonSize.Large,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                } else {
                  WifiItem(
                    borderRadius = borderRadius.sm,
                    ssid = if (!state.scaleNetworkForm.ssid.value.isEmpty()) {
                      state.scaleNetworkForm.ssid.value
                    } else {
                      WifiScaleSetupStrings.SwitchWifi.ChangeNetwork
                    },
                    isConfigured = !state.scaleNetworkForm.ssid.value.isEmpty(),
                    index = 0,
                    total = 1,
                    onClick = {
                      if (state.scaleNetworkForm.ssid.value.isEmpty()) {
                        onIntent(WifiScaleSetupIntent.GoToWifiSettings)
                      }
                    },
                  )
                }

              },
            )
          }

          WifiScaleSetupStep.MAC_ADDRESS -> {
            // Show MAC address component for both AP mode and MAC setup mode
            WifiMacAddress(
              title = WifiScaleSetupStrings.SetupFinished.MacTitle,
              macAddress = state.macAddress,
              onCopyMacAddress = { isSuccess ->
                if (isSuccess) {
                  onIntent(WifiScaleSetupIntent.OnCopyMacAddress(state.macAddress))
                }
              },
            )
          }

          WifiScaleSetupStep.STEP_ON -> {
            SetupContent(
              title = WifiScaleSetupStrings.StepOn.Title,
              subtitle = WifiScaleSetupStrings.StepOn.Message,
              isGifImage = true,
              supportingImage = AppIcons.Setup.WifiStepOn,
            )
          }

          WifiScaleSetupStep.SETUP_FINISHED -> {
            SetupContent(
              title = WifiScaleSetupStrings.SetupFinished.Title,
              subtitle = WifiScaleSetupStrings.SetupFinished.Message,
              supportingImage = AppIcons.Outlined.CheckedCircle,
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
fun WifiScaleSetupPreview() {
  MeAppTheme {
    WifiScaleSetupScreenContent(
      state =
        WifiScaleSetupState(
          sku = "0412",
        ),
      onIntent = {},
      // viewModel = hiltViewModel<WifiScaleSetupViewModel, WifiScaleSetupViewModel.Factory> { factory ->
      //   factory.create("0412", "first", null)
      // },
    )
  }
}
