package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGBTWifiConfig
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.ggbluetoothsdk.external.enums.GGWifiState
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleUsers.strings.ScaleUsersStrings
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.components.ConnectionState
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

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
  override val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  private val dashboardService: IDashboardService,
  override val permissionService: GGPermissionService,
  private val accountService: IAccountService
) : ScaleSetupViewmodel<BtWifiScaleSetupState, BtWifiScaleSetupIntent>(
  ggDeviceService, permissionService,
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
      is BtWifiScaleSetupIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      is BtWifiScaleSetupIntent.ExitSetup ->
        onExitSetup(intent.isSetupFinished)

      BtWifiScaleSetupIntent.OpenHelp -> openHelpModal()
      BtWifiScaleSetupIntent.OpenAccucheckModal -> openAccucheckModel()
      is BtWifiScaleSetupIntent.DeleteUser -> deleteUser(intent.user)
      else -> {}
    }
    super.handleIntent(intent)
  }

  init {
    loadScaleInfo()
    observePermissions()
    observeStepChanges()
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    handleIntent(BtWifiScaleSetupIntent.SetScaleSku(sku))
  }

  private fun observePermissions() {
    viewModelScope.launch {
      subscribePermissions().collect {
        handleIntent(BtWifiScaleSetupIntent.SetPermissions(it))
        Log.d("perm", it.toString())
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(sku, it)
        if (areRequiredPermissionsEnabled) {
          handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        }
      }
    }
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
              handleIntent(SetCurrentStep(BtWifiSetupStep.WAKEUP))
            }

            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
              loadDashboardKeys()
            }

            BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
              connectToBluetooth()
            }

            BtWifiSetupStep.DUPLICATES_FOUND -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Save))
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

            BtWifiSetupStep.UPDATE_SETTINGS -> {
              updateSettings()
            }

            BtWifiSetupStep.STEP_ON -> {
              stepOn()
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
      handleIntent(BtWifiScaleSetupIntent.ExitSetup(true))
    } else {
      // For steps that need async operations, the functions will be called automatically
      // by observeStepChanges() when the step changes. Here we just handle the step transition.
      when (currentState.currentStep) {
        BtWifiSetupStep.WAKEUP,
        BtWifiSetupStep.PERMISSIONS,
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        BtWifiSetupStep.GATHERING_NETWORK,
        BtWifiSetupStep.UPDATE_SETTINGS,
        BtWifiSetupStep.CONNECTING_WIFI,
        BtWifiSetupStep.MEASUREMENT,
          -> {
          // These steps have async operations that prevent automatic progression
          // The user shouldn't be able to click Next while these are in progress
          AppLog.d(TAG, "Next clicked on async step ${currentState.currentStep}, but operation should be in progress")
          return // Don't allow manual Next on these steps
        }

        else -> {
          if (currentState.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST) {
            // TODO: IF wifi configured move to BtWifiSetupStep.CUSTOMIZE_SETTINGS else
            //  move to BtWifiSetupStep.WIFI_PASSWORD
          }
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

        else -> {
          val nextIndex = currentState.currentStepIndex - 1
          if (nextIndex < currentState.steps.size) {
            handleIntent(SetCurrentStep(currentState.steps[nextIndex]))
          }
        }
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
        ggDeviceService.cancelWifi(discoveredScale?.toGGBTDevice()!!) {
        }
        handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
      }

      else -> {
        // For other steps, treat skip as next
        onNext()
      }
    }
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    if (isSetupFinished) {
      navigateBack()
    } else {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(discoveredScale?.connectionStatus == BLEStatus.CONNECTED),
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

      BtWifiSetupStep.STEP_ON -> {
        stepOn()
      }

      BtWifiSetupStep.UPDATE_SETTINGS -> {
        updateSettings()
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
    startObservingDevices()

    // Start collecting device scan responses only now
    AppLog.d(TAG, "Starting wake up scale process")

    // Set loading state and prevent automatic next step
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.WAKEUP, ConnectionState.Loading))

    viewModelScope.launch {
      try {
        ggDeviceService.scanForPairing()
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
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        ConnectionState.Loading,
      ),
    )

    try {
      val ggBtDevice = discoveredScale!!.toGGBTDevice()

      ggDeviceService.pairDevice(
        device = ggBtDevice,
      ) {
        when (it) {
          GGUserActionResponseType.CREATION_COMPLETED -> {
            viewModelScope.launch {
              handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                  BtWifiSetupStep.CONNECTING_BLUETOOTH,
                  ConnectionState.Success,
                ),
              )
              delay(1000)
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
            }
          }

          GGUserActionResponseType.CREATION_FAILED -> {
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                ConnectionState.Error,
              ),
            )
            handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_001"))
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          }

          GGUserActionResponseType.DUPLICATE_USER_ERROR -> {
            ggDeviceService.deleteAccount(device = ggBtDevice) { deleteResponse ->
              when (deleteResponse) {
                GGUserActionResponseType.DELETE_COMPLETED -> {
                  connectToBluetooth()
                }

                else -> null
              }
            }
          }

          else -> null

        }
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
        ggDeviceService.getWifiList(discoveredScale!!.toGGBTDevice()) {
          AppLog.d(TAG, "Network gathering successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.GATHERING_NETWORK,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(BtWifiScaleSetupIntent.SetWifiList(it.wifi))
          handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
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
  private fun connectToWifi(ssid: String = "", password: String = "") {
    AppLog.d(TAG, "Starting wifi connection process")
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_WIFI,
        ConnectionState.Loading,
      ),
    )
    try {
      ggDeviceService.setupWifi(
        discoveredScale!!.toGGBTDevice(),
        GGBTWifiConfig(ssid, password),
      ) {
        if (it.wifiState == GGWifiState.GG_WIFI_STATE_CONNECTED.name) {
          AppLog.d(TAG, "Wifi connection successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.CONNECTING_WIFI,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
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

  private fun stepOn() {
    AppLog.d(TAG, "Starting wifi connection process")
    ggDeviceService.syncDevices(listOf(discoveredScale!!.toGGBTDevice()))
    startObservingEntries()
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.MEASUREMENT,
        ConnectionState.Loading,
      ),
    )
  }

  private fun collectMeasurement() {

    AppLog.d(TAG, "Starting wifi connection process")

    viewModelScope.launch {
      try {
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

        // Move to next step if there are more steps
        val nextIndex = currentState.currentStepIndex + 1
        if (nextIndex < currentState.steps.size) {
          handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(currentState.steps[nextIndex] as BtWifiSetupStep))
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

  private fun updateSettings() {
    AppLog.d(TAG, "Starting settings update process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.UPDATE_SETTINGS,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Simulate settings update process
        delay(4000) // Replace with actual settings update logic

        // TODO: Replace with actual updateSettings(R4 scale Preference and Dashboard Metrics) logic
        val settingsUpdated = true

        if (settingsUpdated) {
          AppLog.d(TAG, "Settings update successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.UPDATE_SETTINGS,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))

          // Check if this is the last step
          val currentState = state.value

          // Move to next step if there are more steps
          val nextIndex = currentState.currentStepIndex + 1
          if (nextIndex < currentState.steps.size) {
            handleIntent(SetCurrentStep(currentState.steps[nextIndex]))
          }
        } else {
          AppLog.w(TAG, "Settings update failed")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.UPDATE_SETTINGS,
              ConnectionState.Error,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetErrorCode("UPDATE_001"))
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during settings update", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.UPDATE_SETTINGS,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("UPDATE_002"))
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

  private fun loadDashboardKeys() {
    viewModelScope.launch {
      dashboardService.getVisibleKeys().collect { dashboardKeys ->
        handleIntent(BtWifiScaleSetupIntent.SetDashboardKeys(dashboardKeys))
      }
    }
  }

  /**
   * Callback when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
    val ggDeviceDetail = response.data
    val device = Device(
      device = ggDeviceDetail,
    )
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (ggDeviceDetail.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value) {
          discoveredScale = device
          AppLog.d(TAG, "Wake up successful, proceeding to next step")
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
          stopObservingDevices()
        }
      }

      else -> null
    }
  }

  override fun onEntryResponse(response: GGScanResponse.Entry) {
    response.data
    when (response.type) {
      GGScanResponseType.SINGLE_ENTRY -> {
        collectMeasurement()
      }

      GGScanResponseType.MULTI_ENTRIES -> {
        collectMeasurement()
      }

      else -> null
    }
  }

  /**
   * Requests a specific permission using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    viewModelScope.launch {
      try {
        permissionService.requestPermission(permissionType)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e.toString())
      }
    }
  }

  private fun deleteUser(user: GGBTUser) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleUsersStrings.DeleteUserAlert.Title,
        message = ScaleUsersStrings.DeleteUserAlert.Message(user.name),
        confirmText = ScaleUsersStrings.DeleteUserAlert.Delete,
        cancelText = ScaleUsersStrings.DeleteUserAlert.Back,
        onConfirm = {
          // Delete user and update the list
          state.value.usernameForm.username.reset()
        },
      ),
    )
  }
}
