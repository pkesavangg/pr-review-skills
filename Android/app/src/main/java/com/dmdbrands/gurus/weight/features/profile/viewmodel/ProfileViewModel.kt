package com.dmdbrands.gurus.weight.features.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
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
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Profile screen. Handles form state, validation, profile update logic.
 * @property accountService Service for account operations.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
  private val accountService: IAccountService,
  private val ggDeviceService: GGDeviceService
) : BaseIntentViewModel<ProfileState, ProfileIntent>(
  reducer = ProfileReducer(),
) {
  private val TAG = "ProfileViewModel"

  init {
    // Load profile data when ViewModel is created
    handleIntent(ProfileIntent.LoadProfile)
  }

  override fun provideInitialState(): ProfileState {
    return ProfileState(
      form = FormGroup(ProfileFormControls.create()),
    )
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: ProfileIntent) {
    super.handleIntent(intent)
    when (intent) {
      is ProfileIntent.LoadProfile -> loadProfile()
      is ProfileIntent.Submit -> onSubmit()
      is ProfileIntent.Success -> onUpdateSuccess()
      is ProfileIntent.OnRequestBack -> onRequestBack()
      else -> Unit
    }
  }

  /**
   * Loads the current user's profile data.
   */
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
              birthday = DateTimeValue.Date(
                DateTimeValue.getEpochMillisFromIsoString(currentAccount.dob),
              ),
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

  /**
   * Handles the profile form submission. Validates the form, shows loading, and attempts to update profile.
   * On success, shows success message. On failure, shows an error message.
   */
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
      // its an flow
      val currentAccount = accountService.getCurrentAccount() ?: return@launch
      val profileUpdateRequest = ProfileUpdateRequest(
        id = currentAccount.id,
        firstName = formControls.firstName.value.trim(),
        lastName = formControls.lastName.value.trim(),
        email = formControls.email.value.trim(),
        zipcode = formControls.zipcode.value.trim(),
        gender = currentAccount.gender,
        dob = DateTimeValue.getDateFormatFromMilliseconds(formControls.birthday.value.getTimestamp()),
      )
      try {
        var scaleResult: GGUserActionResponseType? = null
        // Use offline handler service similar to Angular implementation
        accountService.updateProfile(profileUpdateRequest, true, showToast = false)
        if (profileUpdateRequest.dob != currentAccount.dob || profileUpdateRequest.firstName != currentAccount.firstName) {
          scaleResult = updateR4Profile(currentAccount.toGGBTUserProfile())
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

            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED -> {
              dialogQueueService.showToast(
                Toast(
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

  /**
   * Called when profile update is successful.
   */
  private fun onUpdateSuccess() {
    // Show success message
    // You might want to navigate back or show a success dialog
    AppLog.i(TAG, "Profile update completed successfully")
  }

  /**
   * Handles request to exit the profile screen with confirmation dialog.
   */
  private fun onRequestBack() {
    // Check if form has been modified to show appropriate dialog
    val hasChanges = state.value.form.isDirty

    if (hasChanges) {
      dialogQueueService.enqueue(
        com.dmdbrands.gurus.weight.features.common.model.DialogModel.Confirm(
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
      // No changes, exit directly
      navigateBack()
    }
  }

  /**
   * Handles navigation back/exit from profile screen.
   * Call this when user wants to exit the profile screen.
   */
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

    return result.await()
  }
}
