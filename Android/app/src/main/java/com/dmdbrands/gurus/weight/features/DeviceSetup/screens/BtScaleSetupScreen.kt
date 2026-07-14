package com.dmdbrands.gurus.weight.features.DeviceSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceInfoContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DevicePermissions
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SelectButton
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.BtScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun BtScaleSetupScreen(
  sku: String,
  scaleInfo: DeviceModelInfo?
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
  val state by viewModel.state.collectAsStateWithLifecycle()
  BtScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BtScaleSetupScreenContent(
  state: BtScaleSetupState,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val sku = state.scaleSetupState.sku
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.scaleSetupState.steps.size }
  val currentStep = state.step

  // Sync ViewModel state to Pager state
  LaunchedEffect(currentStep) {
    val targetPage = currentStep.ordinal
    // Only scroll if we're not already on the target page
    if (pagerState.currentPage != targetPage) {
      try {
        pagerState.animateScrollToPage(targetPage)
      } catch (e: Exception) {
        // If animation fails, snap to page immediately
        pagerState.scrollToPage(targetPage)
      }
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
      leadingContent = {
        AppButton(
          type = ButtonType.TextPrimary,
          modifier = Modifier.testTag(TestTags.DeviceSetup.BackButton),
          label = DeviceSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = state.backEnabled,
          onClick = { onIntent(DeviceSetupIntent.Back) },
        )
      },
      middleContent = {
        // Skip button can be added here when needed for other steps
      },
      trailingContent = {
        AppButton(
          type = ButtonType.PrimaryFilled,
          modifier = Modifier.testTag(TestTags.DeviceSetup.NextButton),
          label = if (state.isLastStep) DeviceSetupStrings.FinishButton else DeviceSetupStrings.nextButton,
          size = ButtonSize.Small,
          enabled = state.nextEnabled || state.isFirstStep || state.isLastStep,
          onClick = {
            focusManager.clearFocus()
            onIntent(DeviceSetupIntent.Next)
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
              DeviceInfoContent(sku = sku, setupType = DeviceSetupType.Bluetooth,)
            }

            BtScaleSetupStep.PERMISSIONS -> {
              DevicePermissions(
                sku = sku,
                permissions = state.scaleSetupState.permissions,
                onRequestPermission = { onIntent(DeviceSetupIntent.RequestPermission(it)) },
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
                  onIntent(DeviceSetupIntent.TryAgain)
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
