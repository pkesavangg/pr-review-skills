package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.LcbtScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.LcbtBLESetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun LcbtScaleSetupScreen(
  sku: String,
  scaleInfo: ScaleInfo? = null,
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
  onIntent: (ScaleSetupIntent) -> Unit,
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


  ScaleSetupHeader(
    sku = sku,
    onBack = { onIntent(ScaleSetupIntent.ExitSetup(state.isLastStep)) },
    onHelp = { onIntent(ScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        if (state.isFirstStep || setupState.step == LcbtScaleSetupStep.PERMISSIONS || state.isLastStep) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.backButton,
            size = ButtonSize.Small,
            enabled = (!state.isFirstStep && !state.isLastStep),
            onClick = { onIntent(ScaleSetupIntent.Back) },
          )
        }
      },
      trailingContent = {
        if (state.isFirstStep || setupState.step == LcbtScaleSetupStep.PERMISSIONS || state.isLastStep) {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            enabled = setupState.connectionState == ConnectionState.Success || state.isFirstStep || state.isLastStep || state.nextEnabled,
            onClick = {
              focusManager.clearFocus()
              onIntent(ScaleSetupIntent.Next)
            },
          )
        }
      },
      pageContent = { step ->
        Column(
          modifier =
            Modifier
              .fillMaxSize(),
        ) {
          when (step) {
            LcbtScaleSetupStep.SCALE_INFO -> {
              ScaleInfo(sku = sku, setupType = ScaleSetupType.Lcbt,)
            }

            LcbtScaleSetupStep.PERMISSIONS -> {
              ScalePermissions(
                sku = sku,
                permissions = state.permissions,
                onRequestPermission = { onIntent(ScaleSetupIntent.RequestPermission(it)) },
              )
            }

            LcbtScaleSetupStep.WAKEUP -> {
              ScaleSetupLoader(
                connectionState = setupState.connectionState,
                title = LcbtScaleSetupStrings.WakeupScale.Title(setupState.connectionState),
                subtitle = LcbtScaleSetupStrings.WakeupScale.Subtitle(setupState.connectionState),
                scaleImageSku = if (setupState.connectionState is ConnectionState.Failed)
                  sku else null,
                showIndicationOnly = setupState.connectionState !is ConnectionState.Failed,
                primaryButtonClick = if (setupState.connectionState is ConnectionState.Failed) {
                  { onIntent(ScaleSetupIntent.TryAgain) }
                } else null,
                secondaryButtonClick = if (setupState.connectionState is ConnectionState.Failed) {
                  { onIntent(ScaleSetupIntent.OpenHelp) }
                } else null,
              )
            }

            LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
              ScaleSetupLoader(
                connectionState = setupState.connectionState,
                title = LcbtScaleSetupStrings.ConnectingBluetooth.Title(setupState.connectionState),
                scaleImageSku = sku,
                primaryButtonClick = if (setupState.connectionState is ConnectionState.Failed) {
                  { onIntent(ScaleSetupIntent.TryAgain) }
                } else null,
                secondaryButtonClick = if (setupState.connectionState is ConnectionState.Failed) {
                  { onIntent(ScaleSetupIntent.OpenHelp) }
                } else null,
              )
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
      },
    )
  }
}


