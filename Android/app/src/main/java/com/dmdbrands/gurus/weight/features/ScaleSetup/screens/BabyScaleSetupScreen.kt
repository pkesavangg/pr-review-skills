package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.BabyScaleBLESetupViewModel
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import kotlinx.coroutines.delay

/** Steps rendered in the HorizontalPager — safe from prefetch crashes. */
private val PAGER_STEPS = listOf(
  BabyScaleSetupStep.SCALE_INFO,
  BabyScaleSetupStep.PERMISSIONS,
  BabyScaleSetupStep.WAKEUP,
  BabyScaleSetupStep.SCALE_NAME,
  BabyScaleSetupStep.PAIRED_SUCCESS,
)

/** Steps rendered outside the pager (form/list with complex composables). */
private val NON_PAGER_STEPS = setOf(
  BabyScaleSetupStep.BABY_PROFILE_FORM,
  BabyScaleSetupStep.BABY_LIST,
  BabyScaleSetupStep.SETUP_FINISHED,
)

@Composable
fun BabyScaleSetupScreen(
  sku: String,
  scaleInfo: ScaleInfo? = null,
  broadcastId: String? = null,
  initialStep: BabyScaleSetupStep = BabyScaleSetupStep.SCALE_INFO,
) {
  val setupInit = SetupInitData(
    sku = sku,
    scaleInfo = scaleInfo,
    broadcastId = broadcastId,
    initialStep = initialStep,
  )
  val viewModel: BabyScaleBLESetupViewModel =
    hiltViewModel<BabyScaleBLESetupViewModel, BabyScaleBLESetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsStateWithLifecycle()
  BabyScaleSetupScreenContent(
    state = state,
    sku = sku,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BabyScaleSetupScreenContent(
  state: BabyScaleSetupState,
  sku: String,
  onIntent: (ScaleSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState
  val focusManager = LocalFocusManager.current
  val currentStep = setupState.step
  val isNonPagerStep = currentStep in NON_PAGER_STEPS

  val pagerState = rememberPagerState { PAGER_STEPS.size }
  val isAnimating = remember { mutableStateOf(false) }

  LaunchedEffect(currentStep) {
    if (!isAnimating.value && !isNonPagerStep) {
      isAnimating.value = true
      try {
        val pageIndex = PAGER_STEPS.indexOf(currentStep)
        if (pageIndex >= 0) {
          pagerState.animateScrollToPage(pageIndex)
        }
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
    if (isNonPagerStep) {
      BabyScaleSetupNonPagerContent(
        state = state,
        currentStep = currentStep,
        focusManager = focusManager,
        onIntent = onIntent,
      )
    } else {
      BabyScaleSetupPagerContent(
        state = state,
        sku = sku,
        pagerSteps = PAGER_STEPS,
        pagerState = pagerState,
        focusManager = focusManager,
        onIntent = onIntent,
      )
    }
  }
}
