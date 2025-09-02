package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
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
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
  private val dashboardService: IDashboardService,
  override val permissionService: GGPermissionService,
  override val connectivityObserver: IConnectivityObserver,
  private val dialogUtility: IDialogUtility,
  private val accountService: IAccountService
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
  private var deviceConfigured: Boolean = false

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
      else -> {}
    }
    super.handleIntent(intent)
  }

  init {
    loadScaleInfo()
    observePermissions()
    observeStepChanges()
    initializeSetup()
  }

  private fun initializeSetup() {
    viewModelScope.launch {
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
   */
  private suspend fun initializeUsernameForm() {
    try {
      val activeAccount = accountService.activeAccountFlow.first()
      val username = activeAccount?.firstName ?: "Default"
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
    viewModelScope.launch {
      if (userName == null) {
        ggDeviceService.deleteAccount(discoveredScale!!.toGGBTDevice()) {
          // After deleting account, refresh the user list
          refreshUserListAfterAccountChange()
        }
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
      } else {
        discoveredScale =
          discoveredScale!!.copy(
            preferences = discoveredScale!!.preferences?.copy(displayName = userName),
          )
        // After updating account, refresh the user list
        refreshUserListAfterAccountChange()
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
      }
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
            accountService.activeAccountFlow.first()?.firstName
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
      permissionService.permissionCallBackFlow.collect {
        handleIntent(BtWifiScaleSetupIntent.SetPermissions(it))
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(it, sku)
        if (!areRequiredPermissionsEnabled) {
          if (state.value.currentStep != BtWifiSetupStep.PERMISSIONS && state.value.currentStep != BtWifiSetupStep.SCALE_INFO) {
            handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
          }
        } else {
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
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
              wakeUpScale()
            }

            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
              loadDashboardKeys()
              // Refresh the user list to ensure it's up-to-date for duplicate validation
              AppLog.d(TAG, "Entering CUSTOMIZE_SETTINGS step, refreshing user list...")
              // Prevent automatic progression to UPDATE_SETTINGS
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
            }

            BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
              connectToBluetooth()
            }

            BtWifiSetupStep.DUPLICATES_FOUND -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Save))
              // Check for duplicate users when entering this step
              checkDuplicateUserList()
            }

            BtWifiSetupStep.GATHERING_NETWORK -> {
              gatherNetworks()
            }

            BtWifiSetupStep.WIFI_PASSWORD -> {
              handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Connect))
              // Reset canProceedToNext to false initially, it will be enabled when form validation passes
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
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
    } else if (currentState.currentStep == BtWifiSetupStep.DUPLICATES_FOUND) {
      val newUserName = _state.value.usernameForm.username.value
      if (newUserName != _state.value.duplicateUser?.name) {
        handleIntent(BtWifiScaleSetupIntent.ReplaceAccount(newUserName))
      }
    } else if (currentState.currentStep == BtWifiSetupStep.WIFI_PASSWORD) {
      connectToWifi()
    } else if (currentState.currentStep == BtWifiSetupStep.AVAILABLE_WIFI_LIST) {
      // Check if WiFi is already connected
      if (!currentState.connectedSSID.isNullOrEmpty()) {
        // WiFi already connected, go to customization
        AppLog.d(TAG, "WiFi already connected, navigating to CUSTOMIZE_SETTINGS")
        handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
        // Prevent automatic progression to next step
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
        AppLog.d(TAG, "Set canProceedToNext to false to prevent auto-progression")
        return
      } else {
        // No WiFi connected, user needs to select a network first
        // The canProceedToNext should already be false, so this shouldn't happen
        AppLog.w(TAG, "User clicked Next on WiFi list without selecting a network")
        return
      }
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
          // For other steps (like SCALE_INFO), let the normal flow continue
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
    when (currentState.currentStep) {
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

  /**
   * Handles skipping the current step.
   */
  private fun onSkip() {
    val currentState = state.value
    AppLog.d(TAG, "Skipping current step: ${currentState.currentStep}")

    when (currentState.currentStep) {
      BtWifiSetupStep.GATHERING_NETWORK,
      BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
        // Skip to CUSTOMIZE_SETTINGS
        ggDeviceService.cancelWifi(discoveredScale?.toGGBTDevice()!!) {}
        handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
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
    viewModelScope.launch {
      if (discoveredScale != null) {
        ggDeviceService.cancelWifi(discoveredScale!!.toGGBTDevice()) {}
        if (!deviceConfigured) {
          ggDeviceService.disconnectDevice(discoveredScale!!.toGGBTDevice())
        }
      }
      ggDeviceService.resumeScan(false)
      navigateBack()
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
   */
  private fun handlePasswordNetworkStatus() {
    val currentState = state.value
    val isNoPasswordNetwork = currentState.wifiPasswordForm.noPasswordNetwork.value
    AppLog.d(TAG, "Handling password network status, isNoPasswordNetwork: $isNoPasswordNetwork")
    if (isNoPasswordNetwork) {
      // No password network - remove required validation
      currentState.wifiPasswordForm.password.removeValidator("required")
      // Clear password value since it's not needed
      currentState.wifiPasswordForm.password.reset()
    } else {
      // Password network - add required validation
      currentState.wifiPasswordForm.password.addValidator(
        com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.required(),
      )
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
      val canProceed = if (currentState.wifiPasswordForm.noPasswordNetwork.value) {
        currentState.wifiPasswordForm.ssid.isValueValid()
      } else {
        currentState.wifiPasswordForm.ssid.isValueValid() && currentState.wifiPasswordForm.password.isValueValid()
      }
      handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(canProceed))
    }
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
        AppLog.e(TAG, "Failed to navigate back from scale setup", e)
      }
    }
  }

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {

    // Start collecting device scan responses only now
    AppLog.d(TAG, "Starting wake up scale process")

    // Set loading state and prevent automatic next step
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.WAKEUP, ConnectionState.Loading))

    viewModelScope.launch {
      try {
        ggDeviceService.scanForPairing()
        startObservingDevices()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wake up process", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.WAKEUP,
            ConnectionState.Failed.Error,
          ),
        )
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
    if (_state.value.currentStep != BtWifiSetupStep.CONNECTING_BLUETOOTH) {
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.CONNECTING_BLUETOOTH,
          ConnectionState.Loading,
        ),
      )
    }
    viewModelScope.launch {
      try {
        val ggBtDevice = discoveredScale!!.toGGBTDevice()
        ggDeviceService.pairDevice(
          device = ggBtDevice,
        ) { it ->
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED -> {
              viewModelScope.launch {
                fetchUserList()
                deviceConfigured = true
                handleIntent(
                  BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.CONNECTING_BLUETOOTH,
                    ConnectionState.Success,
                  ),
                )
                deviceService.saveScale(discoveredScale!!)
                handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
              }
            }

            GGUserActionResponseType.CREATION_FAILED -> {
              handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                  BtWifiSetupStep.CONNECTING_BLUETOOTH,
                  ConnectionState.Failed.Error,
                ),
              )
              handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_001"))
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            }

            GGUserActionResponseType.DUPLICATE_USER_ERROR -> {
              viewModelScope.launch {
                // Get duplicate username from either scale preferences or active account
                val duplicateUserName = discoveredScale?.preferences?.displayName
                  ?: _state.value.usernameForm.username.value.takeIf { it.isNotEmpty() }
                  ?: accountService.activeAccountFlow.first()?.firstName

                if (duplicateUserName != null) {
                  AppLog.d(TAG, "Found duplicate user: $duplicateUserName")
                  fetchUserList(duplicateUserName = duplicateUserName)
                  handleIntent(SetCurrentStep(BtWifiSetupStep.DUPLICATES_FOUND))
                } else {
                  // If we still can't get a username, log error and show generic error
                  AppLog.e(TAG, "Could not determine duplicate username")
                  handleIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                      BtWifiSetupStep.CONNECTING_BLUETOOTH,
                      ConnectionState.Failed.Error,
                    ),
                  )
                  // Use BT_001 for duplicate user error (consistent with existing error code)
                  handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_001"))
                }
              }
            }

            GGUserActionResponseType.MEMORY_FULL -> {
              viewModelScope.launch {
                fetchUserList(
                  onSuccess = {
                    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                    handleIntent(
                      SetCurrentStep(BtWifiSetupStep.USER_LIMIT_REACHED),
                    )
                  },
                )

              }
            }

            else -> null

          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during bluetooth connection", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.CONNECTING_BLUETOOTH,
            ConnectionState.Failed.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
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
      handleIntent(BtWifiScaleSetupIntent.SetErrorCode("BT_002"))
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
          handleIntent(BtWifiScaleSetupIntent.SetWifiList(it.wifi))
          ggDeviceService.getConnectedWifiSSID(discoveredScale!!.toGGBTDevice()) { wifiMac ->
            val connectedSSID = wifiMac.cleanCorruptedChars()
            handleIntent(BtWifiScaleSetupIntent.SetConnectedSSID(connectedSSID))

            // Check if WiFi is already connected
            if (connectedSSID.isNotEmpty()) {
              // WiFi already connected, user can proceed to customization
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            } else {
              // No WiFi connected, user needs to select a network first
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
            }
            handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
          }
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during network gathering", e)
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.GATHERING_NETWORK,
          ConnectionState.Failed.Error,
        ),
      )
      handleIntent(BtWifiScaleSetupIntent.SetErrorCode("NET_002"))
      handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
    }
  }

  /**
   * Handles wifi connection process.
   */
  private fun connectToWifi() {
    AppLog.d(TAG, "Starting wifi connection process")
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.CONNECTING_WIFI,
        ConnectionState.Loading,
      ),
    )
    try {
      val ssid = _state.value.wifiPasswordForm.ssid.value
      val password = _state.value.wifiPasswordForm.password.value
      ggDeviceService.setupWifi(
        discoveredScale!!.toGGBTDevice(),
        GGBTWifiConfig(ssid, password),
      ) {
        viewModelScope.launch {
          if (it.wifiState == GGWifiState.GG_WIFI_STATE_CONNECTED.name) {
            AppLog.d(TAG, "Wifi connection successful")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Success,
              ),
            )
            updateWifiDetails()
            if (initialStep == BtWifiSetupStep.GATHERING_NETWORK) {
              onExitSetup(true)
              return@launch
            }
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
          } else {
            AppLog.w(TAG, "Wifi connection failed")
            handleIntent(
              BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Failed.Error,
              ),
            )
            handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WIFI_001"))
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          }
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during wifi connection", e)
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.CONNECTING_WIFI,
          ConnectionState.Failed.Error,
        ),
      )
      handleIntent(BtWifiScaleSetupIntent.SetErrorCode("WIFI_002"))
      handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    }
  }

  private suspend fun updateWifiDetails() {
    val device = discoveredScale?.toGGBTDevice() ?: return

    val wifiMac = suspendCancellableCoroutine<String?> { cont ->
      ggDeviceService.getConnectedWifiMacAddress(device) { mac ->
        cont.resume(mac)
      }
    }

    suspendCancellableCoroutine<String?> { cont ->
      ggDeviceService.getConnectedWifiSSID(device) { ssid ->
        cont.resume(ssid)
      }
    }

    discoveredScale = discoveredScale?.copy(
      device = discoveredScale?.device?.copy(
        isWifiConfigured = wifiMac != null,
        wifiMacAddress = wifiMac,
      ),
    )
  }

  private fun stepOn() {
    AppLog.d(TAG, "Starting wifi connection process")
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.STEP_ON,
        ConnectionState.Loading,
      ),
    )
    try {
      subscribeToLiveData()
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during wifi connection", e)
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.STEP_ON,
          ConnectionState.Failed.Error,
        ),
      )
      handleIntent(BtWifiScaleSetupIntent.SetErrorCode("STEP_ON_002"))
      handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    }
  }

  private fun collectMeasurement() {
    AppLog.d(TAG, "Starting wifi connection process")
    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    viewModelScope.launch {
      try {
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.MEASUREMENT,
            ConnectionState.Loading,
          ),
        )
        delay(5 * 60 * 1000)
        throw Exception("Measurement failed")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wifi connection", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.MEASUREMENT,
            ConnectionState.Failed.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("MEASUREMENT_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
      }
    }
  }

  // USER NAVIGATES TO UPDATE SETTINGS SLIDE AFTER CUSTOMISED SCALE SETTINGS WHILE 0412 SETUP
  private fun updateSettings() {
    AppLog.d(TAG, "Starting settings update process")

    handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
    handleIntent(
      BtWifiScaleSetupIntent.SetStepConnectionState(
        BtWifiSetupStep.UPDATE_SETTINGS,
        ConnectionState.Loading,
      ),
    )
  }

  private fun updateDevicePreferences(dashboardKeys: List<DashboardKey>? = null, preferences: Preferences? = null) {
    viewModelScope.launch {
      try {
        if (dashboardKeys != null) {
          dashboardService.updateVisibleKeys(keys = dashboardKeys)
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
                  deviceService.updateScalePreferencesByMac(
                    updatedDevice.device?.macAddress ?: "",
                    updatedDevice.preferences!!.toR4ScalePreferenceApiModel(),
                  )
                  handleIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                      BtWifiSetupStep.UPDATE_SETTINGS,
                      ConnectionState.Success,
                    ),
                  )
                  handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                  handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
                }
              }

              else -> null
            }
          }
          deviceService.updateScalePreferences(discoveredScale!!.id, preferences.toR4ScalePreferenceApiModel())
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during settings update", e)
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.UPDATE_SETTINGS,
            ConnectionState.Failed.Error,
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
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (ggDeviceDetail.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value) {
          viewModelScope.launch {

            if (deviceService.pairedScales.first().any { it.device?.macAddress == ggDeviceDetail.macAddress }) {
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
              stopObservingDevices()
              customizeDevice(ggDeviceDetail)
              AppLog.d(TAG, "Wake up successful, proceeding to next step")
              handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                  BtWifiSetupStep.WAKEUP,
                  ConnectionState.Success,
                ),
              )
              handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
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
        viewModelScope.launch {
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.MEASUREMENT,
              ConnectionState.Success,
            ),
          )
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(SetCurrentStep(BtWifiSetupStep.SETUP_FINISHED))
        }
      }

      else -> null
    }
  }

  private suspend fun customizeDevice(ggDeviceDetail: GGDeviceDetail) {
    val username = accountService.activeAccountFlow.first()?.firstName ?: "Default"
    _state.value.usernameForm.username.onValueChange(username)
    val token = deviceService.getScaleToken()
    val device = Device(
      device = ggDeviceDetail,
      token = token,
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
          handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
          handleIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
          startObservingEntries()
        }

        else -> null
      }
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
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e)
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
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
          }
        },
      ),
    )
  }

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    openInAppBrowser(url)
  }
}
