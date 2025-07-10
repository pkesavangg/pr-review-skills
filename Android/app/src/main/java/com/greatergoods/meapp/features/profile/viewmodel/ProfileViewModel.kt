package com.greatergoods.meapp.features.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.profile.model.ProfileFormControls
import com.greatergoods.meapp.features.profile.model.ProfileIntent
import com.greatergoods.meapp.features.profile.model.ProfileReducer
import com.greatergoods.meapp.features.profile.model.ProfileState
import com.greatergoods.meapp.features.profile.strings.ProfileStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Profile screen. Handles form state, validation, profile update logic.
 * @property accountService Service for account operations.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountService: IAccountService,
) : BaseIntentViewModel<ProfileState, ProfileIntent>(
    reducer = ProfileReducer(),
) {

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
                                DateTimeValue.getEpochMillisFromIsoString(currentAccount.dob)
                            )
                        )
                    )
                    AppLog.i("ProfileViewModel", "Profile data loaded successfully")
                } else {
                    handleIntent(ProfileIntent.Error(ProfileStrings.Error.MessageGeneric))
                    AppLog.w("ProfileViewModel", "No current account found")
                }
            } catch (e: Exception) {
                AppLog.e("ProfileViewModel", "Failed to load profile", e.toString())
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
            //its an flow
            val currentAccount = accountService.getCurrentAccount()
            if(currentAccount == null){
                return@launch
            }
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
              accountService.updateProfile(profileUpdateRequest)
              handleIntent(ProfileIntent.Success)
              navigateBack()
              AppLog.i("ProfileViewModel", "Profile updated successfully")
            } catch (e: Exception) {
                AppLog.e("ProfileViewModel", "Profile update failed", e.toString())
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
        AppLog.i("ProfileViewModel", "Profile update completed successfully")
    }

    /**
     * Handles request to exit the profile screen with confirmation dialog.
     */
    private fun onRequestBack() {
        // Check if form has been modified to show appropriate dialog
        val hasChanges = state.value.form.isDirty

        if (hasChanges) {
            dialogQueueService.enqueue(
                com.greatergoods.meapp.features.common.model.DialogModel.Confirm(
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
                AppLog.d("ProfileViewModel", "Successfully navigated back from profile")
            } catch (e: Exception) {
                AppLog.e("ProfileViewModel", "Failed to navigate back from profile", e.toString())
            }
        }
    }
}
