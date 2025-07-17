package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.AppsyncScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.AppsyncScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.AppsyncScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the AppsyncScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = AppsyncScaleSetupViewModel.Factory::class,
)
class AppsyncScaleSetupViewModel
@AssistedInject
constructor(
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  @Assisted private val sku: String,
) : BaseIntentViewModel<AppsyncScaleSetupState, AppsyncScaleSetupIntent>(
  reducer = AppsyncScaleSetupReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(sku: String): AppsyncScaleSetupViewModel
  }

  private val TAG = "AppsyncScaleSetupViewModel"

  override fun provideInitialState(): AppsyncScaleSetupState = AppsyncScaleSetupState()

  override fun handleIntent(intent: AppsyncScaleSetupIntent) {
    super.handleIntent(intent)
    when (intent) {
      AppsyncScaleSetupIntent.Next -> onNext()
      AppsyncScaleSetupIntent.Back -> onBack()
      AppsyncScaleSetupIntent.Skip -> onSkip()
      is AppsyncScaleSetupIntent.ExitSetup ->
        onExitSetup(
          intent.isSetupFinished,
          intent.isConnected,
        )

      AppsyncScaleSetupIntent.OpenHelp -> openHelpModal()
      is AppsyncScaleSetupIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      else -> {}
    }
  }

  init {
    loadScaleInfo()
    observePermissions()
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    handleIntent(AppsyncScaleSetupIntent.SetScaleSku(sku))
  }

  private fun observePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect {
        handleIntent(AppsyncScaleSetupIntent.SetPermissions(it))
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(it, sku = sku)
        if (areRequiredPermissionsEnabled && state.value.currentStep != AppsyncScaleSetupStep.PERMISSIONS) {
          handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.PERMISSIONS))
        }
      }
    }
  }

  /**
   * Handles moving to the next step in the setup process.
   */
  private fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      handleIntent(AppsyncScaleSetupIntent.ExitSetup(true, true))
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
    if (isSetupFinished || state.value.isLastStep) {
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
        params =
          mapOf(
            "showGuide" to true,
            "onGuideClick" to {
              openProductGuide()
              dialogQueueService.dismissCurrent()
            },
          ),
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

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    openInAppBrowser(url)
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e.toString())
      }
    }
  }
}
