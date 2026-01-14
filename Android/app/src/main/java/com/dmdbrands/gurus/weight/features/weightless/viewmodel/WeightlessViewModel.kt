package com.dmdbrands.gurus.weight.features.weightless.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.weightless.helper.WeightlessHelper.processStoredWeightToDisplay
import com.dmdbrands.gurus.weight.features.weightless.helper.WeightlessHelper.toWeightlessWeight
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessFormControls
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessIntent
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessReducer
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessState
import com.dmdbrands.gurus.weight.features.weightless.strings.WeightlessStrings
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
      weightUnit = WeightUnit.LB,
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
    val isMetric = currentAccount.weightUnit == WeightUnit.KG
    val targetUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB

    // Get current weightless settings (matching Angular logic)
    val isWeightlessOn = currentAccount.isWeightlessOn ?: false
    val currentWeightlessWeight = currentAccount.weightlessWeight
    val defaultInitialWeight = 0.0 // Default display weights

    val displayWeight =
      if (isWeightlessOn && currentWeightlessWeight != null) {
        // Use exact same pattern as GoalViewModel - process stored weight to display
        processStoredWeightToDisplay(currentWeightlessWeight.toDouble(), targetUnit)
      } else {
        // Use default initial weight
        defaultInitialWeight
      }

    // Create form controls with proper initial values - exact same pattern as GoalViewModel
    val weightValidators =
      if (isWeightlessOn) {
        listOf(
          com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.required(),
          com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.weightValidator(targetUnit),
        )
      } else {
        emptyList()
      }

    val newState =
      WeightlessState(
        form =
          FormGroup(
            WeightlessFormControls(
              weightlessWeight =
                FormControl.create(
                  initialValue = if(displayWeight.toString() == "0.0") "" else displayWeight.toInt().toString(),
                  validators = weightValidators,
                ),
            ),
          ),
        isWeightlessOn = isWeightlessOn,
        hasToggleChanged = false,
        weightUnit = targetUnit,
        isMetric = isMetric,
        account = currentAccount, // Added to match GoalViewModel pattern
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

    val account = state.value.account ?: return
    val isWeightlessOn = state.value.isWeightlessOn

    AppLog.d(tag, "Weightless settings: enabled=$isWeightlessOn")
    viewModelScope.launch {
      try {
        val weightlessWeight = state.value.form.controls.toWeightlessWeight(
          fromUnit = account.weightUnit,
          toUnit = WeightUnit.LB, // We store weights in LB format
        )

        userSettingsService.toggleWeightlessSetting(
          isWeightlessOn = isWeightlessOn,
          weightlessWeight = weightlessWeight,
        )
        handleIntent(WeightlessIntent.Success)
        AppLog.i(tag, "Weightless settings saved successfully")
      } catch (e: Exception) {
        AppLog.e(tag, "Failed to save weightless settings", e)
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
          title = WeightlessStrings.SuccessTitle,
          message = WeightlessStrings.SuccessMessage,
          action = null,
        ),
      )
      navigationService.navigateBack(null)
      AppLog.i(tag, "Weightless settings updated successfully")
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
        AppLog.e(tag, "Failed to navigate back from weightless screen", e)
      }
    }
  }
}
