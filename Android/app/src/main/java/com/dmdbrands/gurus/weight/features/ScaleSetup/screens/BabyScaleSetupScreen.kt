package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyListContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyProfileFormContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyScaleConnectionFailed
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyScaleLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.PairedSuccessContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleNameContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScalePermissions
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.babyScaleInitialSteps
import com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel.BabyScaleBLESetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay

/** Steps rendered outside the pager (form/list with complex composables). */
private val NON_PAGER_STEPS = setOf(
  BabyScaleSetupStep.BABY_PROFILE_FORM,
  BabyScaleSetupStep.BABY_LIST,
  BabyScaleSetupStep.SETUP_FINISHED,
)

/** Steps rendered in the HorizontalPager — derived from the single source of truth. */
private val PAGER_STEPS = babyScaleInitialSteps.filter { it !in NON_PAGER_STEPS }

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

  ScaleSetupHeader(
    sku = sku,
    onBack = { onIntent(ScaleSetupIntent.ExitSetup(state.isLastStep)) },
    onHelp = { onIntent(ScaleSetupIntent.OpenHelp) },
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f)) {
        when (currentStep) {
          BabyScaleSetupStep.SCALE_INFO ->
            ScaleInfo(sku = sku, setupType = ScaleSetupType.BabyScale)

          BabyScaleSetupStep.PERMISSIONS ->
            ScalePermissions(
              sku = sku,
              permissions = state.permissions,
              onRequestPermission = { onIntent(ScaleSetupIntent.RequestPermission(it)) },
            )

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

          BabyScaleSetupStep.SCALE_NAME ->
            ScaleNameContent(
              nickname = state.nickname,
              onNicknameChanged = { onIntent(BabyScaleSetupIntent.SetNickname(it)) },
            )

          BabyScaleSetupStep.PAIRED_SUCCESS ->
            PairedSuccessContent()

          BabyScaleSetupStep.BABY_PROFILE_FORM ->
            BabyProfileFormContent(
              profile = state.editingProfile,
              onProfileChanged = { onIntent(BabyScaleSetupIntent.UpdateEditingProfile(it)) },
            )

          BabyScaleSetupStep.BABY_LIST ->
            BabyListContent(
              babyProfiles = state.babyProfiles,
              onEditBaby = { index ->
                onIntent(BabyScaleSetupIntent.EditBabyProfile(index))
                onIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.BABY_PROFILE_FORM))
              },
              onDeleteBaby = { index ->
                onIntent(BabyScaleSetupIntent.DeleteBabyProfile(index))
              },
              onAddBaby = {
                onIntent(BabyScaleSetupIntent.AddAnotherBaby)
                onIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.BABY_PROFILE_FORM))
              },
            )

          BabyScaleSetupStep.SETUP_FINISHED -> {}
        }
      }

      // Bottom navigation
      val showNav = currentStep in listOf(
        BabyScaleSetupStep.SCALE_INFO,
        BabyScaleSetupStep.PERMISSIONS,
        BabyScaleSetupStep.SCALE_NAME,
        BabyScaleSetupStep.PAIRED_SUCCESS,
        BabyScaleSetupStep.BABY_PROFILE_FORM,
        BabyScaleSetupStep.BABY_LIST,
      )
      if (showNav) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.xs),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AppButton(
            type = ButtonType.TextPrimary,
            label = ScaleSetupStrings.backButton,
            size = ButtonSize.Small,
            enabled = !state.isFirstStep,
            onClick = { onIntent(ScaleSetupIntent.Back) },
          )
          when (currentStep) {
            BabyScaleSetupStep.SCALE_INFO,
            BabyScaleSetupStep.PERMISSIONS ->
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

            BabyScaleSetupStep.SCALE_NAME ->
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

            BabyScaleSetupStep.PAIRED_SUCCESS ->
              AppButton(
                type = ButtonType.PrimaryFilled,
                label = BabyScaleSetupStrings.SetupButtons.Continue,
                size = ButtonSize.Small,
                onClick = { onIntent(ScaleSetupIntent.Next) },
              )

            BabyScaleSetupStep.BABY_PROFILE_FORM ->
              AppButton(
                type = ButtonType.PrimaryFilled,
                label = BabyScaleSetupStrings.SetupButtons.Save,
                size = ButtonSize.Small,
                enabled = state.editingProfile.name.isNotBlank(),
                onClick = {
                  focusManager.clearFocus()
                  onIntent(BabyScaleSetupIntent.SaveBabyProfile)
                  onIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.BABY_LIST))
                },
              )

            BabyScaleSetupStep.BABY_LIST ->
              AppButton(
                type = ButtonType.PrimaryFilled,
                label = BabyScaleSetupStrings.SetupButtons.Finish,
                size = ButtonSize.Small,
                onClick = { onIntent(ScaleSetupIntent.ExitSetup(true)) },
              )

            else -> {}
          }
        }
      }
    }
  }
}
