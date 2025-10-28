package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.NameUtils
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGBTWifiConfig
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGLiveDataResponse
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.ggbluetoothsdk.external.enums.GGWifiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
  @Assisted("sku") private val sku: String,
  @Assisted("broadcastId") private val broadcastId: String? = null,
  @Assisted("initialStep") private val initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  @Assisted("userList") private val userList: List<GGBTUser>? = null,
  override val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  private val entryService: IEntryService,
  private val deviceRepository: IDeviceRepository,
  private val dashboardService: IDashboardService,
  override val permissionService: GGPermissionService,
  override val connectivityObserver: IConnectivityObserver,
  private val dialogUtility: IDialogUtility,
  private val accountService: IAccountService,
  private val bluetoothPreferencesService: BluetoothPreferencesService,
) : ScaleSetupViewmodel<BtWifiScaleSetupState, BtWifiScaleSetupIntent>(
  ggDeviceService, connectivityObserver, permissionService,
  reducer = BtWifiScaleSetupReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted("sku") sku: String,
      @Assisted("broadcastId") broadcastId: String? = null,
      @Assisted("initialStep") initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
      @Assisted("userList") userList: List<GGBTUser>? = null
    ): BtWifiScaleSetupViewModel
  }

  private val TAG = "BtWifiScaleSetupViewModel"
  private var isScaleConnected: Boolean = false
  private var accountId: String? = null
  private var pairingTimeoutJob: kotlinx.coroutines.Job? = null
  private var bluetoothConnectionTimeoutJob: kotlinx.coroutines.Job? = null
  private var wifiConnectionTimeoutJob: kotlinx.coroutines.Job? = null
  private var updateSettingsTimeoutJob: kotlinx.coroutines.Job? = null
  private var measurementTimeoutJob: kotlinx.coroutines.Job? = null
  private var wifiMac: String? = null
  private var wifiSsid: String? = null

  // Timeout constant - 5 minutes for all operations
  private val operationTimeout: Long = 5 * 60 * 1000L // 5 minutes
  override fun provideInitialState(): BtWifiScaleSetupState = BtWifiScaleSetupState()

  override fun handleIntent(intent: BtWifiScaleSetupIntent) {
    when (intent) {
      is BtWifiScaleSetupIntent.ReplaceAccount -> {
        replaceAccount(intent.userName)
      }

      BtWifiScaleSetupIntent.ShowRestoreAccountAlert -> {
        showRestoreAccountAlert()
      }

      BtWifiScaleSetupIntent.Next -> onNext()
      BtWifiScaleSetupIntent.Back -> onBack()
      BtWifiScaleSetupIntent.Skip -> onSkip()
      BtWifiScaleSetupIntent.TryAgain -> onTryAgain()
      is BtWifiScaleSetupIntent.UpdateSettings -> updateDevicePreferences(intent.dashboardKeys, intent.preferences)
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
      BtWifiScaleSetupIntent.ClearWifiPasswordForm -> clearWifiPasswordForm()
      else -> {}
    }
    super.handleIntent(intent)
  }

  init {
    // Set setup in progress when initialization starts
    deviceService.setSetupInProgress(true)
    loadScaleInfo()
    observePermissions()
    observeStepChanges()
    initializeSetup()
    viewModelScope.launch {
      accountId = accountService.activeAccountFlow.first()?.id
    }
  }

  private fun initializeSetup() {
    viewModelScope.launch {
      // Set the initial step in state for button visibility logic
      handleIntent(BtWifiScaleSetupIntent.SetInitialStep(initialStep))
      // Initialize username form with active account name
      initializeUsernameForm()

      if (broadcastId != null) {
        discoveredScale = ggDeviceService.deviceCache.value[broadcastId] as? Device
        // Initialize scale mode preferences based on connected scale
        discoveredScale?.let { scale ->
          setModePreference(scale)
        }
      }
      when (initialStep) {
        BtWifiSetupStep.USER_LIMIT_REACHED -> {
          if (userList != null) {
            handleIntent(BtWifiScaleSetupIntent.SetUserList(userList))
          }
        }

        else -> null
      }
      handleIntent(SetCurrentStep(initialStep))
    }
  }

  /**
   * Initializes the username form with the active account name.
   * This ensures we have a valid username even in the connect popup flow.
   * Trims the name to 20 characters to prevent duplicate user errors.
   */
  private suspend fun initializeUsernameForm() {
    try {
      val activeAccount = accountService.activeAccountFlow.first()
      val username = NameUtils.trimNameForSDK(activeAccount?.firstName)
      _state.value.usernameForm.username.onValueChange(username)
    } catch (e: Exception) {
      _state.value.usernameForm.username.onValueChange("Default")
    }
  }

  /**
   * Sets the mode preferences based on the connected scale's current preferences.
   * Similar to the Angular component's setModePreference method.
   *
   * @param scale The connected scale device
   */
  private fun setModePreference(scale: Device) {
    try {
      // Default values - similar to Angular component initialization
      var heartRateEnabled = false
      var allBodyMetricsMode = true // Default to metrics mode (ScaleModeEnum.metrics)

      // Check if scale has preferences (similar to !!scale?.preference check in Angular)
      scale.preferences?.let { preferences ->
        // Set heart rate based on shouldMeasurePulse preference
        heartRateEnabled = preferences.shouldMeasurePulse ?: false
        allBodyMetricsMode = preferences.shouldMeasureImpedance ?: true
      }
      handleIntent(BtWifiScaleSetupIntent.SetScaleModePreference(allBodyMetricsMode, heartRateEnabled))
    } catch (e: Exception) {
      handleIntent(BtWifiScaleSetupIntent.SetScaleModePreference(true, false))
    }
  }

  private fun replaceAccount(userName: String? = null) {
    try {
      viewModelScope.launch {
        if (userName == null) {
          ggDeviceService.deleteAccount(discoveredScale!!.toGGBTDevice()) {
            // After deleting account, refresh the user list
            refreshUserListAfterAccountChange()
          }
          handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
          handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
        } else {
          discoveredScale =
            discoveredScale!!.copy(
              preferences = discoveredScale!!.preferences?.copy(displayName = userName),
            )
          // After updating account, refresh the user list
          refreshUserListAfterAccountChange()
          handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
          handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
        }
      }
    } catch (e: Exception) {
      AppLog.d(TAG, "Error replacing account ")
    }
  }

  /**
   * Deletes users from the scale using broadcastId and token, similar to Angular's deleteUsers method.
   * This method deletes either a specific user or all duplicate users.
   */
  private fun deleteUsers(userDetails: GGBTUser? = null) {
    viewModelScope.launch {
      try {
        val broadcastId = discoveredScale?.device?.broadcastId
        if (broadcastId == null) {
          AppLog.e(TAG, "Cannot delete users: broadcastId is null")
          return@launch
        }

        if (userDetails != null) {
          // Delete specific user
          deleteUserByBroadcastIdAndToken(broadcastId, userDetails.token)
        } else {
          // Delete all duplicate users
          val duplicateList = state.value.duplicateUserList
          for (user in duplicateList) {
            deleteUserByBroadcastIdAndToken(broadcastId, user.token)
          }
        }

        // After deletion, restart connection
        restartConnection()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error deleting users", e)
      }
    }
  }

  /**
   * Deletes a user using broadcastId and token, similar to Angular's deleteScaleByBroadcastId.
   * We need to create a minimal GGBTDevice with just the required fields for deletion.
   */
  private fun deleteUserByBroadcastIdAndToken(broadcastId: String, token: String) {
    try {
      // Create a minimal GGBTDevice with just the required fields for deletion
      val minimalDevice = GGBTDevice(
        name = "", // Not needed for deletion
        broadcastId = broadcastId,
        token = token,
      )
      ggDeviceService.deleteAccount(minimalDevice) {
        refreshUserListAfterAccountChange()
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error deleting user with token: $token", e)
    }
  }

  /**
   * Restarts the connection after user deletion, similar to Angular's restartConnection method.
   */
  private fun restartConnection() {
    viewModelScope.launch {
      delay(1000)
      // Clear duplicate user state
      handleIntent(BtWifiScaleSetupIntent.SetDuplicateUser(null))
      handleIntent(BtWifiScaleSetupIntent.SetDuplicateUserList(emptyList()))
      // Reset button text to Next before changing step
      handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
      // Restart from Bluetooth connection step
      handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
    }
  }

  /**
   * Shows restore account confirmation dialog similar to Angular implementation.
   * Based on the restore() method from smart-wifi-setup.page.ts
   */
  private fun showRestoreAccountAlert() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Title,
        message = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Message,
        confirmText = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Restore,
        cancelText = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.GoBack,
        onConfirm = {
          // Delete duplicate users and restore account
          viewModelScope.launch {
            accountService.activeAccountFlow.first()?.firstName?.take(20)
            // First check for duplicate users
            checkDuplicateUserList()
            // Then delete them
            deleteUsers()
          }
        },
      ),
    )
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
      // Use the same logic as ScaleSetupViewmodel.subscribePermissions to handle WIFI_SWITCH properly
      combine(
        permissionService.permissionCallBackFlow,
        connectivityObserver.observe(),
      ) { permissions, networkState ->
        val networkStatus = if (networkState.available) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED

        AppLog.d(TAG, "Network status: $networkStatus, WiFi switch status: $wifiSwitchStatus")

        // WiFi switch is enabled if either network is available OR WiFi switch is enabled
        val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
          wifiSwitchStatus == GGPermissionState.ENABLED
        ) {
          GGPermissionState.ENABLED
        } else {
          GGPermissionState.DISABLED
        }

        AppLog.d(TAG, "Updated WiFi switch status: $updatedWifiSwitchStatus")
        val updatedPermissions = permissions.toMutableMap().apply {
          put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
        }

        handleIntent(BtWifiScaleSetupIntent.SetPermissions(updatedPermissions))
        val areRequiredPermissionsEnabled =
          AppPermissionsHelper.areRequiredPermissionsEnabled(updatedPermissions, setupType = ScaleSetupType.BtWifiR4)
        if (!areRequiredPermissionsEnabled) {
          // Use comprehensive permission-based error handling
          handlePermissionBasedErrors()
        }
      }.collect { }
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
          when (currentStep) {
            BtWifiSetupStep.WAKEUP -> {
              wakeUpScale()
            }

            BtWifiSetupStep.PERMISSIONS -> {
            }

            BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
              connectToBluetooth()
            }

            BtWifiSetupStep.DUPLICATES_FOUND -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Save))
              checkDuplicateUserList()
            }

            BtWifiSetupStep.GATHERING_NETWORK -> {
              if (!AppPermissionsHelper.areRequiredPermissionsEnabled(
                  state.value.permissions,
                  setupType = ScaleSetupType.BtWifiR4,
                )
              ) {
                setGatheringNetworkFailed()
              } else {
                gatherNetworks()
              }
            }

            BtWifiSetupStep.WIFI_PASSWORD -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Connect))
            }

            BtWifiSetupStep.CONNECTING_WIFI -> {
              connectToWifi()
            }

            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
              loadDashboardKeys()
              loadGoalProgress()
            }

            BtWifiSetupStep.STEP_ON -> {
              if (!AppPermissionsHelper.areRequiredPermissionsEnabled(
                  state.value.permissions,
                  setupType = ScaleSetupType.BtWifiR4,
                )
              ) {
                setMeasurementFailed()
              } else {
                stepOn()
              }
            }

            BtWifiSetupStep.MEASUREMENT -> {
              collectMeasurement()
            }

            else -> {
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
    if (currentState.isLastStep) {
      handleIntent(BtWifiScaleSetupIntent.ExitSetup(true))
    }
    when (currentState.currentStep) {
      BtWifiSetupStep.SCALE_INFO -> {
        viewModelScope.launch {
          permissionAccess()
        }
        handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        return
      }

      BtWifiSetupStep.PERMISSIONS -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.WAKEUP))
        return
      }

      BtWifiSetupStep.WAKEUP -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
        return
      }

      BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
        return
      }

      BtWifiSetupStep.GATHERING_NETWORK -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
        return
      }

      BtWifiSetupStep.DUPLICATES_FOUND -> {
        val newUserName = _state.value.usernameForm.username.value
        if (newUserName != _state.value.duplicateUser?.name) {
          handleIntent(BtWifiScaleSetupIntent.ReplaceAccount(newUserName))
        }
      }

      BtWifiSetupStep.WIFI_PASSWORD -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_WIFI))
        return
      }

      BtWifiSetupStep.CONNECTING_WIFI -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
        return
      }

      BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
        if (!currentState.connectedSSID.isNullOrEmpty()) {
          ggDeviceService.cancelWifi(discoveredScale?.toGGBTDevice()!!) {}
          handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
          return
        } else {
          return
        }
      }

      BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
        if (hasCustomizationChanges()) {
          handleIntent(SetCurrentStep(BtWifiSetupStep.UPDATE_SETTINGS))
          return
        } else {
          handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
          return
        }
      }

      BtWifiSetupStep.UPDATE_SETTINGS -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
        return
      }

      BtWifiSetupStep.STEP_ON -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
        return
      }

      BtWifiSetupStep.MEASUREMENT -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.SETUP_FINISHED))
        return
      }

      else -> {
      }
    }
  }

  /**
   * Handles moving to the previous step in the setup process.
   */
  private fun onBack() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to previous step from: ${currentState.currentStep}")

    when (currentState.currentStep) {
      BtWifiSetupStep.WAKEUP -> {
        ggDeviceService.resumeScan(true)
      }

      BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
      }

      BtWifiSetupStep.WIFI_PASSWORD -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
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

  /**
   * Handles skipping the current step.
   */
  private fun onSkip() {
    val currentState = state.value
    AppLog.d(TAG, "Skipping current step: ${currentState.currentStep}")

    when (currentState.currentStep) {
      BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
        // Show confirmation dialog for WiFi skip
        showWifiSkipConfirmation()
      }

      BtWifiSetupStep.GATHERING_NETWORK -> {
        handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
      }

      else -> {
        // For other steps, treat skip as next
        onNext()
      }
    }
  }

  /**
   * Shows WiFi skip confirmation dialog.
   */
  private fun showWifiSkipConfirmation() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleSetupStrings.SkipBtWifiPermissions.Title,
        message = ScaleSetupStrings.SkipBtWifiPermissions.Message,
        confirmText = ScaleSetupStrings.SkipBtWifiPermissions.Skip,
        cancelText = ScaleSetupStrings.SkipBtWifiPermissions.Goback,
        onConfirm = {
          // User confirmed skip - proceed to customization
          AppLog.d(TAG, "User confirmed WiFi skip, proceeding to customization")
          ggDeviceService.cancelWifi(discoveredScale?.toGGBTDevice()!!) {}
          handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
        },
        onCancel = {
          // User chose to go back - stay on WiFi list
          AppLog.d(TAG, "User chose to go back from WiFi skip confirmation")
        },
      ),
    )
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    // Clear setup in progress state when exiting
    deviceService.setSetupInProgress(false)
    if (isSetupFinished) {
      onExit()
    } else {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(discoveredScale?.connectionStatus == BLEStatus.CONNECTED),
          confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
          cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
          onConfirm = {
            onExit()
          },
        ),
      )
    }
  }

  private fun onExit() {
    // Clear all timeouts before exiting
    clearAllTimeouts()

    // Only navigation remains in viewModelScope
    viewModelScope.launch {
      // Ensure WiFi configuration is up to date before final save
      try {
        updateWifiDetails()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during scale cleanup operations", e)
      }
      try {
        if (discoveredScale != null) {
          ggDeviceService.cancelWifi(discoveredScale!!.toGGBTDevice()) {}
          if (!isScaleConnected && initialStep != BtWifiSetupStep.GATHERING_NETWORK) {
            ggDeviceService.disconnectDevice(discoveredScale!!.toGGBTDevice())
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during Bluetooth cleanup", e)
      }
      try {
        ggDeviceService.resumeScan(false)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error resuming scan", e)
      }
      try {
        navigateBack()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back from scale setup", e)
      }
    }
  }

  /**
   * Handles refreshing networks - goes back to GATHERING_NETWORK step.
   */
  private fun onRefreshNetworks() {
    AppLog.d(TAG, "Refreshing networks, going back to GATHERING_NETWORK")
    handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
  }

  /**
   * Handles password network status toggle by dynamically adding/removing validation.
   * Reads the current status from the wifiPasswordForm.noPasswordNetwork control.
   * Properly clears form state to avoid validation errors.
   */
  private fun handlePasswordNetworkStatus() {
    val currentState = state.value
    val isNoPasswordNetwork = currentState.wifiPasswordForm.noPasswordNetwork.value
    AppLog.d(TAG, "Handling password network status, isNoPasswordNetwork: $isNoPasswordNetwork")
    if (isNoPasswordNetwork) {
      // No password network - remove required validation and reset field completely
      currentState.wifiPasswordForm.password.removeValidator("required")
      // Reset clears value, error, touched, dirty, and pending states
      currentState.wifiPasswordForm.password.reset("")
    } else {
      // Password network - add required validation
      currentState.wifiPasswordForm.password.addValidator(
        com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.required(),
      )
      // Reset to clear any stale errors, then revalidate if there's a value
      currentState.wifiPasswordForm.password.reset("")
    }

    // Update canProceedToNext based on current form state
    updateWifiPasswordFormValidation()
  }

  /**
   * Updates the canProceedToNext state based on WiFi password form validation.
   * This function should be called whenever the form state changes.
   */
  private fun updateWifiPasswordFormValidation() {
    val currentState = state.value
    if (currentState.currentStep == BtWifiSetupStep.WIFI_PASSWORD) {
      if (currentState.wifiPasswordForm.noPasswordNetwork.value) {
        currentState.wifiPasswordForm.ssid.isValueValid()
      } else {
        currentState.wifiPasswordForm.ssid.isValueValid() && currentState.wifiPasswordForm.password.isValueValid()
      }
    }
  }

  /**
   * Clears the WiFi password form after successful WiFi connection.
   * Resets both SSID and password fields to empty values.
   */
  private fun clearWifiPasswordForm() {
    AppLog.d(TAG, "Clearing WiFi password form after successful connection")
    val currentState = state.value
    // currentState.wifiPasswordForm.ssid.reset()
    currentState.wifiPasswordForm.password.reset()
    currentState.wifiPasswordForm.noPasswordNetwork.onValueChange(false)
  }

  /**
   * Refreshes the user list after account changes (delete/update) to keep it in sync.
   * This ensures that the user list is always current for duplicate validation.
   */
  private fun refreshUserListAfterAccountChange() {
    viewModelScope.launch {
      try {
        if (discoveredScale != null) {
          val userList = suspendCoroutine { continuation ->
            ggDeviceService.getUsers(discoveredScale!!.toGGBTDevice()) { response ->
              continuation.resume(response.user)
            }
          }
          // Filter out the current user to prevent duplicate validation errors when restoring the same name
          val filteredUserList = userList.filter { user -> user.token != discoveredScale?.token }
          handleIntent(BtWifiScaleSetupIntent.SetUserList(filteredUserList))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating user list after account change", e)
      }
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

  private fun setBluetoothFailedStatus() {
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        ConnectionState.Failed.Error,
      ),
    )
  }

  private fun setGatheringNetworkFailed() {
    if (isScaleConnected) {
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.GATHERING_NETWORK,
          ConnectionState.Failed.Error,
        ),
      )
    }
  }

  /**
   * Sets measurement collection failed error state
   */
  private fun setMeasurementFailed() {
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.MEASUREMENT,
        ConnectionState.Failed.Error,
      ),
    )
  }

  /**
   * Handles try again action based on error type, similar to Angular's retryClicked() method.
   * Clears error and navigates to the appropriate step for retry.
   */
  private fun onTryAgain() {
    val currentState = state.value
    val errorCode = currentState.errorCode
    AppLog.d(TAG, "Try again for error: $errorCode, step: ${currentState.currentStep}")
    when (currentState.currentStep) {
      BtWifiSetupStep.WAKEUP -> {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        ggDeviceService.resumeScan(true)
        wakeUpScale()
      }

      BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
        bluetoothConnectionTimeoutJob?.cancel()
        bluetoothConnectionTimeoutJob = null
        if (discoveredScale != null) {
          val broadcastId = discoveredScale?.device?.broadcastId
          if (broadcastId != null) {
            ggDeviceService.disconnectDevice(discoveredScale!!.toGGBTDevice())
          }
          connectToBluetooth()
        } else {
          ggDeviceService.resumeScan(true)
          wakeUpScale()
        }
      }

      BtWifiSetupStep.GATHERING_NETWORK -> {
        gatherNetworks()
      }

      BtWifiSetupStep.CONNECTING_WIFI -> {
        wifiConnectionTimeoutJob?.cancel()
        wifiConnectionTimeoutJob = null
        handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
        gatherNetworks()
      }

      BtWifiSetupStep.STEP_ON -> {
        stepOn()
      }

      BtWifiSetupStep.UPDATE_SETTINGS -> {
        updateSettingsTimeoutJob?.cancel()
        updateSettingsTimeoutJob = null
      }

      BtWifiSetupStep.MEASUREMENT -> {
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null
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
        AppLog.e(TAG, "Failed to navigate back from scale setup", e)
      }
    }
  }

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {
    // Call the pair() method which handles the scanning with timeout
    pair()
  }

  /**
   * Handles pairing process with timeout mechanism similar to Angular implementation.
   * This method scans for devices and handles the pairing flow with a 5-minute timeout.
   */
  private fun pair() {
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.WAKEUP,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Add a small delay to ensure Bluetooth service is properly initialized
        ggDeviceService.scanForPairing()
        startObservingDevices()
        // Set up 5-minute timeout
        val timeoutJob = viewModelScope.launch {
          delay(operationTimeout) // 5 minutes timeout
          if (state.value.currentStep == BtWifiSetupStep.WAKEUP) {
            AppLog.w(TAG, "Pairing timeout reached after 5 minutes")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.WAKEUP,
                ConnectionState.Failed.Error,
              ),
            )
            stopObservingDevices()
          }
        }

        // Store timeout job reference for potential cancellation
        pairingTimeoutJob = timeoutJob
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during pairing process", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.WAKEUP,
            ConnectionState.Failed.Error,
          ),
        )
      }
    }
  }

  /**
   * Handles bluetooth connection process with timeout mechanism.
   */
  private fun connectToBluetooth() {
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_BLUETOOTH,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Set up timeout for bluetooth connection
        val timeoutJob = viewModelScope.launch {
          delay(operationTimeout) // 5 minutes timeout
          if (state.value.currentStep == BtWifiSetupStep.CONNECTING_BLUETOOTH) {
            AppLog.w(TAG, "Bluetooth connection timeout reached")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                ConnectionState.Failed.Error,
              ),
            )
            stopObservingDevices()
          }
        }

        // Store timeout job reference for potential cancellation
        bluetoothConnectionTimeoutJob = timeoutJob

        val ggBtDevice = discoveredScale!!.toGGBTDevice()
        ggDeviceService.pairDevice(
          device = ggBtDevice,
        ) { it ->
          // Cancel timeout since we got a response
          bluetoothConnectionTimeoutJob?.cancel()
          bluetoothConnectionTimeoutJob = null

          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED -> {
              viewModelScope.launch {
                fetchUserList()
                isScaleConnected = true
                handleIntent(
                  BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.CONNECTING_BLUETOOTH,
                    ConnectionState.Success,
                  ),
                )
                discoveredScale =
                  deviceService.saveScale(discoveredScale!!.copy(connectionStatus = BLEStatus.CONNECTED))
                if (accountService.activeAccountFlow.first()?.dashboardType == DashboardType.DASHBOARD_4_METRICS.value) {
                  accountService.updateDashboardType(DashboardType.DASHBOARD_12_METRICS)
                  val dashboardMetrics = accountService.activeAccountFlow.first()!!.dashboardMetrics
                  val additionalMetrics = StatHelper.getAdditionalMetrics()
                  val updatedMetrics = (dashboardMetrics ?: emptyList()).toMutableList().apply {
                    additionalMetrics.forEach { metric ->
                      if (!contains(metric)) {
                        add(metric)
                      }
                    }
                  }
                  // Convert camelCase strings to MetricKey enums and update via dashboard service
                  val metricKeys = updatedMetrics.mapNotNull { MetricKeyConstants.CAMEL_CASE_TO_ENUM[it] }
                  dashboardService.updateVisibleMetricKeys(accountId, metricKeys, DashboardType.DASHBOARD_12_METRICS)
                }
                handleIntent(BtWifiScaleSetupIntent.SetScaleId(discoveredScale?.id ?: ""))
                onNext()
              }
            }

            GGUserActionResponseType.DUPLICATE_USER_ERROR -> {
              Log.d("discoveredscale", "$discoveredScale")
              viewModelScope.launch {
                // Get duplicate username from either scale preferences or active account
                val duplicateUserName = discoveredScale?.preferences?.displayName
                  ?: _state.value.usernameForm.username.value.takeIf { it.isNotEmpty() }
                  ?: accountService.activeAccountFlow.first()?.firstName?.take(20)
                Log.d("duplicateusername", "$discoveredScale")
                if (duplicateUserName != null) {
                  AppLog.d(TAG, "Found duplicate user: $duplicateUserName")
                  fetchUserList(duplicateUserName = duplicateUserName)
                  handleIntent(SetCurrentStep(BtWifiSetupStep.DUPLICATES_FOUND))
                } else {
                  // If we still can't get a username, log error and show generic error
                  AppLog.e(TAG, "Could not determine duplicate username")
                  setBluetoothFailedStatus()
                }
              }
            }

            GGUserActionResponseType.MEMORY_FULL -> {
              viewModelScope.launch {
                fetchUserList(
                  onSuccess = {
                    handleIntent(
                      SetCurrentStep(BtWifiSetupStep.USER_LIMIT_REACHED),
                    )
                  },
                )
              }
            }

            else -> setBluetoothFailedStatus()
          }
        }
      } catch (e: Exception) {
        // Cancel timeout on exception
        bluetoothConnectionTimeoutJob?.cancel()
        bluetoothConnectionTimeoutJob = null

        AppLog.e(TAG, "Error during bluetooth connection", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.CONNECTING_BLUETOOTH,
            ConnectionState.Failed.Error,
          ),
        )
      }
    }
  }

  /**
   * Checks for duplicate users and creates a list of users to be deleted.
   * Similar to Angular's checkDuplicateUserList() method.
   */
  private fun checkDuplicateUserList() {
    try {
      val currentUserName = _state.value.usernameForm.username.value
      val currentUser = state.value.userList.find { user ->
        user.name.equals(currentUserName, ignoreCase = true)
      }

      if (currentUser != null) {
        // Create a list of duplicate users (same name)
        val duplicateList = state.value.userList.filter { user ->
          user.name.equals(currentUser.name, ignoreCase = true)
        }
        // Store duplicate list for deletion
        handleIntent(BtWifiScaleSetupIntent.SetDuplicateUserList(duplicateList))
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking duplicate user list", e)
    }
  }

  private fun fetchUserList(duplicateUserName: String? = null, onSuccess: (() -> Unit)? = null) {
    try {
      viewModelScope.launch {
        val userList = suspendCoroutine { continuation ->
          ggDeviceService.getUsers(discoveredScale!!.toGGBTDevice()) { response ->

            Log.d("userslist", "$response")
            if (duplicateUserName != null) {
              val user = response.user.first { it.name == duplicateUserName }
              // Don't update discoveredScale token here - keep the original token for proper filtering
              handleIntent(BtWifiScaleSetupIntent.SetDuplicateUser(user))
            }
            continuation.resume(response.user)
            onSuccess?.invoke()
          }
        }
        val filteredUserList = userList.filter { user -> user.token != discoveredScale?.token }
        handleIntent(BtWifiScaleSetupIntent.SetUserList(filteredUserList))
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during fetching user list", e)
      // Show error state to user
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.CONNECTING_BLUETOOTH,
          ConnectionState.Failed.Error,
        ),
      )
      // Use BT_002 for general Bluetooth errors (consistent with existing error code)
    }
  }

  /**
   * Handles network gathering process.
   */
  private fun gatherNetworks() {
    AppLog.d(TAG, "Starting network gathering process")
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.GATHERING_NETWORK,
        ConnectionState.Loading,
      ),
    )
    try {
      ggDeviceService.getWifiList(discoveredScale!!.toGGBTDevice()) {
        viewModelScope.launch {

          AppLog.d(TAG, "Network gathering successful")
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.GATHERING_NETWORK,
              ConnectionState.Success,
            ),
          )
          val canRequestNotifPermission =
            AppPermissionsHelper.canRequestNotificationPermission(state.value.permissions)
          if (canRequestNotifPermission) {
            requestPermission(GGPermissionType.NOTIFICATION)
          }

          val connectedSSID = suspendCancellableCoroutine<String> { cont ->
            ggDeviceService.getConnectedWifiSSID(discoveredScale!!.toGGBTDevice()) { wifiMac ->
              val ssid = wifiMac.cleanCorruptedChars()
              this@BtWifiScaleSetupViewModel.wifiSsid = ssid
              cont.resume(ssid)
            }
          }
          suspendCancellableCoroutine { cont ->
            ggDeviceService.getConnectedWifiMacAddress(discoveredScale!!.toGGBTDevice()) { mac ->
              this@BtWifiScaleSetupViewModel.wifiMac = mac
              cont.resume(mac)
            }
          }
          handleIntent(BtWifiScaleSetupIntent.SetWifiList(it.wifi))
          handleIntent(BtWifiScaleSetupIntent.SetConnectedSSID(connectedSSID))
          onNext()

        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during network gathering", e)
      setGatheringNetworkFailed()
    }
  }

  /**
   * Handles permission-based errors for different steps, similar to Angular's handlePermissionChange()
   */
  private fun handlePermissionBasedErrors() {
    val areRequiredPermissionsEnabled =
      AppPermissionsHelper.areRequiredPermissionsEnabled(state.value.permissions, setupType = ScaleSetupType.BtWifiR4)
    if (!areRequiredPermissionsEnabled) {
      val currentStep = state.value.currentStep
      when (currentStep) {
        BtWifiSetupStep.WAKEUP -> {
          handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        }

        BtWifiSetupStep.GATHERING_NETWORK -> {
          setGatheringNetworkFailed()
        }

        BtWifiSetupStep.STEP_ON -> {
          setMeasurementFailed()
        }

        else -> {}
      }
    }
  }

  /**
   * Handles wifi connection process with timeout mechanism.
   */
  private fun connectToWifi() {
    AppLog.d(TAG, "Starting wifi connection process with timeout")
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_WIFI,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Set up timeout for WiFi connection
        val timeoutJob = viewModelScope.launch {
          delay(operationTimeout) // 5 minutes timeout
          if (state.value.currentStep == BtWifiSetupStep.CONNECTING_WIFI) {
            AppLog.w(TAG, "WiFi connection timeout reached")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Failed.Error,
              ),
            )
          }
        }

        // Store timeout job reference for potential cancellation
        wifiConnectionTimeoutJob = timeoutJob

        // Call the actual WiFi setup method
        setupWifi()
      } catch (e: Exception) {
        // Cancel timeout on exception
        wifiConnectionTimeoutJob?.cancel()
        wifiConnectionTimeoutJob = null

        AppLog.e(TAG, "Error during wifi connection", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.CONNECTING_WIFI,
            ConnectionState.Failed.Error,
          ),
        )
      }
    }
  }

  /**
   * Performs the actual WiFi setup operation.
   */
  private fun setupWifi() {
    try {
      val ssid = _state.value.wifiPasswordForm.ssid.value
      val password = _state.value.wifiPasswordForm.password.value
      this.wifiSsid = null
      this.wifiMac = null
      ggDeviceService.setupWifi(
        discoveredScale!!.toGGBTDevice(),
        GGBTWifiConfig(ssid, password),
      ) {
        // Cancel timeout since we got a response
        wifiConnectionTimeoutJob?.cancel()
        wifiConnectionTimeoutJob = null

        viewModelScope.launch {
          if (it.wifiState == GGWifiState.GG_WIFI_STATE_CONNECTED.name) {
            AppLog.d(TAG, "Wifi connection successful")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Success,
              ),
            )
            clearWifiPasswordForm()
            this@BtWifiScaleSetupViewModel.wifiSsid = ssid
            this@BtWifiScaleSetupViewModel.wifiMac = suspendCancellableCoroutine { cont ->
              ggDeviceService.getConnectedWifiMacAddress(discoveredScale!!.toGGBTDevice()) { mac ->
                cont.resume(mac)
              }
            }
            if (initialStep == BtWifiSetupStep.GATHERING_NETWORK) {
              onExitSetup(true)
              return@launch
            }
            onNext()
          } else {
            AppLog.w(TAG, "Wifi connection failed")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Failed.Error,
              ),
            )
            handleIntent(BtWifiScaleSetupIntent.SetErrorCode(it.errorCode))
          }
        }
      }
    } catch (e: Exception) {
      // Cancel timeout on exception
      wifiConnectionTimeoutJob?.cancel()
      wifiConnectionTimeoutJob = null
      this.wifiSsid = null
      this.wifiMac = null
      AppLog.e(TAG, "Error during wifi setup", e)
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.CONNECTING_WIFI,
          ConnectionState.Failed.Error,
        ),
      )
    }
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    if (permissionType == GGPermissionType.WIFI_SWITCH) {
      permissionService.requestPermission(permissionType)
      return
    }
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

  fun permissionAccess() {
    val currentPermissions = state.value.permissions

    // Check Bluetooth Switch permission
    if (currentPermissions[GGPermissionType.BLUETOOTH_SWITCH] != GGPermissionState.ENABLED) {
      AppLog.d(TAG, "Requesting Bluetooth Switch permission")
      handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.BLUETOOTH_SWITCH))
    }

    // For Android API 31+ (Android 12+), check NEARBY_DEVICE permission
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      if (currentPermissions[GGPermissionType.NEARBY_DEVICE] != GGPermissionState.ENABLED) {
        AppLog.d(TAG, "Requesting Nearby Device permission")
        handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.NEARBY_DEVICE))
        return
      }
    } else {
      // For older Android versions, check Location permissions
      if (currentPermissions[GGPermissionType.LOCATION_SWITCH] != GGPermissionState.ENABLED) {
        AppLog.d(TAG, "Requesting Location Switch permission")
        handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION_SWITCH))
      }

      if (currentPermissions[GGPermissionType.LOCATION] != GGPermissionState.ENABLED) {
        AppLog.d(TAG, "Requesting Location permission")
        handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION))
      }
    }

    AppLog.d(TAG, "All required permissions are enabled")
  }

  private suspend fun updateWifiDetails() {
    val supervisorJob = SupervisorJob()
    val supervisorScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    discoveredScale = discoveredScale?.copy(
      device = discoveredScale?.device?.copy(
        isWifiConfigured = wifiSsid != null,
        wifiMacAddress = wifiMac,
      ),
    )

    // Save the scale with updated WiFi configuration to ensure UI updates properly
    discoveredScale?.let { scale ->
      AppLog.d(TAG, "Saving scale with updated WiFi configuration: isWifiConfigured=${scale.device?.isWifiConfigured}")
      supervisorScope.launch {
        deviceService.saveScale(scale)
      }
      val deviceDetail = scale.device
      if (deviceDetail != null) {
        AppLog.d(
          TAG,
          "Triggering onDeviceUpdate for device ${deviceDetail.macAddress} with WiFi configured: ${deviceDetail.isWifiConfigured}",
        )
        deviceService.onDeviceUpdate(deviceDetail, scale.connectionStatus)
        AppLog.d(TAG, "Triggered onDeviceUpdate for WiFi configuration change")
      }
    }
  }

  private fun stepOn() {
    AppLog.d(TAG, "Starting step on process")
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.STEP_ON,
        ConnectionState.Loading,
      ),
    )
    try {
      subscribeToLiveData()
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during step on", e)
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.STEP_ON,
          ConnectionState.Failed.Error,
        ),
      )
    }
  }

  private fun collectMeasurement() {
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.MEASUREMENT,
        ConnectionState.Loading,
      ),
    )

    viewModelScope.launch {
      try {
        // Set up timeout for measurement collection
        val timeoutJob = viewModelScope.launch {
          delay(operationTimeout) // 5 minutes timeout
          if (state.value.currentStep == BtWifiSetupStep.MEASUREMENT) {
            AppLog.w(TAG, "Measurement collection timeout reached")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.MEASUREMENT,
                ConnectionState.Failed.Error,
              ),
            )
          }
        }

        // Store timeout job reference for potential cancellation
        measurementTimeoutJob = timeoutJob

        // Start measurement collection process
        startObservingEntries()
      } catch (e: Exception) {
        // Cancel timeout on exception
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null

        AppLog.e(TAG, "Error during measurement collection", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.MEASUREMENT,
            ConnectionState.Failed.Error,
          ),
        )
      }
    }
  }

  /**
   * Checks if any customization settings have changed from their original values.
   * This determines whether we need to go through the UPDATE_SETTINGS step or can skip directly to STEP_ON.
   */
  private fun hasCustomizationChanges(): Boolean {
    val hasChanges = state.value.hasSavedSettings
    return hasChanges
  }

  private fun updateDevicePreferences(dashboardKeys: List<DashboardKey>? = null, preferences: Preferences? = null) {
    viewModelScope.launch {
      try {
        onNext()
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.UPDATE_SETTINGS,
            ConnectionState.Loading,
          ),
        )
        val timeoutJob = viewModelScope.launch {
          delay(2 * 60 * 1000) // 5 minutes timeout
          if (state.value.currentStep == BtWifiSetupStep.UPDATE_SETTINGS) {
            AppLog.w(TAG, "Update settings timeout reached")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.UPDATE_SETTINGS,
                ConnectionState.Failed.Error,
              ),
            )
          }
        }
        if (dashboardKeys != null) {
          dashboardService.updateVisibleKeys(
            accountId = accountId,
            keys = dashboardKeys,
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
          )
        }
        if (preferences != null) {
          val newName = _state.value.usernameForm.username.value
          val updatedDevice =
            discoveredScale!!.copy(preferences = preferences.copy(displayName = newName.ifEmpty { preferences.displayName }))
          discoveredScale = updatedDevice
          ggDeviceService.updateAccount(
            updatedDevice.toGGBTDevice(),
          ) {
            when (it) {
              GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
                viewModelScope.launch {
                  timeoutJob.cancel()
                  deviceService.updateScalePreferencesByMac(
                    discoveredScale?.device?.macAddress ?: "",
                    discoveredScale?.preferences!!.toR4ScalePreferenceApiModel(),
                  )
                  handleIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                      BtWifiSetupStep.UPDATE_SETTINGS,
                      ConnectionState.Success,
                    ),
                  )
                  onNext()
                }
              }

              else -> {
                viewModelScope.launch {
                  timeoutJob.cancel()
                  handleIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                      BtWifiSetupStep.UPDATE_SETTINGS,
                      ConnectionState.Failed.Error,
                    ),
                  )
                }
              }
            }
          }
          if (!state.value.hasSavedSettings) {
            deviceService.updateScalePreferences(discoveredScale!!.id, preferences.toR4ScalePreferenceApiModel())
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during settings update", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.UPDATE_SETTINGS,
            ConnectionState.Failed.Error,
          ),
        )
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
      entryService.progress.collect {
        handleIntent(BtWifiScaleSetupIntent.SetGoalProgress(it))
      }
    }
  }

  private fun loadGoalProgress() {
    viewModelScope.launch {
      entryService.progress.collect {
        handleIntent(BtWifiScaleSetupIntent.SetGoalProgress(it))
      }
    }
  }

  /**
   * Callback when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
    val ggDeviceDetail = response.data
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (ggDeviceDetail.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value) {
          viewModelScope.launch {

            if (deviceService.pairedScales.first().any { it.device?.macAddress == ggDeviceDetail.macAddress }) {
              // Cancel timeout since we found a known scale
              pairingTimeoutJob?.cancel()
              pairingTimeoutJob = null

              dialogQueueService.showDialog(
                DialogModel.Alert(
                  title = "Known Scale Discovered",
                  message = "Weight Gurus sees a scale that is already set up. If you are trying to set up a second scale, make sure only one is turned on at a time.",
                  onDismiss = {
                    onExitSetup(true)
                    dialogQueueService.dismissCurrent()
                  },
                ),
              )
            } else {
              // Cancel timeout since device was found
              pairingTimeoutJob?.cancel()
              pairingTimeoutJob = null
              val deviceSku = ggDeviceDetail.getSKU()
              val shouldShow = if (deviceSku == "0412") {
                val isAllow = bluetoothPreferencesService.shouldShowDevice(ggDeviceDetail.macAddress)
                isAllow
              } else {
                true // Don't filter non-0412 scales
              }
              if (!shouldShow) {
                return@launch
              }
              stopObservingDevices()
              customizeDevice(ggDeviceDetail)
              AppLog.d(TAG, "Wake up successful, proceeding to next step")
              handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                  BtWifiSetupStep.WAKEUP,
                  ConnectionState.Success,
                ),
              )
              onNext()
            }
          }
        }
      }

      else -> null
    }
  }

  override fun onEntryResponse(response: GGScanResponse.Entry) {
    when (response.type) {
      GGScanResponseType.SINGLE_ENTRY -> {
        // Cancel measurement timeout since we received a measurement
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null

        viewModelScope.launch {
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.MEASUREMENT,
              ConnectionState.Success,
            ),
          )
          onNext()
        }
      }

      else -> null
    }
  }

  private suspend fun customizeDevice(ggDeviceDetail: GGDeviceDetail) {
    val username =
      discoveredScale?.preferences?.displayName ?: accountService.activeAccountFlow.first()?.firstName?.take(20)
      ?: "Default"
    _state.value.usernameForm.username.onValueChange(username)
    val token = deviceService.getScaleToken()
    val device = Device(
      device = ggDeviceDetail,
      token = token,
      nickname = "Accucheck Verve Smart Scale",
    )
    discoveredScale = device.copy(
      deviceType = ScaleSetupType.BtWifiR4.value,
      sku = sku,
      preferences = ScaleMetricsHelper.getDefaultPreference(username, device.id),
    )

    // Set mode preferences after discovering scale
    discoveredScale?.let { scale ->
      setModePreference(scale)
    }
  }

  private fun subscribeToLiveData() {
    ggDeviceService.subscribeToLiveData(discoveredScale!!.toGGBTDevice()) {
      when (it) {
        is GGLiveDataResponse.Success -> {
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.STEP_ON,
              ConnectionState.Success,
            ),
          )
          onNext()
        }

        else -> null
      }
    }
  }

  private fun deleteUser(user: GGBTUser) {
    try {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleUsersStrings.DeleteUserAlert.Title,
          message = ScaleUsersStrings.DeleteUserAlert.Message(user.name),
          confirmText = ScaleUsersStrings.DeleteUserAlert.Delete,
          cancelText = ScaleUsersStrings.DeleteUserAlert.Back,
          onConfirm = {
            // Delete user and update the list
            viewModelScope.launch {
              val deleteDevice = discoveredScale?.copy(
                preferences = discoveredScale?.preferences?.copy(
                  displayName = user.name,
                  shouldMeasureImpedance = user.isBodyMetricsEnabled,
                ),
                token = user.token,
              )
              ggDeviceService.deleteAccount(deleteDevice!!.toGGBTDevice()) {
                // After deleting user, refresh the user list to keep it in sync
                refreshUserListAfterAccountChange()
              }
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
              handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
            }
          },
        ),
      )
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during user deletion", e)
    }
  }

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    openInAppBrowser(url)
  }

  override fun onCleared() {
    super.onCleared()
    // Clean up all timeout jobs when ViewModel is destroyed
    clearAllTimeouts()
    deviceService.setSetupInProgress(false)
    AppLog.d(TAG, "BtWifiScaleSetupViewModel cleared - all timeouts cancelled")
  }

  /**
   * Clears all active timeout jobs to prevent memory leaks and unwanted callbacks.
   * This should be called when the ViewModel is destroyed or when exiting the setup.
   */
  private fun clearAllTimeouts() {
    AppLog.d(TAG, "Clearing all timeout jobs")

    // Cancel pairing timeout
    pairingTimeoutJob?.cancel()
    pairingTimeoutJob = null

    // Cancel bluetooth connection timeout
    bluetoothConnectionTimeoutJob?.cancel()
    bluetoothConnectionTimeoutJob = null

    // Cancel WiFi connection timeout
    wifiConnectionTimeoutJob?.cancel()
    wifiConnectionTimeoutJob = null

    // Cancel update settings timeout
    updateSettingsTimeoutJob?.cancel()
    updateSettingsTimeoutJob = null

    // Cancel measurement timeout
    measurementTimeoutJob?.cancel()
    measurementTimeoutJob = null

    AppLog.d(TAG, "All timeout jobs cleared successfully")
  }
}
