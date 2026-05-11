package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.AppsyncSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.AppsyncScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.greatergoods.libs.appsync.startAppSyncScan
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun AppsyncScaleSetupScreen(
  sku: String,
  viewModel: AppsyncScaleSetupViewModel =
    hiltViewModel<AppsyncScaleSetupViewModel, AppsyncScaleSetupViewModel.Factory> { factory ->
      factory.create(sku)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

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
  var isScanning by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  val context = LocalContext.current

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.currentStep) {
    val targetPage = state.currentStep.ordinal
    // Skip pager animation for OPEN_CAMERA step since we launch a separate activity
    if (state.currentStep == AppsyncScaleSetupStep.OPEN_CAMERA) {
      isScanning = true
      coroutineScope.launch {
        try {
          val result = startAppSyncScan(
            context = context,
            zoom = 4,
            showManualEntryButton = false,
            onBack = {
              // Create cancelled result and call intent handler immediately
              val cancelResult = AppSyncResultFactory.createCancelResult(4)
              com.greatergoods.libs.appsync.AppSyncResultHolder.result = cancelResult
              onIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(cancelResult))
            },
          )
          onIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        } catch (e: Exception) {
          // Handle error
        } finally {
          isScanning = false
        }
      }
    } else {
      // Only animate pager for non-camera steps
      if (pagerState.currentPage != state.currentStepIndex) {
        try {
          pagerState.animateScrollToPage(state.currentStepIndex)
        } catch (e: Exception) {
          // If animation fails, snap to page immediately
          pagerState.scrollToPage(state.currentStepIndex)
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
      leadingContent = when (state.currentStep) {
        AppsyncScaleSetupStep.OPEN_CAMERA -> null
        else -> {
          {
            AppButton(
              type = ButtonType.TextPrimary,
              label = ScaleSetupStrings.backButton,
              size = ButtonSize.Small,
              enabled = !state.isFirstStep && !state.isLastStep,
              onClick = { onIntent(AppsyncScaleSetupIntent.Back) },
            )
          }
        }
      },
      trailingContent = when (state.currentStep) {
        AppsyncScaleSetupStep.OPEN_CAMERA -> null
        else -> {
          {
            AppButton(
              type = ButtonType.PrimaryFilled,
              label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
              size = ButtonSize.Small,
              enabled = state.isNextEnabled && !isScanning,
              onClick = {
                focusManager.clearFocus()
                if (state.isLastStep) {
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
            ScaleInfo(sku = state.sku, setupType = ScaleSetupType.AppSync,)
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
                  canApplyUppercaseStyle = true,
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
                  canApplyUppercaseStyle = true,
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
          sku = "0342",
          bodyComp = true,
          steps = persistentListOf(
            AppsyncScaleSetupStep.SCALE_INFO,
            AppsyncScaleSetupStep.PERMISSIONS,
            AppsyncScaleSetupStep.ACTIVATE_SCALE,
            AppsyncScaleSetupStep.ADD_INFO,
            AppsyncScaleSetupStep.STEP_ON,
            AppsyncScaleSetupStep.OPEN_CAMERA,
            AppsyncScaleSetupStep.SETUP_FINISHED,
          ),
        ),
      onIntent = {},
    )
  }
}
