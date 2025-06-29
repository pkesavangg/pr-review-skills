package com.greatergoods.meapp.features.weightless.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IUserSettingsService
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.weightless.model.WeightlessFormControls
import com.greatergoods.meapp.features.weightless.model.WeightlessIntent
import com.greatergoods.meapp.features.weightless.model.WeightlessReducer
import com.greatergoods.meapp.features.weightless.model.WeightlessState
import com.greatergoods.meapp.features.weightless.strings.WeightlessStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Weightless screen. Handles form state, validation, weightless logic, and navigation.
 * @property dialogUtility Service for showing dialogs.
 * @property accountService Service for account operations.
 * @property userSettingsService Service for user settings operations.
 */
@HiltViewModel
class WeightlessViewModel
@Inject
constructor(
    private val dialogUtility: IDialogUtility,
    private val accountService: IAccountService,
    private val userSettingsService: IUserSettingsService,
) : BaseIntentViewModel<WeightlessState, WeightlessIntent>(
    reducer = WeightlessReducer(),
) {
    private val tag = "WeightlessViewModel"

    override fun provideInitialState(): WeightlessState {
        // Initialize with defaults since we'll load data in init block
        return WeightlessState(
            form = FormGroup(WeightlessFormControls.create()),
            isWeightlessOn = false,
            hasToggleChanged = false,
            weightUnit = "lbs",
            isMetric = false,
        )
    }

    init {
        // Load current account data
        viewModelScope.launch {
            accountService.activeAccountFlow.collect { account ->
                account?.let { updateStateWithAccount(it) }
            }
        }
    }

    private fun updateStateWithAccount(currentAccount: Account) {
        val isMetric = currentAccount.weightUnit?.value == "kg"

        // Get current weightless settings (matching Angular logic)
        val isWeightlessOn = currentAccount.isWeightlessOn ?: false
        val currentWeightlessWeight = currentAccount.weightlessWeight
        val defaultInitialWeight = if (isMetric) 680 else 1500 // stored as tenths
        val displayWeight = if (isWeightlessOn && currentWeightlessWeight != null) {
            // Use existing weightless weight converted to display
            ConversionTools.convertStoredToDisplay(currentWeightlessWeight.toDouble(), isMetric)
        } else {
            // Use default initial weight converted to display
            ConversionTools.convertStoredToDisplay(defaultInitialWeight.toDouble(), isMetric)
        }
        // Create form controls with proper initial values
        val weightValidators = if (isWeightlessOn) {
            listOf(com.greatergoods.meapp.features.common.helper.form.FormValidations.required())
        } else {
            emptyList()
        }
        val formattedWeight = String.format("%.1f", displayWeight)
        val newState = WeightlessState(
            form = FormGroup(
                WeightlessFormControls(
                    weightlessWeight = FormControl.create(
                        initialValue = formattedWeight,
                        validators = weightValidators,
                    ),
                ),
            ),
            isWeightlessOn = isWeightlessOn,
            hasToggleChanged = false,
            weightUnit = if (isMetric) "kg" else "lbs",
            isMetric = isMetric,
        )
        _state.value = newState
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: WeightlessIntent) {
        super.handleIntent(intent)
        when (intent) {
            is WeightlessIntent.Submit -> onSubmit()
            is WeightlessIntent.OpenHelpModal -> openHelpModal()
            is WeightlessIntent.OnBack -> onBack()
            is WeightlessIntent.Success -> onSuccess()
            else -> null
        }
    }

    /**
     * Handles the weightless form submission. Validates the form, shows loading, and attempts to save.
     */
    private fun onSubmit() {
        dialogQueueService.showLoader(
            message = WeightlessStrings.LoaderMessage,
        )

        val isWeightlessOn = state.value.isWeightlessOn
        val weightlessWeightDisplay = state.value.form.controls.weightlessWeight.value
        val isMetric = state.value.isMetric
        AppLog.d(tag, "Weightless settings: enabled=$isWeightlessOn, weight=$weightlessWeightDisplay")
        viewModelScope.launch {
            try {
                val weightlessWeight = if (isWeightlessOn) {
                    ConversionTools.convertDisplayToStored(weightlessWeightDisplay.toDouble(), isMetric).toDouble()
                } else null

                userSettingsService.toggleWeightlessSetting(
                    isWeightlessOn = isWeightlessOn,
                    weightlessWeight = weightlessWeight,
                )
                handleIntent(WeightlessIntent.Success)
                AppLog.i(tag, "Weightless settings saved successfully")
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to save weightless settings", e.toString())
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Opens the Help modal with weightless information.
     */
    private fun openHelpModal() {
        AppLog.d(tag, "Opening weightless help modal")
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = WeightlessStrings.WeightlessInfoTitle,
                message = WeightlessStrings.WeightlessInfoMessage,
                onDismiss = {},
            ),
        )
    }

    /**
     * Handles back navigation with unsaved changes check.
     */
    private fun onBack() {
        val hasChanges = state.value.form.isDirty || state.value.hasToggleChanged

        if (hasChanges) {
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = AppPopupStrings.UnsavedChanges.Title,
                    message = AppPopupStrings.UnsavedChanges.Message,
                    confirmText = AppPopupStrings.UnsavedChanges.Exit,
                    cancelText = AppPopupStrings.UnsavedChanges.Return,
                    onConfirm = {
                        navigateBack()
                    },
                ),
            )
        } else {
            navigateBack()
        }
    }

    private fun onSuccess() {
        viewModelScope.launch {
            dialogQueueService.showToast(
                Toast(
                    title = WeightlessStrings.title,
                    message = WeightlessStrings.SuccessMessage,
                    action = null,
                ),
            )
            navigationService.navigateBack(null)
            AppLog.i("ChangePasswordViewModel", "Password changed successfully")
        }
    }

    /**
     * Navigates back to the previous screen.
     */
    private fun navigateBack() {
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to navigate back from weightless screen", e.toString())
            }
        }
    }
}
