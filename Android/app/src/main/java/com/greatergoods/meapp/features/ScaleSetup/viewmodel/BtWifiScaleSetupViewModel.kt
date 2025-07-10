package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the BtWifiScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = BtWifiScaleSetupViewModel.Factory::class,
)
class BtWifiScaleSetupViewModel
  @AssistedInject
  constructor(
    @Assisted private val sku: String,
  ) : BaseIntentViewModel<BtWifiScaleSetupState, BtWifiScaleSetupIntent>(
      reducer = BtWifiScaleSetupReducer(),
    ) {
    @AssistedFactory
    interface Factory {
      fun create(sku: String): BtWifiScaleSetupViewModel
    }

    private val TAG = "BtWifiScaleSetupViewModel"

    override fun provideInitialState(): BtWifiScaleSetupState = BtWifiScaleSetupState()

    override fun handleIntent(intent: BtWifiScaleSetupIntent) {
      super.handleIntent(intent)
      when (intent) {
        BtWifiScaleSetupIntent.Next -> onNext()
        BtWifiScaleSetupIntent.Back -> onBack()
        BtWifiScaleSetupIntent.Skip -> onSkip()
        is BtWifiScaleSetupIntent.ExitSetup ->
          onExitSetup(
            intent.isSetupFinished,
            intent.isConnected,
          )

        BtWifiScaleSetupIntent.OpenHelp -> openHelpModal()
        else -> {}
      }
    }

    init {
      loadScaleInfo()
    }

    /**
     * Loads scale information based on the provided SKU.
     */
    private fun loadScaleInfo() {
      AppLog.d(TAG, "Loading scale info for SKU: $sku")
      handleIntent(BtWifiScaleSetupIntent.SetScaleSku(sku))
    }

    /**
     * Handles moving to the next step in the setup process.
     */
    private fun onNext() {
      val currentState = state.value
      AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

      if (currentState.isLastStep) {
        AppLog.d(TAG, "Reached last step, completing setup")
        handleIntent(BtWifiScaleSetupIntent.ExitSetup(true, true))
      } else {
        AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.currentStep}")
      }
    }

    /**
     * Handles moving to the previous step in the setup process.
     */
    private fun onBack() {
      val currentState = state.value
      AppLog.d(TAG, "Moving to previous step from: ${currentState.currentStep}")

      if (currentState.isFirstStep) {
        AppLog.d(TAG, "At first step, navigating back")
        navigateTo(AppRoute.AccountSettings.AddEditScales)
      } else {
        AppLog.d(TAG, "After Back intent - new currentStep: ${currentState.currentStep}")
      }
    }

    /**
     * Handles skipping the current step.
     */
    private fun onSkip() {
      AppLog.d(TAG, "Skipping current step: ${state.value.currentStep}")
      // For now, treat skip as next
      onNext()
    }

    private fun onExitSetup(
      isSetupFinished: Boolean,
      isConnected: Boolean,
    ) {
      if (isSetupFinished) {
        navigateBack()
      } else {
        dialogQueueService.enqueue(
          DialogModel.Confirm(
            title = ScaleSetupStrings.ExitSetupAlert.Title,
            message = ScaleSetupStrings.ExitSetupAlert.Message(isConnected),
            confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
            cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
            onConfirm = {
              navigateBack()
            },
          ),
        )
      }
    }

    /**
     * Opens the Help modal.
     */
    private fun openHelpModal() {
      dialogQueueService.enqueue(
        DialogModel.Custom(
          contentKey = DialogType.HelpPopup,
        ),
      )
    }

    private fun navigateTo(route: AppRoute) {
      viewModelScope.launch {
        navigationService.navigateTo(route)
      }
    }

    /**
     * Navigates back from the setup screen.
     */
    private fun navigateBack() {
      viewModelScope.launch {
        try {
          navigationService.navigateBack()
          AppLog.d(TAG, "Successfully navigated back from scale setup")
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to navigate back from scale setup", e.toString())
        }
      }
    }
  }
