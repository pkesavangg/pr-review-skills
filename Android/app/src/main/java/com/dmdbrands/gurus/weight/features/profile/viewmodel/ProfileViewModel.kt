package com.dmdbrands.gurus.weight.features.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.Gender
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.DeviceProfileConstants
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.profile.model.ProfileFormControls
import com.dmdbrands.gurus.weight.features.profile.model.ProfileIntent
import com.dmdbrands.gurus.weight.features.profile.model.ProfileReducer
import com.dmdbrands.gurus.weight.features.profile.model.ProfileState
import com.dmdbrands.gurus.weight.features.profile.strings.ProfileStrings
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
  private val accountService: IAccountService,
  private val ggDeviceService: GGDeviceService,
  private val bodyCompositionService: IBodyCompositionService,
) : BaseIntentViewModel<ProfileState, ProfileIntent>(
  reducer = ProfileReducer(),
) {
  companion object {
    private const val TAG = "ProfileViewModel"
  }

  init {
    handleIntent(ProfileIntent.LoadProfile)
  }

  override fun provideInitialState(): ProfileState {
    return ProfileState(
      form = FormGroup(ProfileFormControls.create()),
    )
  }

  override fun handleIntent(intent: ProfileIntent) {
    super.handleIntent(intent)
    when (intent) {
      is ProfileIntent.LoadProfile -> loadProfile()
      is ProfileIntent.Submit -> onSubmit()
      is ProfileIntent.Success -> onUpdateSuccess()
      is ProfileIntent.ShowBiologicalSexModal -> showBiologicalSexModal()
      is ProfileIntent.ShowHeightModal -> showHeightModal()
      is ProfileIntent.OnRequestBack -> onRequestBack()
      else -> Unit
    }
  }

  private fun loadProfile() {
    viewModelScope.launch {
      try {
        val currentAccount = accountService.getCurrentAccount()
        if (currentAccount != null) {
          handleIntent(
            ProfileIntent.ProfileLoaded(
              firstName = currentAccount.firstName,
              lastName = currentAccount.lastName,
              email = currentAccount.email,
              zipcode = currentAccount.zipcode,
              // For an unset DOB (e.g. baby-only accounts, MOB-591) fall back to the form's
              // default sentinel instead of coercing null -> "" -> today. Passing "" here would
              // make getEpochMillisFromIsoString swallow the parse failure and return now, so the
              // editor would pre-fill the birthday to today and could persist a wrong DOB on save.
              birthday = DateTimeValue.Date(
                currentAccount.dob?.let { DateTimeValue.getEpochMillisFromIsoString(it) }
                  ?: DateTimeValue.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE),
              ),
              gender = currentAccount.gender ?: "",
              height = currentAccount.height ?: 0,
              weightUnit = currentAccount.weightUnit,
            ),
          )
          AppLog.i(TAG, "Profile data loaded successfully")
        } else {
          handleIntent(ProfileIntent.Error(ProfileStrings.Error.MessageGeneric))
          AppLog.w(TAG, "No current account found")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to load profile", e)
        handleIntent(ProfileIntent.Error(ProfileStrings.Error.MessageGeneric))
      }
    }
  }

  private fun showBiologicalSexModal() {
    val currentGender = state.value.form.controls.gender.value
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.BiologicalSex,
      options = listOf(
        RadioButtonOption(Gender.MALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Male),
        RadioButtonOption(Gender.FEMALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Female),
      ),
      selectedItem = currentGender.ifEmpty { null },
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selected ->
        selected?.let { gender ->
          state.value.form.controls.gender.onValueChange(gender.toString())
        }
      },
    )
  }

  private fun showHeightModal() {
    val currentHeight = state.value.form.controls.height.value
    val isMetric = state.value.weightUnit == WeightUnit.KG

    val currentHeightInput = HeightInput.fromStoredHeight(
      storedHeight = if (currentHeight > 0) currentHeight else BodyCompUpdateRequest.DEFAULT_HEIGHT,
      isMetric = isMetric,
    )

    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HeightPicker,
        params = mapOf("value" to currentHeightInput, "confirmText" to RadioGroupModalStrings.Button.Save),
        onConfirm = { selectedHeight ->
          if (selectedHeight is HeightInput) {
            state.value.form.controls.height.onValueChange(selectedHeight.toStoredHeight())
          }
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
        dismissOnBackPress = true,
      ),
    )
  }

  private fun onSubmit() {
    dialogQueueService.showLoader(
      message = ProfileStrings.LoaderMessage,
    )

    if (!state.value.form.validate()) {
      dialogQueueService.dismissLoader()
      handleIntent(ProfileIntent.Error(ProfileStrings.Error.MessageValidation))
      return
    }

    val formControls = state.value.form.controls
    viewModelScope.launch {
      val currentAccount = accountService.getCurrentAccount() ?: run {
        dialogQueueService.dismissLoader()
        return@launch
      }

      val newGender = formControls.gender.value.ifEmpty { null }
      val newHeight = formControls.height.value.takeIf { it > 0 }

      // Don't fabricate a DOB for an account that never had one. If the account's dob was unset
      // and the user left the picker on the default sentinel, send null so the server keeps it
      // unset rather than persisting the sentinel/today as a real birthday (MOB-591).
      val birthdayMillis = formControls.birthday.value.getTimestamp()
      val defaultBirthdayMillis =
        DateTimeValue.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE)
      val newDob = if (currentAccount.dob == null && birthdayMillis == defaultBirthdayMillis) {
        null
      } else {
        DateTimeValue.getDateFormatFromMilliseconds(birthdayMillis)
      }

      val profileUpdateRequest = ProfileUpdateRequest(
        id = currentAccount.id,
        firstName = formControls.firstName.value.trim(),
        lastName = formControls.lastName.value.trim(),
        email = formControls.email.value.trim(),
        zipcode = formControls.zipcode.value.trim(),
        gender = newGender ?: currentAccount.gender,
        dob = newDob,
      )
      try {
        var scaleResult: GGUserActionResponseType? = null
        accountService.updateProfile(profileUpdateRequest, true, showToast = false)

        // Write order is server-first, scale-second: the server is the source of truth and must
        // persist the user's edit even if no scale is paired or reachable. The body-comp call is
        // isolated in its own try/catch so a server failure on height does not skip the
        // downstream R4 scale profile update; the outer try/catch handles the broader failure UX.
        val heightChanged = newHeight != null && newHeight != currentAccount.height
        if (heightChanged) {
          val bodyComposition = BodyCompUpdateRequest(
            height = newHeight ?: currentAccount.height ?: BodyCompUpdateRequest.DEFAULT_HEIGHT,
            activityLevel = currentAccount.activityLevel ?: BodyCompUpdateRequest.DEFAULT_ACTIVITY_LEVEL,
            weightUnit = currentAccount.weightUnit.value,
          )
          try {
            bodyCompositionService.updateBodyComposition(BodyCompUpdateType.HEIGHT, bodyComposition)
          } catch (e: Exception) {
            AppLog.e(TAG, "Body composition height update failed; continuing with scale update", e)
          }
        }

        // Update scale profile if gender, dob, name, or height changed
        val genderChanged = newGender != null && newGender != currentAccount.gender
        val dobChanged = profileUpdateRequest.dob != currentAccount.dob
        val nameChanged = profileUpdateRequest.firstName != currentAccount.firstName

        if (dobChanged || nameChanged || genderChanged || heightChanged) {
          val updatedProfile = currentAccount.toGGBTUserProfile().let { profile ->
            var updated = profile
            if (genderChanged) updated = updated.copy(sex = newGender)
            if (heightChanged) updated = updated.copy(
              height = ConversionTools.convertStoredHeightToCm(newHeight ?: BodyCompUpdateRequest.DEFAULT_HEIGHT).toDouble(),
            )
            updated
          }
          scaleResult = updateR4Profile(updatedProfile)
        }

        if (scaleResult != null) {
          when (scaleResult) {
            GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
              dialogQueueService.enqueue(
                DialogModel.Alert(
                  title = AppPopupStrings.R4ProfileUpdatePending.Title,
                  message = AppPopupStrings.R4ProfileUpdatePending.Message,
                  onDismiss = { dialogQueueService.dismissCurrent() },
                ),
              )
            }

            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED, GGUserActionResponseType.EXCEPTION_ENCOUNTERED -> {
              dialogQueueService.showToast(
                Toast.Simple(
                  ToastStrings.Success.UpdateProfileSuccess.Message,
                  ToastStrings.Success.UpdateProfileSuccess.Header,
                ),
              )
            }

            else -> {}
          }
        }
        handleIntent(ProfileIntent.Success)
        navigateBack()
        AppLog.i(TAG, "Profile updated successfully")
      } catch (e: Exception) {
        AppLog.e(TAG, "Profile update failed", e)
        handleIntent(ProfileIntent.Error(ProfileStrings.Error.MessageGeneric))
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun onUpdateSuccess() {
    AppLog.i(TAG, "Profile update completed successfully")
  }

  private fun onRequestBack() {
    val hasChanges = state.value.form.isDirty

    if (hasChanges) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ProfileStrings.ExitDialog.Title,
          message = ProfileStrings.ExitDialog.Message,
          confirmText = ProfileStrings.ExitDialog.ConfirmText,
          cancelText = ProfileStrings.ExitDialog.CancelText,
          onConfirm = {
            navigateBack()
            dialogQueueService.dismissCurrent()
          },
          onCancel = {
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    } else {
      navigateBack()
    }
  }

  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
        AppLog.d(TAG, "Successfully navigated back from profile")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back from profile", e)
      }
    }
  }

  private suspend fun updateR4Profile(profile: GGBTUserProfile): GGUserActionResponseType {
    val result = CompletableDeferred<GGUserActionResponseType>()
    try {
      ggDeviceService.updateProfile(
        profile,
      ) { responseType ->
        result.complete(responseType)
      }
    } catch (e: Exception) {
      AppLog.d(TAG, "updateR4Profile - Error updating profile to scale: ${e.message}")
      result.complete(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
    }
    return withTimeoutOrNull(DeviceProfileConstants.SCALE_PROFILE_UPDATE_TIMEOUT_MS) { result.await() }
      ?: run {
        AppLog.d(TAG, "updateR4Profile - Timeout or no callback from scale; dismissing loader")
        GGUserActionResponseType.EXCEPTION_ENCOUNTERED
      }
  }
}
