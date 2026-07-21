package com.dmdbrands.gurus.weight.features.DeviceSetup.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceInfoContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DevicePermissions
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.AppsyncScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.AppsyncScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.AppsyncSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.AppsyncScaleSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.greatergoods.libs.appsync.startAppSyncScan
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
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

  AppsyncStepSync(
    state = state,
    pagerState = pagerState,
    coroutineScope = coroutineScope,
    context = context,
    onScanningChange = { isScanning = it },
    onIntent = onIntent,
  )

  DeviceSetupHeader(
    sku = state.sku,
    onBack = { onIntent(AppsyncScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(AppsyncScaleSetupIntent.OpenHelp) },
  ) {
    AppsyncSetupPager(
      state = state,
      pagerState = pagerState,
      isScanning = isScanning,
      focusManager = focusManager,
      onIntent = onIntent,
    )
  }
}

// Sync ViewModel state to Pager state
@Composable
private fun AppsyncStepSync(
  state: AppsyncScaleSetupState,
  pagerState: PagerState,
  coroutineScope: CoroutineScope,
  context: Context,
  onScanningChange: (Boolean) -> Unit,
  onIntent: (AppsyncScaleSetupIntent) -> Unit,
) {
  LaunchedEffect(state.currentStep) {
    val targetPage = state.currentStep.ordinal
    // Skip pager animation for OPEN_CAMERA step since we launch a separate activity
    if (state.currentStep == AppsyncScaleSetupStep.OPEN_CAMERA) {
      onScanningChange(true)
      coroutineScope.launch {
        try {
          val result = startAppSyncScan(
            context = context,
            zoom = state.appSyncZoomLevel,
            showManualEntryButton = false,
            onBack = {
              // Create cancelled result and call intent handler immediately
              val cancelResult = AppSyncResultFactory.createCancelResult(state.appSyncZoomLevel)
              com.greatergoods.libs.appsync.AppSyncResultHolder.result = cancelResult
              onIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(cancelResult))
            },
          )
          AppLog.w(
            "AppSyncScan",
            "Scale display detected results on appsync setup flow (device=${android.os.Build.MODEL}, weight=${result.weight} errors=${result.errors})",
          )
          onIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        } catch (e: Exception) {
          AppLog.e("AppSyncScan", "AppSync scan failed on setup flow: ${e.message}", e)
        } finally {
          onScanningChange(false)
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
}

@Composable
private fun AppsyncSetupPager(
  state: AppsyncScaleSetupState,
  pagerState: PagerState,
  isScanning: Boolean,
  focusManager: FocusManager,
  onIntent: (AppsyncScaleSetupIntent) -> Unit,
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
            modifier = Modifier.testTag(TestTags.DeviceSetup.BackButton),
            label = DeviceSetupStrings.backButton,
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
            modifier = Modifier.testTag(TestTags.DeviceSetup.NextButton),
            label = if (state.isLastStep) DeviceSetupStrings.FinishButton else DeviceSetupStrings.nextButton,
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
      AppsyncPageContent(step = step, state = state, onIntent = onIntent)
    },
  )
}

@Composable
private fun AppsyncPageContent(
  step: AppsyncScaleSetupStep,
  state: AppsyncScaleSetupState,
  onIntent: (AppsyncScaleSetupIntent) -> Unit,
) {
  when (step) {
    AppsyncScaleSetupStep.SCALE_INFO -> {
      DeviceInfoContent(sku = state.sku, setupType = DeviceSetupType.AppSync,)
    }

    AppsyncScaleSetupStep.PERMISSIONS -> {
      DevicePermissions(
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
        AppsyncAddInfoContent()
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
}

@Composable
private fun AppsyncAddInfoContent() {
  Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
    AppText(
      text = AppsyncSetupStrings.AddInfo.UserNumber,
      textType = TextType.ListTitle1,
      // TalkBack: section header.
      modifier = Modifier.semantics { heading() },
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
      // TalkBack: section header.
      modifier = Modifier.semantics { heading() },
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
      // TalkBack: section header.
      modifier = Modifier.semantics { heading() },
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
