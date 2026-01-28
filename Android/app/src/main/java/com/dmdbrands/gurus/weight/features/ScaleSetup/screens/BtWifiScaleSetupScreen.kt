package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.ScaleCustomization.screens.CustomizeScaleSettings
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.WifiPasswordForm
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.WifiSelection
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LoaderIconType
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.BtWifiScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.ScaleUsers.components.ScaleUserList
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.formatTimestamp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser

@Composable
fun BtWifiScaleSetupScreen(
  sku: String,
  initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  broadcastId: String? = null,
  userList: List<GGBTUser>? = null
) {
  val viewModel: BtWifiScaleSetupViewModel =
    hiltViewModel<BtWifiScaleSetupViewModel, BtWifiScaleSetupViewModel.Factory> { factory ->
      factory.create(sku, broadcastId, initialStep, userList)
    }
  val state by viewModel.state.collectAsState()
  BtWifiScaleSetupScreenContent(
    state = state,
    initialStep == BtWifiSetupStep.GATHERING_NETWORK,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BtWifiScaleSetupScreenContent(
  state: BtWifiScaleSetupState,
  isFromWiFiSetup: Boolean = false,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.steps.size }
  val isAnimating = remember { mutableStateOf(false) }

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.currentStep) {
    if (!isAnimating.value && pagerState.currentPage != state.currentStepIndex) {
      isAnimating.value = true
      try {
        pagerState.scrollToPage(state.currentStepIndex)
      } finally {
        withFrameNanos { }
        isAnimating.value = false
      }
    }
  }

  val isNextButtonEnabledForStep: Boolean =
    when (state.currentStep) {
      BtWifiSetupStep.SCALE_INFO ->
        true

      BtWifiSetupStep.DUPLICATES_FOUND ->
        // Only validate for duplicates in the DUPLICATES_FOUND step
        state.usernameForm.username.isValueValid() &&
          !state.usernameForm.username.value.equals(state.duplicateUser?.name, ignoreCase = true)

      BtWifiSetupStep.CONNECTING_WIFI ->
        state.wifiPasswordForm.ssid.isValueValid() && state.wifiPasswordForm.password.isValueValid()

      BtWifiSetupStep.WIFI_PASSWORD -> {
        // For WIFI_PASSWORD step, check if password is valid
        // If it's a no-password network, only SSID needs to be valid
        // If it requires password, both SSID and password must be valid
        val isSSIDValid = state.wifiPasswordForm.ssid.isValueValid()
        val isPasswordValid = if (state.wifiPasswordForm.noPasswordNetwork.value) {
          true // No password required
        } else {
          state.wifiPasswordForm.password.isValueValid() // Password required and must be valid
        }
        isSSIDValid && isPasswordValid
      }

      BtWifiSetupStep.PERMISSIONS ->
        AppPermissionsHelper.areRequiredPermissionsEnabled(state.permissions, state.sku)

      BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
        // For WiFi list step, enable Next button if WiFi is already connected
        // or if user has selected a network (handled by canProceedToNext)
        !state.connectedSSID.isNullOrEmpty()
      }

      BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
        // For customization step, only check if username is valid (no duplicate validation)
        state.usernameForm.username.isValueValid()
      }

      else -> true

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
      leadingContent = when {
        state.currentStep == BtWifiSetupStep.SCALE_INFO ||
          state.currentStep == BtWifiSetupStep.PERMISSIONS ||
          state.currentStep == BtWifiSetupStep.DUPLICATES_FOUND ||
          state.currentStep == BtWifiSetupStep.WIFI_PASSWORD ||
          (state.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST && !state.connectedSSID.isNullOrEmpty() && state.initialStep != BtWifiSetupStep.GATHERING_NETWORK) -> {
          {
            AppButton(
              type = ButtonType.TextPrimary,
              label = ScaleSetupStrings.backButton,
              size = ButtonSize.Small,
              enabled = !state.isFirstStep && state.currentStep != BtWifiSetupStep.DUPLICATES_FOUND && state.currentStep != BtWifiSetupStep.AVAILABLE_WIFI_LIST,
              onClick = { onIntent(BtWifiScaleSetupIntent.Back) },
            )
          }
        }

        else -> null
      },
      middleContent = when {
        state.currentStep == BtWifiSetupStep.SETUP_FINISHED -> {
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

        state.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST && state.connectedSSID.isNullOrEmpty() && state.initialStep != BtWifiSetupStep.GATHERING_NETWORK
          -> {
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
      trailingContent = when {
        state.currentStep == BtWifiSetupStep.SCALE_INFO ||
          state.currentStep == BtWifiSetupStep.PERMISSIONS ||
          state.currentStep == BtWifiSetupStep.DUPLICATES_FOUND ||
          (state.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST && !state.connectedSSID.isNullOrEmpty() && state.initialStep != BtWifiSetupStep.GATHERING_NETWORK) ||
          state.currentStep == BtWifiSetupStep.WIFI_PASSWORD -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = when (state.currentStep) {
                BtWifiSetupStep.WIFI_PASSWORD -> ScaleSetupStrings.SetupButtons.Connect
                else -> state.nextButtonText
              },
              size = ButtonSize.Small,
              enabled = !state.isLoading && isNextButtonEnabledForStep,
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

        when (state.currentStep) {
          BtWifiSetupStep.SCALE_INFO -> {
            ScaleInfo(sku = state.sku, setupType = ScaleSetupType.BtWifiR4)
          }

          BtWifiSetupStep.PERMISSIONS -> {
            ScalePermissions(
              sku = state.sku,
              permissions = state.permissions,
              onRequestPermission = { onIntent(BtWifiScaleSetupIntent.RequestPermission(it)) },
            )
          }

          BtWifiSetupStep.WAKEUP -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.WakeupScale.Title(state.currentStepConnectionState),
              subtitle = BtWifiScaleSetupStrings.WakeupScale.Subtitle(state.currentStepConnectionState),
              errorCode = state.errorCode,
              scaleImageSku = if (state.currentStepConnectionState is ConnectionState.Failed)
                state.sku else null,
              showIndicationOnly = state.currentStepConnectionState !is ConnectionState.Failed,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
              } else null,
            )
          }

          BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.ConnectingBluetooth.Title(state.currentStepConnectionState),
              scaleImageSku = state.sku,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
              } else null,
            )
          }

          BtWifiSetupStep.DUPLICATES_FOUND -> {
            SetupForm(
              formControl = state.usernameForm.username,
              title = BtWifiScaleSetupStrings.DuplicateUser.Title,
              subtitle = BtWifiScaleSetupStrings.DuplicateUser.Subtitle,
              label = BtWifiScaleSetupStrings.DuplicateUser.UsernameLabel,
              supportingImage = AppIcons.Setup.UserNameScale,
              supportingButtonLabel = BtWifiScaleSetupStrings.DuplicateUser.RestoreAccountButton,
              supportText = BtWifiScaleSetupStrings.DuplicateUser
                .LastActive(state.duplicateUser?.lastActive?.formatTimestamp()).lowercase(),
              onSupportingButtonClick = {
                onIntent(BtWifiScaleSetupIntent.ShowRestoreAccountAlert)
              },
              userList = state.userList,
            )
          }

          BtWifiSetupStep.USER_LIMIT_REACHED -> {
            ScaleUserList(
              title = BtWifiScaleSetupStrings.UserList.Title,
              subtitle = BtWifiScaleSetupStrings.UserList.Subtitle,
              userList = state.userList,
              onDeleteUser = {
                onIntent(BtWifiScaleSetupIntent.DeleteUser(it))
              },
              modifier = Modifier.padding(
                horizontal = spacing.sm, vertical = spacing.md,
              ),
            )
          }

          BtWifiSetupStep.GATHERING_NETWORK -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.GatheringNetwork.Title(state.currentStepConnectionState),
              secondaryButtonText = ScaleSetupStrings.SetupButtons.SetupWifiLater,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.Skip) }
              } else null,
              indicatorIcon = LoaderIconType.Wifi,
              showIndicationOnly = state.currentStepConnectionState is ConnectionState.Loading,
              scaleImageSku = if(state.currentStepConnectionState is ConnectionState.Failed) "0412" else null,
            )
          }

          BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
            WifiSelection(
              wifiList = state.wifiList,
              title = BtWifiScaleSetupStrings.WifiList.Title(!state.connectedSSID.isNullOrEmpty()),
              subtitle = BtWifiScaleSetupStrings.WifiList.Subtitle(!state.connectedSSID.isNullOrEmpty()),
              configuredSSID = state.connectedSSID,
              onSelect = { selectedSSID ->
                // Check if the selected network is already connected
                if (selectedSSID != state.connectedSSID) {
                  // New network selected, go to password step
                  state.wifiPasswordForm.ssid.onValueChange(selectedSSID)
                  onIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.WIFI_PASSWORD))
                }
              },
              onRefresh = {
                onIntent(BtWifiScaleSetupIntent.RefreshNetworks)
              },
            )
          }

          BtWifiSetupStep.WIFI_PASSWORD -> {
            WifiPasswordForm(
              state = state,
              onIntent = onIntent,
            )
          }

          BtWifiSetupStep.CONNECTING_WIFI -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.ConnectingWifi.Title(state.currentStepConnectionState),
              scaleImageSku = state.sku,
              errorCode = if (state.currentStepConnectionState is ConnectionState.Failed)
                state.errorCode
              else null,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
              } else null,
              indicatorIcon = LoaderIconType.Wifi,
            )
          }

          BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
            CustomizeScaleSettings(
              title = BtWifiScaleSetupStrings.CustomizeSettings.Title,
              subtitle = BtWifiScaleSetupStrings.CustomizeSettings.Subtitle,
              state = state,
              onIntent = onIntent,
              userList = state.userList,
            )
          }

          BtWifiSetupStep.UPDATE_SETTINGS -> {
            ScaleSetupLoader(
              connectionState = state.currentStepConnectionState,
              title = BtWifiScaleSetupStrings.UpdateSettings.Title(state.currentStepConnectionState),
              showIndicationOnly = true,
              indicatorIcon = LoaderIconType.Measurement,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
              } else null,
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
              indicatorIcon = LoaderIconType.Measurement,
              primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.TryAgain) }
              } else null,
              secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
                { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
              } else null,
            )
          }

          BtWifiSetupStep.SETUP_FINISHED -> {
            ScaleSetupLoader(
              title = BtWifiScaleSetupStrings.ScaleConnected.Title,
              subtitle = BtWifiScaleSetupStrings.ScaleConnected.Subtitle,
              setupImage = AppIcons.Setup.Accuchecked,
              contentButtonText = BtWifiScaleSetupStrings.ScaleConnected.WhatsThisButton,
              contentButtonClick = { onIntent(BtWifiScaleSetupIntent.OpenAccucheckModal) },
            )
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
