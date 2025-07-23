package com.greatergoods.meapp.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.greatergoods.meapp.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.reducer.LcbtScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.strings.LcbtScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.viewmodel.LcbtScaleSetupViewModel
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
  val viewModel: LcbtScaleSetupViewModel =
    hiltViewModel<LcbtScaleSetupViewModel, LcbtScaleSetupViewModel.Factory> { factory ->
      factory.create(sku, broadcastId, initialStep)
    }
  val state by viewModel.state.collectAsState()
  LcbtScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun LcbtScaleSetupScreenContent(
  state: LCBTScaleSetupState,
  onIntent: (LcbtScaleSetupIntent) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { LCBTScaleSetupState.steps.size }
  val isAnimating = remember { mutableStateOf(false) }

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.setupState.step) {
    if (!isAnimating.value) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(state.setupState.step.ordinal)
      } finally {
        delay(100)
        isAnimating.value = false
      }
    }
  }


  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(LcbtScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(LcbtScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = LCBTScaleSetupState.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        if (state.isFirstStep || state.setupState.step == LcbtScaleSetupStep.PERMISSIONS) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.backButton,
            size = ButtonSize.Small,
            enabled = !state.isFirstStep,
            onClick = { onIntent(LcbtScaleSetupIntent.Back) },
          )
        }
      },
      trailingContent = {
        if (state.isFirstStep || state.setupState.step == LcbtScaleSetupStep.PERMISSIONS || state.isLastStep) {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            enabled = state.setupState.connectionState == ConnectionState.Success || state.isFirstStep || state.isLastStep,
            onClick = {
              focusManager.clearFocus()
              onIntent(LcbtScaleSetupIntent.Next)
            },
          )
        }
      },
      pageContent = { step ->
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(MeTheme.spacing.md),
        ) {
          when (step) {
            LcbtScaleSetupStep.SCALE_INFO -> {
              ScaleInfo(sku = state.sku)
            }

            LcbtScaleSetupStep.PERMISSIONS -> {
              ScalePermissions(
                sku = state.sku,
                permissions = state.permissions,
                onRequestPermission = { onIntent(LcbtScaleSetupIntent.RequestPermission(it)) },
              )
            }

            LcbtScaleSetupStep.WAKEUP -> {
              ScaleSetupLoader(
                connectionState = state.setupState.connectionState,
                title = LcbtScaleSetupStrings.WakeupScale.Title(state.setupState.connectionState),
                subtitle = LcbtScaleSetupStrings.WakeupScale.Subtitle(state.setupState.connectionState),
                errorCode = if (state.setupState.connectionState is ConnectionState.ErrorWithMessage) state.setupState.connectionState.message else null,
                scaleImageSku = if (state.setupState.connectionState == ConnectionState.Error)
                  state.sku else null,
                showIndicationOnly = state.setupState.connectionState != ConnectionState.Error,
                primaryButtonClick = if (state.setupState.connectionState == ConnectionState.Error) {
                  { onIntent(LcbtScaleSetupIntent.TryAgain) }
                } else null,
                secondaryButtonClick = if (state.setupState.connectionState == ConnectionState.Error) {
                  { onIntent(LcbtScaleSetupIntent.TryAgain) }
                } else null,
              )
            }

            LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
              ScaleSetupLoader(
                connectionState = state.setupState.connectionState,
                title = LcbtScaleSetupStrings.ConnectingBluetooth.Title(state.setupState.connectionState),
                scaleImageSku = state.sku,
                primaryButtonClick = if (state.setupState.connectionState == ConnectionState.Error) {
                  { onIntent(LcbtScaleSetupIntent.TryAgain) }
                } else null,
                secondaryButtonClick = if (state.setupState.connectionState == ConnectionState.Error) {
                  { onIntent(LcbtScaleSetupIntent.TryAgain) }
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
          }
        }
      },
    )
  }
}


