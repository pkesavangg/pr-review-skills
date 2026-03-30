package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.BabyProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

val babyScaleInitialSteps: ImmutableList<BabyScaleSetupStep> = persistentListOf(
  BabyScaleSetupStep.SCALE_INFO,
  BabyScaleSetupStep.PERMISSIONS,
  BabyScaleSetupStep.WAKEUP,
  BabyScaleSetupStep.CONNECTING_BLUETOOTH,
  BabyScaleSetupStep.SCALE_NAME,
  BabyScaleSetupStep.PAIRED_SUCCESS,
  BabyScaleSetupStep.BABY_PROFILE_FORM,
  BabyScaleSetupStep.BABY_LIST,
  BabyScaleSetupStep.SETUP_FINISHED,
)

/**
 * State for BabyScaleSetupScreen.
 */
@Stable
data class BabyScaleSetupState(
  override val scaleSetupState: ScaleSetupState<BabyScaleSetupStep> = ScaleSetupState(
    setupState = SetupState(
      step = BabyScaleSetupStep.SCALE_INFO,
    ),
    steps = babyScaleInitialSteps,
  ),
  val nickname: String = "Smart Baby Scale",
  val babyProfiles: ImmutableList<BabyProfile> = persistentListOf(),
  val editingProfile: BabyProfile = BabyProfile(),
  val editingProfileIndex: Int = -1,
) : BaseState<BabyScaleSetupStep, BabyScaleSetupState> {
  override fun copyBaseState(scaleSetupState: ScaleSetupState<BabyScaleSetupStep>): BabyScaleSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Intents for BabyScaleSetupScreen actions.
 */
sealed interface BabyScaleSetupIntent : ScaleSetupIntent {
  data class SetNickname(val nickname: String) : BabyScaleSetupIntent
  data class UpdateEditingProfile(val profile: BabyProfile) : BabyScaleSetupIntent
  data object SaveBabyProfile : BabyScaleSetupIntent
  data class EditBabyProfile(val index: Int) : BabyScaleSetupIntent
  data class DeleteBabyProfile(val index: Int) : BabyScaleSetupIntent
  data object AddAnotherBaby : BabyScaleSetupIntent
  data object ResetEditingProfile : BabyScaleSetupIntent
}

/**
 * Reducer for BabyScaleSetupScreen.
 */
class BabyScaleSetupReducer : ScaleSetupReducer<BabyScaleSetupStep, BabyScaleSetupState>() {
  override fun reduce(state: BabyScaleSetupState, intent: ScaleSetupIntent): BabyScaleSetupState? {
    return when (intent) {
      is BabyScaleSetupIntent.SetNickname -> state.copy(nickname = intent.nickname)

      is BabyScaleSetupIntent.UpdateEditingProfile -> state.copy(editingProfile = intent.profile)

      is BabyScaleSetupIntent.SaveBabyProfile -> {
        val profiles = state.babyProfiles.toMutableList()
        if (state.editingProfileIndex >= 0) {
          profiles[state.editingProfileIndex] = state.editingProfile
        } else {
          profiles.add(state.editingProfile)
        }
        state.copy(
          babyProfiles = profiles.toImmutableList(),
          editingProfile = BabyProfile(),
          editingProfileIndex = -1,
        )
      }

      is BabyScaleSetupIntent.EditBabyProfile -> {
        val profile = state.babyProfiles.getOrNull(intent.index) ?: return state
        state.copy(
          editingProfile = profile,
          editingProfileIndex = intent.index,
        )
      }

      is BabyScaleSetupIntent.DeleteBabyProfile -> {
        val profiles = state.babyProfiles.toMutableList()
        profiles.removeAt(intent.index)
        state.copy(babyProfiles = profiles.toImmutableList())
      }

      is BabyScaleSetupIntent.AddAnotherBaby -> {
        state.copy(
          editingProfile = BabyProfile(),
          editingProfileIndex = -1,
        )
      }

      is BabyScaleSetupIntent.ResetEditingProfile -> {
        state.copy(
          editingProfile = BabyProfile(),
          editingProfileIndex = -1,
        )
      }

      else -> super.reduce(state, intent)
    }
  }
}
