package com.greatergoods.meapp.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleInfo
import com.greatergoods.meapp.features.ScaleSetup.components.ScalePermissions
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupHeader
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupLoader
import com.greatergoods.meapp.features.ScaleSetup.components.SetupContent
import com.greatergoods.meapp.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState
import com.greatergoods.meapp.features.ScaleSetup.modal.SetupInitData
import com.greatergoods.meapp.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.reducer.ScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.strings.LcbtScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.viewmodel.LcbtBLESetupViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.delay

@Composable
fun LcbtScaleSetupScreen(
  sku: String,
  broadcastId: String? = null,
  initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO
) {
  val setupInit = SetupInitData(
    sku = sku,
    broadcastId = broadcastId,
    initialStep = initialStep,
  )
  val viewModel: LcbtBLESetupViewModel =
    hiltViewModel<LcbtBLESetupViewModel, LcbtBLESetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsState()
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
  val isAnimating = remember { mutableStateOf(false) }

  // Sync ViewModel state to Pager state
  LaunchedEffect(setupState.step) {
    if (!isAnimating.value) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(setupState.step.ordinal)
      } finally {
        delay(100)
        isAnimating.value = false
      }
    }
  }


  ScaleSetupHeader(
    sku = sku,
    onBack = { onIntent(ScaleSetupIntent.ExitSetup(state.isFirstStep)) },
    onHelp = { onIntent(ScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        if (state.isFirstStep || setupState.step == LcbtScaleSetupStep.PERMISSIONS) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.backButton,
            size = ButtonSize.Small,
            enabled = !state.isFirstStep,
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
            enabled = setupState.connectionState == ConnectionState.Success || state.isFirstStep || state.isLastStep,
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
              ScaleInfo(sku = sku)
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
                errorCode = if (setupState.connectionState is ConnectionState.Failed.ErrorWithMessage) setupState.connectionState.message else null,
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


