package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.WifiMacAddressStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the WifiScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = WifiScaleSetupViewModel.Factory::class,
)
class WifiScaleSetupViewModel
  @AssistedInject
  constructor(
    @Assisted private val sku: String,
  ) : BaseIntentViewModel<WifiScaleSetupState, WifiScaleSetupIntent>(
      reducer = WifiScaleSetupReducer(),
    ) {
    @AssistedFactory
    interface Factory {
      fun create(sku: String): WifiScaleSetupViewModel
    }

    private val TAG = "WifiScaleSetupViewModel"

    override fun provideInitialState(): WifiScaleSetupState = WifiScaleSetupState()

    override fun handleIntent(intent: WifiScaleSetupIntent) {
      super.handleIntent(intent)
      when (intent) {
        WifiScaleSetupIntent.Next -> onNext()
        WifiScaleSetupIntent.Back -> onBack()
        WifiScaleSetupIntent.Skip -> onSkip()
        is WifiScaleSetupIntent.ExitSetup ->
          onExitSetup(
            intent.isSetupFinished,
            intent.isConnected,
          )
        is WifiScaleSetupIntent.OnCopyMacAddress -> onCopyMacAddress(intent.isCopied)
        WifiScaleSetupIntent.OpenHelp -> openHelpModal()
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
      handleIntent(WifiScaleSetupIntent.SetScaleSku(sku))
    }

    /**
     * Handles moving to the next step in the setup process.
     */
    private fun onNext() {
      val currentState = state.value
      AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

      if (currentState.isLastStep) {
        AppLog.d(TAG, "Reached last step, completing setup")
        handleIntent(WifiScaleSetupIntent.ExitSetup(true, true))
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

    private fun onCopyMacAddress(isCopied: Boolean) {
      showToast(
        message = if (isCopied) WifiMacAddressStrings.Toast.Success
        else WifiMacAddressStrings.Toast.Error,
      )
    }
    private fun showToast(message: String) {
      dialogQueueService.showToast(
        Toast(
          title = null,
          message = message,
          action = null,
        ),
      )
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
