package com.greatergoods.meapp.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.libs.appsync.startAppSyncScan
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleInfo
import com.greatergoods.meapp.features.ScaleSetup.components.ScalePermissions
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupHeader
import com.greatergoods.meapp.features.ScaleSetup.components.SetupContent
import com.greatergoods.meapp.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.AppsyncScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.AppsyncScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.AppsyncSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.viewmodel.AppsyncScaleSetupViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppsyncScaleSetupScreen(
  sku: String,
  viewModel: AppsyncScaleSetupViewModel =
    hiltViewModel<AppsyncScaleSetupViewModel, AppsyncScaleSetupViewModel.Factory> { factory ->
      factory.create(sku)
    },
) {
  val state by viewModel.state.collectAsState()

  AppsyncScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun AppsyncScaleSetupScreenContent(
  state: AppsyncScaleSetupState,
  onIntent: (AppsyncScaleSetupIntent) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.steps.size }
  val isAnimating = remember { mutableStateOf(false) }
  var isScanning by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  val context = LocalContext.current

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

    if(state.currentStep == AppsyncScaleSetupStep.OPEN_CAMERA){
      isScanning = true
      coroutineScope.launch {
        try {
          val result = startAppSyncScan(
            context = context,
            zoom = 4,
            showManualEntryButton = false,
          )
          onIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        } catch (e: Exception) {
          // Handle error
        } finally {
          isScanning = false
        }
      }
    }
  }

  ScaleSetupHeader(
    sku = state.sku,
    onBack = { onIntent(AppsyncScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(AppsyncScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = when (state.currentStep)  {
        AppsyncScaleSetupStep.OPEN_CAMERA -> null
        else -> {
          {
            AppButton(
              type = ButtonType.TextPrimary,
              label = ScaleSetupStrings.backButton,
              size = ButtonSize.Small,
              enabled = !state.isFirstStep,
              onClick = { onIntent(AppsyncScaleSetupIntent.Back) },
            )
          }
        }
      },
      trailingContent = when (state.currentStep)  {
        AppsyncScaleSetupStep.OPEN_CAMERA -> null
        else -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
              size = ButtonSize.Small,
              enabled = state.isNextEnabled || !isScanning,
              onClick = {
                focusManager.clearFocus()
                if(state.isLastStep) {
                  onIntent(AppsyncScaleSetupIntent.ExitSetup(true))
                } else {
                  onIntent(AppsyncScaleSetupIntent.Next)
                }
              },
            )
          }
        }
      },
      pageContent = { step ->
        when (step) {
          AppsyncScaleSetupStep.SCALE_INFO -> {
            ScaleInfo(sku = state.sku)
          }

          AppsyncScaleSetupStep.PERMISSIONS -> {
            ScalePermissions(
              sku = state.sku,
              permissions = state.permissions,
              onRequestPermission = {
                onIntent(AppsyncScaleSetupIntent.RequestPermission(it))
              },
            )
          }

          AppsyncScaleSetupStep.ACTIVATE_SCALE -> {
            SetupContent(
              title = AppsyncSetupStrings.ActivateScale.Title,
              subtitle = AppsyncSetupStrings.ActivateScale.Message,
            )
          }

          AppsyncScaleSetupStep.ADD_INFO -> {
            SetupContent(
              title = AppsyncSetupStrings.AddInfo.Title,
              subtitle = AppsyncSetupStrings.AddInfo.Message,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AppText(
                  text = AppsyncSetupStrings.AddInfo.UserNumber,
                  textType = TextType.ListTitle1,
                )
                AppText(
                  text = AppsyncSetupStrings.AddInfo.UserNumberMessage,
                  textType = TextType.Body,
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AppText(
                  text = AppsyncSetupStrings.AddInfo.BodyComp,
                  textType = TextType.ListTitle1,
                )
                AppText(
                  text = AppsyncSetupStrings.AddInfo.BodyCompMessage,
                  textType = TextType.Body,
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AppText(
                  text = AppsyncSetupStrings.AddInfo.HeightAge,
                  textType = TextType.ListTitle1,
                )
              }
            }
          }

          AppsyncScaleSetupStep.STEP_ON -> {
            SetupContent(
              title = AppsyncSetupStrings.StepOn.Title,
              subtitle = AppsyncSetupStrings.StepOn.Message,
            )
          }
          AppsyncScaleSetupStep.SETUP_FINISHED -> {
            SetupContent(
              title = AppsyncSetupStrings.SetupComplete.Title,
              subtitle = AppsyncSetupStrings.SetupComplete.Message,
              setupFinished = true,
              supportingImage = AppIcons.Setup.AppSyncNavBar,
            )
          }
          else -> Unit
        }
      },
    )
  }
}

@PreviewTheme()
@Composable
fun AppsyncScaleSetupPreview() {
  MeAppTheme {
    AppsyncScaleSetupScreenContent(
      state =
        AppsyncScaleSetupState(
          sku = "0412",
        ),
      onIntent = {},
    )
  }
}
