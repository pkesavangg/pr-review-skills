package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.ConnectionState
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    when (intent) {
      BtWifiScaleSetupIntent.Next -> onNext()
      BtWifiScaleSetupIntent.Back -> onBack()
      BtWifiScaleSetupIntent.Skip -> onSkip()
      BtWifiScaleSetupIntent.TryAgain -> onTryAgain()
      BtWifiScaleSetupIntent.RefreshNetworks -> onRefreshNetworks()
      BtWifiScaleSetupIntent.HandlePasswordNetworkStatus -> handlePasswordNetworkStatus()
      is BtWifiScaleSetupIntent.ExitSetup ->
        onExitSetup(
          intent.isSetupFinished,
          intent.isConnected,
        )

      BtWifiScaleSetupIntent.OpenHelp -> openHelpModal()
      BtWifiScaleSetupIntent.OpenAccucheckModal -> openAccucheckModel()
      else -> {}
    }
    super.handleIntent(intent)
  }

  init {
    loadScaleInfo()
    observeStepChanges()
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    handleIntent(BtWifiScaleSetupIntent.SetScaleSku(sku))
  }

  /**
   * Observes step changes and triggers appropriate functions when steps change.
   */
  private fun observeStepChanges() {
    viewModelScope.launch {
      var previousStep: BtWifiSetupStep? = null

      state.collect { currentState ->
        val currentStep = currentState.currentStep

        // Only trigger if step actually changed
        if (previousStep != null && previousStep != currentStep) {
          AppLog.d(TAG, "Step changed from $previousStep to $currentStep")

          // Call appropriate function based on the new step
          when (currentStep) {
            BtWifiSetupStep.WAKEUP -> {
              wakeUpScale()
            }

            BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
              connectToBluetooth()
            }

            BtWifiSetupStep.GATHERING_NETWORK -> {
              gatherNetworks()
            }

            BtWifiSetupStep.WIFI_PASSWORD -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Connect))
            }

            BtWifiSetupStep.CONNECTING_WIFI -> {
              connectToWifi()
            }

            BtWifiSetupStep.PERMISSIONS -> {
              handlePermissions()
            }

            BtWifiSetupStep.MEASUREMENT -> {
              collectMeasurement()
            }

            else -> {
              // No automatic action needed for other steps
            }
          }
        }

        previousStep = currentStep
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
      handleIntent(BtWifiScaleSetupIntent.ExitSetup(true, true))
    } else {
      // For steps that need async operations, the functions will be called automatically
      // by observeStepChanges() when the step changes. Here we just handle the step transition.
      when (currentState.currentStep) {
        BtWifiSetupStep.WAKEUP,
        BtWifiSetupStep.PERMISSIONS,
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        BtWifiSetupStep.GATHERING_NETWORK,
        BtWifiSetupStep.CONNECTING_WIFI,
        BtWifiSetupStep.MEASUREMENT,
          -> {
          // These steps have async operations that prevent automatic progression
          // The user shouldn't be able to click Next while these are in progress
          AppLog.d(TAG, "Next clicked on async step ${currentState.currentStep}, but operation should be in progress")
          return // Don't allow manual Next on these steps
        }

        else -> {
          // For other steps (like SCALE_INFO, AVAILABLE_WIFI_LIST), let the normal flow continue
          // The base class will handle the intent and call the reducer
        }
      }
      AppLog.d(TAG, "After Next intent - new currentStep: ${state.value.currentStep}")
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
      when (currentState.currentStep) {
        BtWifiSetupStep.WIFI_PASSWORD,
        BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
          handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
        }

        else -> {}
      }
      // Let the base class handle the Back intent through the reducer
      AppLog.d(TAG, "Moving to previous step - will be handled by reducer")
    }
  }

  /**
   * Handles skipping the current step.
   */
  private fun onSkip() {
    val currentState = state.value
    AppLog.d(TAG, "Skipping current step: ${currentState.currentStep}")

    when (currentState.currentStep) {
      BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
        // Skip to CUSTOMIZE_SETTINGS
        handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
      }

      else -> {
        // For other steps, treat skip as next
        onNext()
      }
    }
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
   * Handles refreshing networks - goes back to GATHERING_NETWORK step.
   */
  private fun onRefreshNetworks() {
    AppLog.d(TAG, "Refreshing networks, going back to GATHERING_NETWORK")
    // Let the base class handle the RefreshNetworks intent through the reducer
  }

  /**
   * Handles password network status toggle by dynamically adding/removing validation.
   * Reads the current status from the wifiPasswordForm.noPasswordNetwork control.
   */
  private fun handlePasswordNetworkStatus() {
    val currentState = state.value
    val isNoPasswordNetwork = currentState.wifiPasswordForm.noPasswordNetwork.value
    AppLog.d(TAG, "Handling password network status, isNoPasswordNetwork: $isNoPasswordNetwork")
    if (isNoPasswordNetwork) {
      // No password network - remove required validation
      currentState.wifiPasswordForm.password.removeValidator("required")
    } else {
      // Password network - add required validation
      currentState.wifiPasswordForm.password.addValidator(
        com.greatergoods.meapp.features.common.helper.form.FormValidations.required(),
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

  /**
   * Handles try again action. Clears error and restarts the current step's function.
   */
  private fun onTryAgain() {
    val currentState = state.value
    AppLog.d(TAG, "Try again for step: ${currentState.currentStep}")

    // Clear error state first (manually call the individual intents to avoid infinite loop)
    handleIntent(BtWifiScaleSetupIntent.SetErrorCode(null))
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))

    // Restart the appropriate function based on current step
    when (currentState.currentStep) {
      BtWifiSetupStep.WAKEUP -> {
        wakeUpScale()
      }

      BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
        connectToBluetooth()
      }

      BtWifiSetupStep.GATHERING_NETWORK -> {
        gatherNetworks()
      }

      BtWifiSetupStep.CONNECTING_WIFI -> {
        connectToWifi()
      }

      BtWifiSetupStep.PERMISSIONS -> {
        handlePermissions()
      }

      BtWifiSetupStep.STEP_ON -> {
        stepOn()
      }

      BtWifiSetupStep.MEASUREMENT -> {
        collectMeasurement()
      }

      else -> {
        AppLog.w(TAG, "Try again called on step that doesn't support retry: ${currentState.currentStep}")
      }
    }
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

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {
    AppLog.d(TAG, "Starting wake up scale process")

    // Set loading state and prevent automatic next step
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.WAKEUP, ConnectionState.Loading))

    viewModelScope.launch {
      try {
        // Simulate wake up process
        delay(3000) // Replace with actual wake up logic

        // TODO: Replace with actual wake up logic
        val wakeUpSuccessful = true // Set to false to test try again functionality

        if (wakeUpSuccessful) {
          AppLog.d(TAG, "Wake up successful, proceeding to next step")
          // Don't update connection state to Success here as per requirement
          // Just allow proceeding to next step
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
        } else {
          AppLog.w(TAG, "Wake up failed")
          handleIntent(BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.WAKEUP, ConnectionState.Error))
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WAKEUP_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wake up process", e.toString())
        handleIntent(BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.WAKEUP, ConnectionState.Error))
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WAKEUP_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
      }
    }
  }

  /**
   * Handles bluetooth connection process.
   */
  private fun connectToBluetooth() {
    AppLog.d(TAG, "Starting bluetooth connection process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate bluetooth connection
        delay(2000) // Replace with actual bluetooth connection logic

        // TODO: Replace with actual bluetooth connection logic
        val bluetoothConnected = true

        if (bluetoothConnected) {
          AppLog.d(TAG, "Bluetooth connection successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.CONNECTING_BLUETOOTH,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
        } else {
          AppLog.w(TAG, "Bluetooth connection failed")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.CONNECTING_BLUETOOTH,
              ConnectionState.Error,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during bluetooth connection", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.CONNECTING_BLUETOOTH,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
      }
    }
  }

  /**
   * Handles permissions step.
   */
  private fun handlePermissions() {
    AppLog.d(TAG, "Handling permissions step")
    // For now, just proceed to next step
    val currentState = state.value
    val nextIndex = currentState.currentStepIndex + 1
    if (nextIndex < currentState.steps.size) {
      handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(currentState.steps[nextIndex]))
    }
  }

  /**
   * Handles network gathering process.
   */
  private fun gatherNetworks() {
    AppLog.d(TAG, "Starting network gathering process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.GATHERING_NETWORK,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate network gathering
        delay(2500) // Replace with actual network gathering logic

        // TODO: Replace with actual network gathering logic
        val networksGathered = true

        if (networksGathered) {
          AppLog.d(TAG, "Network gathering successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.GATHERING_NETWORK,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
        } else {
          AppLog.w(TAG, "Network gathering failed")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.GATHERING_NETWORK,
              ConnectionState.Error,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("NET_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during network gathering", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.GATHERING_NETWORK,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("NET_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
      }
    }
  }

  /**
   * Handles wifi connection process.
   */
  private fun connectToWifi() {
    AppLog.d(TAG, "Starting wifi connection process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_WIFI,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate wifi connection
        delay(4000) // Replace with actual wifi connection logic

        // TODO: Replace with actual wifi connection logic
        val wifiConnected = true

        if (wifiConnected) {
          AppLog.d(TAG, "Wifi connection successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.CONNECTING_WIFI,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          // Check if this is the last step, if so complete setup
          val currentState = state.value
          if (currentState.isLastStep) {
            handleIntent(BtWifiScaleSetupIntent.ExitSetup(true, true))
          } else {
            // Move to next step if there are more steps
            val nextIndex = currentState.currentStepIndex + 1
            if (nextIndex < currentState.steps.size) {
              handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(currentState.steps[nextIndex]))
            }
          }
        } else {
          AppLog.w(TAG, "Wifi connection failed")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.CONNECTING_WIFI,
              ConnectionState.Error,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WIFI_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wifi connection", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.CONNECTING_WIFI,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WIFI_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
      }
    }
  }

  private fun stepOn() {
    AppLog.d(TAG, "Starting wifi connection process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.MEASUREMENT,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate Step on
        delay(5000) // Replace with actual step on logic

        // TODO: Replace with actual step on logic
        val steppedOn = true

        if (steppedOn) {
          AppLog.d(TAG, "Step on successful")
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          // Check if this is the last step, if so complete setup
          val currentState = state.value

          // Move to next step if there are more steps
          val nextIndex = currentState.currentStepIndex + 1
          if (nextIndex < currentState.steps.size) {
            handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(currentState.steps[nextIndex]))
          }
        } else {
          AppLog.w(TAG, "Step on failed")
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during step on", e.toString())
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
      }
    }
  }

  private fun collectMeasurement() {
    AppLog.d(TAG, "Starting wifi connection process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.MEASUREMENT,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate wifi connection
        delay(4000) // Replace with actual wifi connection logic

        // TODO: Replace with actual collect Measurement logic
        val wifiConnected = true

        if (wifiConnected) {
          AppLog.d(TAG, "collect Measurement successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.MEASUREMENT,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          // Check if this is the last step, if so complete setup
          val currentState = state.value
          if (currentState.isLastStep) {
            handleIntent(BtWifiScaleSetupIntent.ExitSetup(true, true))
          } else {
            // Move to next step if there are more steps
            val nextIndex = currentState.currentStepIndex + 1
            if (nextIndex < currentState.steps.size) {
              handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(currentState.steps[nextIndex]))
            }
          }
        } else {
          AppLog.w(TAG, "Measurement collection failed")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.MEASUREMENT,
              ConnectionState.Error,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("MEASURE_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during measurement collection", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.MEASUREMENT,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("MEASURE_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
      }
    }
  }

  private fun openAccucheckModel() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.AccucheckModal,
      ),
    )
  }
}
