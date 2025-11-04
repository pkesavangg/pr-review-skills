package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

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
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SelectButton
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.BtScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay

@Composable
fun BtScaleSetupScreen(
  sku: String,
  scaleInfo: ScaleInfo?
) {
  val setupInit = SetupInitData(
    sku = sku,
    initialStep = BtScaleSetupStep.SCALE_INFO,
    scaleInfo = scaleInfo
  )
  val viewModel: BtScaleSetupViewModel =
    hiltViewModel<BtScaleSetupViewModel, BtScaleSetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsState()
  BtScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BtScaleSetupScreenContent(
  state: BtScaleSetupState,
  onIntent: (ScaleSetupIntent) -> Unit,
) {
  val sku = state.scaleSetupState.sku
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.scaleSetupState.steps.size }
  val isAnimating = remember { mutableStateOf(false) }

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.scaleSetupState.setupState.step) {
    if (!isAnimating.value) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(state.step.ordinal)
      } finally {
        delay(100)
        isAnimating.value = false
      }
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
        AppButton(
          type = ButtonType.TextPrimary,
          label = ScaleSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = state.backEnabled,
          onClick = { onIntent(ScaleSetupIntent.Back) },
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
          enabled = state.nextEnabled || state.isFirstStep || state.isLastStep,
          onClick = {
            focusManager.clearFocus()
            onIntent(ScaleSetupIntent.Next)
          },
        )
      },
      pageContent = { step ->
        Column(
          modifier =
            Modifier
              .fillMaxSize(),
        ) {
          when (step) {
            BtScaleSetupStep.SCALE_INFO -> {
              ScaleInfo(sku = sku, setupType = ScaleSetupType.Bluetooth,)
            }

            BtScaleSetupStep.PERMISSIONS -> {
              ScalePermissions(
                sku = sku,
                permissions = state.scaleSetupState.permissions,
                onRequestPermission = { onIntent(ScaleSetupIntent.RequestPermission(it)) },
              )
            }

            BtScaleSetupStep.SELECT_USER -> {
              val userNumbers = (1..8).toList()
              val userButtons = SelectButtonHelper.createUserNumberButtons(userNumbers, selectedNumber = state.user)
              SelectButton(
                title = BtScaleSetupStrings.ChooseUser.Title,
                subtitle = BtScaleSetupStrings.ChooseUser.Message,
                selectButtonItems = userButtons,
                isSelectable = true,
                sku = sku,
                modifier = Modifier,
                onItemSelected = { value ->
                  onIntent(BtScaleSetupIntent.SetUser(value.toInt()))
                },
              )
            }

            BtScaleSetupStep.PAIRING_MODE -> {
              SetupContent(
                title = BtScaleSetupStrings.PairingMode.Title,
                subtitle = BtScaleSetupStrings.PairingMode.Subtitle,
                isGifImage = true,
                supportingImage = AppIcons.Setup.PairModeGif(sku),
                loaderText = BtScaleSetupStrings.PairingMode.PairModeText(state.scaleSetupState.setupState.connectionState),
                connectionState = state.scaleSetupState.setupState.connectionState,
                loaderClick = {
                  onIntent(ScaleSetupIntent.TryAgain)
                },
              )
            }

            BtScaleSetupStep.SET_DEVICE_USER -> {
              SetupContent(
                title = BtScaleSetupStrings.SetDeviceUser.Title,
                subtitle = BtScaleSetupStrings.SetDeviceUser.Subtitle(sku, state.userString),
                isGifImage = true,
                supportingImage = AppIcons.Setup.SetUserGif(sku),
              )
            }

            BtScaleSetupStep.STEP_ON -> {
              SetupContent(
                title = BtScaleSetupStrings.StepOn.Title,
                subtitle = BtScaleSetupStrings.StepOn.Subtitle,
                isGifImage = true,
                supportingImage = AppIcons.Setup.StepOnGif(sku),
                loaderText = BtScaleSetupStrings.StepOn.StepOnText(state.scaleSetupState.setupState.connectionState),
                connectionState = state.scaleSetupState.setupState.connectionState,
              )
            }

            BtScaleSetupStep.SETUP_FINISHED -> {
              SetupContent(
                title = BtScaleSetupStrings.SetupFinished.Title,
                subtitle = BtScaleSetupStrings.SetupFinished.Subtitle,
                setupFinished = true,
              )
            }
          }
        }
      },
    )
  }
}
