package com.dmdbrands.gurus.weight.features.DeviceSetup.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.DeviceCustomization.screens.CustomizeDeviceSettings
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceInfoContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DevicePermissions
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupForm
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.WifiPasswordForm
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.WifiSelection
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LoaderIconType
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.BtWifiScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.DeviceUsers.components.DeviceUserList
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
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
  val state by viewModel.state.collectAsStateWithLifecycle()
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
  val currentStep = state.currentStep

  BtWifiStepSync(
    currentStep = currentStep,
    currentStepIndex = state.currentStepIndex,
    pagerState = pagerState,
  )

  val isNextButtonEnabledForStep = isBtWifiNextButtonEnabled(state)

  DeviceSetupHeader(
    sku = state.sku,
    onBack = { onIntent(BtWifiScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(BtWifiScaleSetupIntent.OpenHelp) },
  ) {
    BtWifiSetupPager(
      state = state,
      pagerState = pagerState,
      focusManager = focusManager,
      isNextEnabled = isNextButtonEnabledForStep,
      onIntent = onIntent,
    )
  }
}

// Sync ViewModel state to Pager state
@Composable
private fun BtWifiStepSync(
  currentStep: BtWifiSetupStep,
  currentStepIndex: Int,
  pagerState: PagerState,
) {
  LaunchedEffect(currentStep) {
    val targetPage = currentStep.ordinal
    if (pagerState.currentPage != currentStepIndex) {
      try {
        pagerState.scrollToPage(currentStepIndex)
      }
      catch(e: Exception){
        pagerState.scrollToPage(targetPage)
      }
      finally {
      }
    }
  }
}

private fun isBtWifiNextButtonEnabled(state: BtWifiScaleSetupState): Boolean =
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

private fun btWifiLeadingContent(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
): (@Composable () -> Unit)? = when {
  state.currentStep == BtWifiSetupStep.SCALE_INFO ||
    state.currentStep == BtWifiSetupStep.PERMISSIONS ||
    state.currentStep == BtWifiSetupStep.DUPLICATES_FOUND ||
    state.currentStep == BtWifiSetupStep.WIFI_PASSWORD ||
    (state.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST && !state.connectedSSID.isNullOrEmpty() && state.initialStep != BtWifiSetupStep.GATHERING_NETWORK) -> {
    {
      AppButton(
        type = ButtonType.TextPrimary,
        modifier = Modifier.testTag(TestTags.DeviceSetup.BackButton),
        label = DeviceSetupStrings.backButton,
        size = ButtonSize.Small,
        enabled = !state.isFirstStep && state.currentStep != BtWifiSetupStep.DUPLICATES_FOUND && state.currentStep != BtWifiSetupStep.AVAILABLE_WIFI_LIST,
        onClick = { onIntent(BtWifiScaleSetupIntent.Back) },
      )
    }
  }

  else -> null
}

private fun btWifiMiddleContent(
  state: BtWifiScaleSetupState,
  focusManager: FocusManager,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
): (@Composable () -> Unit)? = when {
  state.currentStep == BtWifiSetupStep.SETUP_FINISHED -> {
    {
      AppButton(
        type = ButtonType.PrimaryFilled,
        modifier = Modifier.testTag(TestTags.DeviceSetup.FinishButton),
        label = DeviceSetupStrings.FinishButton,
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
        modifier = Modifier.testTag(TestTags.DeviceSetup.SkipButton),
        label = DeviceSetupStrings.SetupButtons.Skip,
        size = ButtonSize.Small,
        onClick = {
          focusManager.clearFocus()
          onIntent(BtWifiScaleSetupIntent.Skip)
        },
      )
    }
  } // No skip button on wakeup step
  else -> null // No skip button for other steps yet
}

private fun btWifiTrailingContent(
  state: BtWifiScaleSetupState,
  focusManager: FocusManager,
  isNextEnabled: Boolean,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
): (@Composable () -> Unit)? = when {
  state.currentStep == BtWifiSetupStep.SCALE_INFO ||
    state.currentStep == BtWifiSetupStep.PERMISSIONS ||
    state.currentStep == BtWifiSetupStep.DUPLICATES_FOUND ||
    (state.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST && !state.connectedSSID.isNullOrEmpty() && state.initialStep != BtWifiSetupStep.GATHERING_NETWORK) ||
    state.currentStep == BtWifiSetupStep.WIFI_PASSWORD -> {
    {
      AppButton(
        type = ButtonType.PrimaryFilled,
        modifier = Modifier.testTag(TestTags.DeviceSetup.NextButton),
        label = when (state.currentStep) {
          BtWifiSetupStep.WIFI_PASSWORD -> DeviceSetupStrings.SetupButtons.Connect
          else -> state.nextButtonText
        },
        size = ButtonSize.Small,
        enabled = !state.isLoading && isNextEnabled,
        onClick = {
          focusManager.clearFocus()
          onIntent(BtWifiScaleSetupIntent.Next)
        },
      )
    }
  }

  else -> null
}

