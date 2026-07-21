package com.dmdbrands.gurus.weight.features.DeviceSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceInfoContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DevicePermissions
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.LCBTScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.LcbtScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.LcbtBLESetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun LcbtScaleSetupScreen(
  sku: String,
  scaleInfo: DeviceModelInfo? = null,
  broadcastId: String? = null,
  initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO
) {
  val setupInit = SetupInitData(
    sku = sku,
    scaleInfo = scaleInfo,
    broadcastId = broadcastId,
    initialStep = initialStep,
  )
  val viewModel: LcbtBLESetupViewModel =
    hiltViewModel<LcbtBLESetupViewModel, LcbtBLESetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LcbtScaleSetupScreenContent(
    state = state,
    sku = sku,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun LcbtScaleSetupScreenContent(
  state: LCBTScaleSetupState,
  sku: String,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.scaleSetupState.steps.size }

  // Sync ViewModel state to Pager state. animateScrollToPage handles its own
  // cancellation when LaunchedEffect restarts — a manual isAnimating guard
  // could get stuck on `true` if the coroutine was cancelled mid-animation,
  // swallowing the slide animation on rapid step changes.
  LaunchedEffect(setupState.step) {
    if (pagerState.currentPage != setupState.step.ordinal) {
      pagerState.animateScrollToPage(setupState.step.ordinal)
    }
  }


  DeviceSetupHeader(
    sku = sku,
    onBack = { onIntent(DeviceSetupIntent.ExitSetup(state.isLastStep)) },
    onHelp = { onIntent(DeviceSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = { LcbtLeadingButton(state = state, onIntent = onIntent) },
      trailingContent = {
        LcbtTrailingButton(state = state, focusManager = focusManager, onIntent = onIntent)
      },
      pageContent = { step ->
        LcbtStepContent(step = step, sku = sku, state = state, onIntent = onIntent)
      },
    )
  }
}

@Composable
private fun LcbtLeadingButton(
  state: LCBTScaleSetupState,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState
  if (state.isFirstStep || setupState.step == LcbtScaleSetupStep.PERMISSIONS || state.isLastStep) {
    AppButton(
      type = ButtonType.TextPrimary,
      modifier = Modifier.testTag(TestTags.DeviceSetup.BackButton),
      label = DeviceSetupStrings.backButton,
      size = ButtonSize.Small,
      enabled = (!state.isFirstStep && !state.isLastStep),
      onClick = { onIntent(DeviceSetupIntent.Back) },
    )
  }
}

@Composable
private fun LcbtTrailingButton(
  state: LCBTScaleSetupState,
  focusManager: FocusManager,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState
  if (state.isFirstStep || setupState.step == LcbtScaleSetupStep.PERMISSIONS || state.isLastStep) {
    AppButton(
      type = ButtonType.PrimaryFilled,
      modifier = Modifier.testTag(TestTags.DeviceSetup.NextButton),
      label = if (state.isLastStep) DeviceSetupStrings.FinishButton else DeviceSetupStrings.nextButton,
      size = ButtonSize.Small,
      enabled = setupState.connectionState == ConnectionState.Success || state.isFirstStep || state.isLastStep || state.nextEnabled,
      onClick = {
        focusManager.clearFocus()
        onIntent(DeviceSetupIntent.Next)
      },
    )
  }
}

@Composable
private fun LcbtStepContent(
  step: LcbtScaleSetupStep,
  sku: String,
  state: LCBTScaleSetupState,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState
  Column(
    modifier =
      Modifier
        .fillMaxSize(),
  ) {
    when (step) {
      LcbtScaleSetupStep.SCALE_INFO -> {
        DeviceInfoContent(sku = sku, setupType = DeviceSetupType.Lcbt,)
      }

      LcbtScaleSetupStep.PERMISSIONS -> {
        DevicePermissions(
          sku = sku,
          permissions = state.permissions,
          onRequestPermission = { onIntent(DeviceSetupIntent.RequestPermission(it)) },
        )
      }

      LcbtScaleSetupStep.WAKEUP -> {
        LcbtWakeupContent(sku = sku, connectionState = setupState.connectionState, onIntent = onIntent)
      }

      LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
        LcbtConnectingContent(sku = sku, connectionState = setupState.connectionState, onIntent = onIntent)
      }

      LcbtScaleSetupStep.SETUP_FINISHED -> {
        SetupContent(
          title = LcbtScaleSetupStrings.SetupFinished.Title,
          subtitle = LcbtScaleSetupStrings.SetupFinished.Subtitle,
          setupFinished = true,
        )
      }

      else -> null
    }
  }
}

@Composable
private fun LcbtWakeupContent(
  sku: String,
  connectionState: ConnectionState,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    connectionState = connectionState,
    title = LcbtScaleSetupStrings.WakeupScale.Title(connectionState),
    subtitle = LcbtScaleSetupStrings.WakeupScale.Subtitle(connectionState),
    scaleImageSku = if (connectionState is ConnectionState.Failed)
      sku else null,
    showIndicationOnly = connectionState !is ConnectionState.Failed,
    primaryButtonClick = if (connectionState is ConnectionState.Failed) {
      { onIntent(DeviceSetupIntent.TryAgain) }
    } else null,
    secondaryButtonClick = if (connectionState is ConnectionState.Failed) {
      { onIntent(DeviceSetupIntent.OpenHelp) }
    } else null,
  )
}

@Composable
private fun LcbtConnectingContent(
  sku: String,
  connectionState: ConnectionState,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  DeviceSetupLoader(
    connectionState = connectionState,
    title = LcbtScaleSetupStrings.ConnectingBluetooth.Title(connectionState),
    scaleImageSku = sku,
    primaryButtonClick = if (connectionState is ConnectionState.Failed) {
      { onIntent(DeviceSetupIntent.TryAgain) }
    } else null,
    secondaryButtonClick = if (connectionState is ConnectionState.Failed) {
      { onIntent(DeviceSetupIntent.OpenHelp) }
    } else null,
  )
}


