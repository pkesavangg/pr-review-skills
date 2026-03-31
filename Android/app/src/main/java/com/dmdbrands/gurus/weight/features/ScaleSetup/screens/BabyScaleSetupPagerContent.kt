package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyScaleConnectionFailed
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyScaleLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.PairedSuccessContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleNameContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Pager-based content for the first phase of baby scale setup:
 * SCALE_INFO → PERMISSIONS → WAKEUP → SCALE_NAME → PAIRED_SUCCESS
 *
 * BABY_PROFILE_FORM and BABY_LIST are rendered outside the pager
 * to avoid Compose HorizontalPager prefetch crash.
 */
@Composable
fun BabyScaleSetupPagerContent(
  state: BabyScaleSetupState,
  sku: String,
  pagerSteps: List<BabyScaleSetupStep>,
  pagerState: PagerState,
  focusManager: FocusManager,
  onIntent: (ScaleSetupIntent) -> Unit,
) {
  val setupState = state.scaleSetupState.setupState

  HorizontalPagerWithBottomNavigation(
    steps = pagerSteps,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    pagerState = pagerState,
    leadingContent = {
      val showBack = setupState.step in listOf(
        BabyScaleSetupStep.SCALE_INFO,
        BabyScaleSetupStep.PERMISSIONS,
        BabyScaleSetupStep.SCALE_NAME,
        BabyScaleSetupStep.PAIRED_SUCCESS,
      )
      if (showBack) {
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
      when (setupState.step) {
        BabyScaleSetupStep.SCALE_INFO,
        BabyScaleSetupStep.PERMISSIONS -> {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            enabled = state.isFirstStep || state.nextEnabled,
            onClick = {
              focusManager.clearFocus()
              onIntent(ScaleSetupIntent.Next)
            },
          )
        }

        BabyScaleSetupStep.SCALE_NAME -> {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = ScaleSetupStrings.nextButton,
            size = ButtonSize.Small,
            enabled = state.nickname.isNotBlank(),
            onClick = {
              focusManager.clearFocus()
              onIntent(ScaleSetupIntent.Next)
            },
          )
        }

        BabyScaleSetupStep.PAIRED_SUCCESS -> {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = BabyScaleSetupStrings.SetupButtons.Continue,
            size = ButtonSize.Small,
            onClick = { onIntent(ScaleSetupIntent.Next) },
          )
        }

        else -> {}
      }
    },
    pageContent = { step ->
      Column(modifier = Modifier.fillMaxSize()) {
        when (step) {
          BabyScaleSetupStep.SCALE_INFO -> {
            ScaleInfo(sku = sku, setupType = ScaleSetupType.BabyScale)
          }

          BabyScaleSetupStep.PERMISSIONS -> {
            ScalePermissions(
              sku = sku,
              permissions = state.permissions,
              onRequestPermission = { onIntent(ScaleSetupIntent.RequestPermission(it)) },
            )
          }

          BabyScaleSetupStep.WAKEUP -> {
            if (setupState.connectionState is ConnectionState.Failed) {
              BabyScaleConnectionFailed(
                title = BabyScaleSetupStrings.WakeupScale.Title(setupState.connectionState),
                subtitle = BabyScaleSetupStrings.WakeupScale.Subtitle(setupState.connectionState),
                onPairAgain = { onIntent(ScaleSetupIntent.TryAgain) },
                onSupport = { onIntent(ScaleSetupIntent.OpenHelp) },
              )
            } else {
              BabyScaleLoader(
                title = BabyScaleSetupStrings.WakeupScale.Title(setupState.connectionState),
                subtitle = BabyScaleSetupStrings.WakeupScale.Subtitle(setupState.connectionState),
              )
            }
          }

          BabyScaleSetupStep.SCALE_NAME -> {
            ScaleNameContent(
              nickname = state.nickname,
              onNicknameChanged = { onIntent(BabyScaleSetupIntent.SetNickname(it)) },
            )
          }

          BabyScaleSetupStep.PAIRED_SUCCESS -> {
            PairedSuccessContent()
          }

          else -> {}
        }
      }
    },
  )
}
