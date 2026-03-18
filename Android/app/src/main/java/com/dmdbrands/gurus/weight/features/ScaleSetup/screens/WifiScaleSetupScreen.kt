package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiModes
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.WifiScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WifiMacAddress
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonDisplayValue
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
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
  val state by viewModel.state.collectAsStateWithLifecycle()
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
        // Clear navigation state after animation completes
        onIntent(WifiScaleSetupIntent.ClearNavigationState)
      }
    }
  }

  // Default button content for all steps
  val defaultButtonContent: @Composable () -> Unit = {
    AppButton(
      type = ButtonType.PrimaryFilled,
      label = if (state.isLastStep) ScaleSetupStrings.FinishButton else state.nextButtonText,
      size = ButtonSize.Small,
      enabled = !state.isLoading && !state.isNavigating && state.isNextButtonEnabled,
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

  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(WifiScaleSetupIntent.ExitSetup(isSetupFinished = false)) },
    onHelp = { onIntent(WifiScaleSetupIntent.OpenHelp()) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      shouldCenterMiddleContent = true,
      pagerState = pagerState,
      leadingContent = {
        AppButton(
          type = ButtonType.TextPrimary,
          label = ScaleSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = !state.isFirstStep && !state.isLoading && !state.isNavigating && !state.isLastStep,
          onClick = { onIntent(WifiScaleSetupIntent.Back) },
        )
      },
      middleContent = {
        // Show skip button only on permissions step and NOT in MAC setup mode
        if (!state.isGetMACSetup && state.currentStep == WifiScaleSetupStep.PERMISSIONS) {
          AppButton(
            type = ButtonType.TextTertiary,
            label = ScaleSetupStrings.SetupButtons.Skip,
            size = ButtonSize.Small,
            enabled = !state.isLoading && !state.isNavigating,
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
        WifiScaleSetupStep.SETUP_FINISHED,
        WifiScaleSetupStep.SWITCH_WIFI,
        WifiScaleSetupStep.SCALE_COUNTS,
        WifiScaleSetupStep.STEP_ON,
        WifiScaleSetupStep.ERROR_CODE_SELECTED,
        WifiScaleSetupStep.TROUBLE_SHOOTING -> defaultButtonContent
      },
      pageContent = {
        Crossfade(targetState = state.currentStep) { step ->
          when (step) {
            WifiScaleSetupStep.SCALE_INFO -> {
              com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo(
                sku = state.sku,
                setupType = ScaleSetupType.Wifi,
                buttonText = ScaleSetupStrings.ScaleInfo.WifiScaleButtonText,
                onButtonClick = { onIntent(WifiScaleSetupIntent.OnGetScaleMacAddress()) },
              )
            }

            WifiScaleSetupStep.PERMISSIONS -> {
              ScalePermissions(
                sku = state.sku,
                permissions = state.permissions,
                wifiName = state.wifiStatus?.ssid,
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
                onImeAction = {
                  onIntent(WifiScaleSetupIntent.GoToWifiSettings)
                },
                isWifiConnected = state.wifiPasswordForm.ssid.isValueValid() && !state.permissionsSkipped,
                hasToggle = true,
                toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
                toggleChecked = state.wifiPasswordForm.noPasswordNetwork.value,
                onToggleChanged = { isChecked ->
                  state.wifiPasswordForm.noPasswordNetwork.onValueChange(isChecked)
                  if (isChecked) {
                    state.wifiPasswordForm.password.reset("")
                    // No password network - remove all password validators
                    // Remove validators first (they check against current value)
                    state.wifiPasswordForm.password.removeValidator("required")
                    state.wifiPasswordForm.password.removeValidator("Invalid")
                    state.wifiPasswordForm.password.reset("")
                  } else {
                    state.wifiPasswordForm.password.addValidator(FormValidations.required())
                    // Reset to clear any stale errors before validation runs
                    state.wifiPasswordForm.password.reset("")
                  }
                },
                noteMessage = WifiScaleSetupStrings.Note.NetworkMessage,
                inputType = AppInputType.PASSWORD,
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
                sku = state.sku,
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
                supportingImage = if (state.sku == "0384") AppIcons.Setup.WifiPair else AppIcons.Setup.WifiPair0396,
              )
            }

            WifiScaleSetupStep.WIFI_MODE -> {
              // Show different buttons based on the flow type
              val wifiButtons = when {
                state.isGetMACSetup || state.permissionsSkipped -> {
                  val image = if (state.sku == "0384") AppIcons.Setup.WifiAPModeFilled0384 else AppIcons.Setup.WifiAPModeSelected
                  // Permission skipped flow: Only AP mode available
                  listOf(
                    SelectButtonItem(
                      id = "wifi_ap_mode",
                      displayValue = SelectButtonDisplayValue.Image(image),
                      emitValue = WifiModes.AP_MODE.value,
                      isSelected = true,
                    ),
                  )
                }

                else -> {
                  // Normal flow: Both modes available
                  SelectButtonHelper.createWifiModeButtons(
                    selectedMode = state.selectedWifiMode,
                    state.sku,
                    state.selectedUser,
                  )
                }
              }

              SelectButton(
                title =  when {
                  state.isGetMACSetup || state.permissionsSkipped -> {
                    WifiScaleSetupStrings.WifiMode.APTitle
                  }
                  else -> {
                    WifiScaleSetupStrings.WifiMode.Title
                  }
                },
                subtitle = when {
                  state.isGetMACSetup || state.permissionsSkipped -> {
                    "Once your scale displays that is in AP mode, tap NEXT"
                  }

                  else -> {
                    null
                  }
                },
                selectButtonItems = wifiButtons,
                isSelectable = true,
                sku = state.sku,
                onItemSelected = { value ->
                  if(state.permissionsSkipped || state.isGetMACSetup){
                    onIntent(WifiScaleSetupIntent.SelectWifiMode(wifiMode = WifiModes.AP_MODE.value))
                  }
                  else
                  onIntent(WifiScaleSetupIntent.SelectWifiMode(wifiMode = value))
                },
                noteMessage = if (state.permissionsSkipped || state.isGetMACSetup) WifiScaleSetupStrings.WifiMode.ApNote else WifiScaleSetupStrings.WifiMode.CommonNote,
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
                sku = state.sku,
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
                      label = WifiScaleSetupStrings.SwitchWifi.goToWifiSettings,
                      type = ButtonType.PrimaryFilled,
                      size = ButtonSize.Large,
                      enabled = true,
                      modifier = Modifier.fillMaxWidth(),
                    )
                  } else {
                    WifiItem(
                      borderRadius = borderRadius.sm,
                      isWifiIcon = !state.scaleNetworkForm.ssid.value.isEmpty(),
                      ssid = if (!state.scaleNetworkForm.ssid.value.isEmpty()) {
                        state.scaleNetworkForm.ssid.value
                      } else {
                        WifiScaleSetupStrings.SwitchWifi.ChangeNetwork
                      },
                      isConfigured = false,
                      index = 0,
                      total = 1,
                      onClick = {
                        onIntent(WifiScaleSetupIntent.GoToWifiSettings)
                      },
                      modifier = Modifier.size(spacing.md)
                    )
                  }

                },
              )
            }

            WifiScaleSetupStep.SCALE_COUNTS -> {
              SetupContent(
                title = WifiScaleSetupStrings.ScaleCount.Title,
                isGifImage = false,
                supportingImage = AppIcons.Setup.wifiAPModeStepOn,
                noteMessage = if (state.permissionsSkipped || state.isGetMACSetup) WifiScaleSetupStrings.WifiMode.ApNote else WifiScaleSetupStrings.WifiMode.CommonNote,
                supportingButtonLabel = WifiScaleSetupStrings.Note.NavigateToErrorSlide,
                onSupportingButtonClick = {
                  // Navigate to error guide step
                  onIntent(WifiScaleSetupIntent.NavigateToErrorGuide())
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
                setupFinished = true,
              )
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
      // viewModel = hiltViewModel<WifiScaleSetupViewModel, WifiScaleSetupViewModel.Factory> { factory ->
      //   factory.create("0412", "first", null)
      // },
    )
  }
}