@Composable
private fun BtWifiSetupPager(
  state: BtWifiScaleSetupState,
  pagerState: PagerState,
  focusManager: FocusManager,
  isNextEnabled: Boolean,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  HorizontalPagerWithBottomNavigation(
    steps = state.steps,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    pagerState = pagerState,
    shouldCenterMiddleContent = true,
    leadingContent = btWifiLeadingContent(state = state, onIntent = onIntent),
    middleContent = btWifiMiddleContent(state = state, focusManager = focusManager, onIntent = onIntent),
    trailingContent = btWifiTrailingContent(
      state = state,
      focusManager = focusManager,
      isNextEnabled = isNextEnabled,
      onIntent = onIntent,
    ),
    pageContent = { step ->
      BtWifiPageContent(state = state, onIntent = onIntent)
    },
  )
}

@Composable
private fun BtWifiPageContent(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  when (state.currentStep) {
    BtWifiSetupStep.SCALE_INFO ->
      DeviceInfoContent(sku = state.sku, setupType = DeviceSetupType.BtWifiR4)
    BtWifiSetupStep.PERMISSIONS -> BtWifiPermissionsStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.WAKEUP -> BtWifiWakeupStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.CONNECTING_BLUETOOTH -> BtWifiConnectingBluetoothStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.DUPLICATES_FOUND -> BtWifiDuplicatesStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.USER_LIMIT_REACHED -> BtWifiUserLimitStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.GATHERING_NETWORK -> BtWifiGatheringNetworkStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.AVAILABLE_WIFI_LIST -> BtWifiWifiListStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.WIFI_PASSWORD ->
      WifiPasswordForm(state = state, onIntent = onIntent)
    BtWifiSetupStep.CONNECTING_WIFI -> BtWifiConnectingWifiStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.CUSTOMIZE_SETTINGS -> BtWifiCustomizeStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.UPDATE_SETTINGS -> BtWifiUpdateSettingsStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.STEP_ON -> BtWifiStepOnStep()
    BtWifiSetupStep.MEASUREMENT -> BtWifiMeasurementStep(state = state, onIntent = onIntent)
    BtWifiSetupStep.SETUP_FINISHED -> BtWifiFinishedStep(onIntent = onIntent)
  }
}

@Composable
private fun BtWifiPermissionsStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DevicePermissions(
    sku = state.sku,
    permissions = state.permissions,
    onRequestPermission = { onIntent(BtWifiScaleSetupIntent.RequestPermission(it)) },
  )
}

@Composable
private fun BtWifiWakeupStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
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

@Composable
private fun BtWifiConnectingBluetoothStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
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

@Composable
private fun BtWifiDuplicatesStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
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

@Composable
private fun BtWifiUserLimitStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceUserList(
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

@Composable
private fun BtWifiGatheringNetworkStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    connectionState = state.currentStepConnectionState,
    title = BtWifiScaleSetupStrings.GatheringNetwork.Title(state.currentStepConnectionState),
    secondaryButtonText = DeviceSetupStrings.SetupButtons.SetupWifiLater,
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

@Composable
private fun BtWifiWifiListStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
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

@Composable
private fun BtWifiConnectingWifiStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
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

@Composable
private fun BtWifiCustomizeStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  CustomizeDeviceSettings(
    title = BtWifiScaleSetupStrings.CustomizeSettings.Title,
    subtitle = BtWifiScaleSetupStrings.CustomizeSettings.Subtitle,
    state = state,
    onIntent = onIntent,
    userList = state.userList,
  )
}

@Composable
private fun BtWifiUpdateSettingsStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    connectionState = state.currentStepConnectionState,
    title = BtWifiScaleSetupStrings.UpdateSettings.Title(state.currentStepConnectionState),
    showIndicationOnly = true,
    indicatorIcon = if(state.currentStepConnectionState is ConnectionState.Failed)  LoaderIconType.Error else LoaderIconType.Measurement,
    primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
      { onIntent(BtWifiScaleSetupIntent.TryAgain) }
    } else null,
    secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
      { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
    } else null,
  )
}

@Composable
private fun BtWifiStepOnStep() {
  DeviceSetupLoader(
    title = BtWifiScaleSetupStrings.StepOn.Title,
    subtitle = BtWifiScaleSetupStrings.StepOn.Subtitle,
    setupImage = AppIcons.Setup.StepOnGif,
    isGifImage = true,
  )
}

@Composable
private fun BtWifiMeasurementStep(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    connectionState = state.currentStepConnectionState,
    title = BtWifiScaleSetupStrings.CollectingMeasurement.Title(state.currentStepConnectionState),
    showIndicationOnly = true,
    indicatorIcon = if(state.currentStepConnectionState is ConnectionState.Failed)  LoaderIconType.Error else LoaderIconType.Measurement,
    primaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
      { onIntent(BtWifiScaleSetupIntent.TryAgain) }
    } else null,
    secondaryButtonClick = if (state.currentStepConnectionState is ConnectionState.Failed) {
      { onIntent(BtWifiScaleSetupIntent.OpenHelp) }
    } else null,
  )
}

@Composable
private fun BtWifiFinishedStep(
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    title = BtWifiScaleSetupStrings.DeviceConnected.Title,
    subtitle = BtWifiScaleSetupStrings.DeviceConnected.Subtitle,
    setupImage = AppIcons.Setup.Accuchecked,
    contentButtonText = BtWifiScaleSetupStrings.DeviceConnected.WhatsThisButton,
    contentButtonClick = { onIntent(BtWifiScaleSetupIntent.OpenAccucheckModal) },
  )
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
