package com.dmdbrands.gurus.weight.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyListContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyProfileFormContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Non-pager content for baby scale setup — renders BABY_PROFILE_FORM and BABY_LIST
 * outside the HorizontalPager to avoid the Compose prefetch crash.
 */
@Composable
fun BabyScaleSetupNonPagerContent(
  state: BabyScaleSetupState,
  currentStep: BabyScaleSetupStep,
  focusManager: FocusManager,
  onIntent: (ScaleSetupIntent) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f)) {
      when (currentStep) {
        BabyScaleSetupStep.BABY_PROFILE_FORM -> {
          BabyProfileFormContent(
            profile = state.editingProfile,
            onProfileChanged = { onIntent(BabyScaleSetupIntent.UpdateEditingProfile(it)) },
          )
        }

        BabyScaleSetupStep.BABY_LIST -> {
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
        }

        else -> {}
      }
    }

    // Bottom navigation bar
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
        onClick = { onIntent(ScaleSetupIntent.Back) },
      )
      when (currentStep) {
        BabyScaleSetupStep.BABY_PROFILE_FORM -> {
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
        }

        BabyScaleSetupStep.BABY_LIST -> {
          AppButton(
            type = ButtonType.PrimaryFilled,
            label = BabyScaleSetupStrings.SetupButtons.Finish,
            size = ButtonSize.Small,
            onClick = { onIntent(ScaleSetupIntent.ExitSetup(true)) },
          )
        }

        else -> {}
      }
    }
  }
}
