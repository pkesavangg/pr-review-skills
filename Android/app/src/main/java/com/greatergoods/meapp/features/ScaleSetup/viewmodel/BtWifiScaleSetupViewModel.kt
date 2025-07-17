package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGBTWifiConfig
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGLiveDataResponse
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.ggbluetoothsdk.external.enums.GGWifiState
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.Preferences
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
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
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.DialogModel
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
  @Assisted private val sku: String,
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
    fun create(sku: String): BtWifiScaleSetupViewModel
  }

  private val TAG = "BtWifiScaleSetupViewModel"

  override fun provideInitialState(): BtWifiScaleSetupState = BtWifiScaleSetupState()

  override fun handleIntent(intent: BtWifiScaleSetupIntent) {
    when (intent) {
      is BtWifiScaleSetupIntent.ReplaceAccount -> {
        replaceAccount(intent.userName)
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
  }

  private fun replaceAccount(userName: String? = null) {
    viewModelScope.launch {
      if (userName == null) {
        ggDeviceService.deleteAccount(discoveredScale!!.toGGBTDevice()) {
          if (it == GGUserActionResponseType.DELETE_COMPLETED) {
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
          }
        }
      } else {
        val newToken = deviceService.getScaleToken()
        discoveredScale =
          discoveredScale!!.copy(
            preferences = discoveredScale!!.preferences?.copy(displayName = userName),
            token = newToken,
          )
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
      }

    }
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
        if (!areRequiredPermissionsEnabled && state.value.currentStep == BtWifiSetupStep.PERMISSIONS) {
          handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
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
    } else if (currentState.currentStep == BtWifiSetupStep.DUPLICATES_FOUND) {
      val newUserName = _state.value.usernameForm.username.value
      if (newUserName != _state.value.duplicateUser?.name) {
        handleIntent(BtWifiScaleSetupIntent.ReplaceAccount(newUserName))
      }
    } else if (currentState.currentStep == BtWifiSetupStep.WIFI_PASSWORD) {
      connectToWifi()
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
      ggDeviceService.resumeScan(clearOnlyPairing = false)
      val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
      ggDeviceService.syncDevices(pairedDevices)
      if (discoveredScale != null)
        deviceService.onDeviceUpdate(discoveredScale!!)
      navigateBack()
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
        AppLog.e(TAG, "Failed to navigate back from scale setup", e.toString())
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
    viewModelScope.launch {
      try {
        val ggBtDevice = discoveredScale!!.toGGBTDevice()
        ggDeviceService.pairDevice(
          device = ggBtDevice,
        ) {
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED -> {
              viewModelScope.launch {
                fetchUserList()
                handleIntent(
                  BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.CONNECTING_BLUETOOTH,
                    ConnectionState.Success,
                  ),
                )
                deviceService.saveScale(discoveredScale!!)
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
              viewModelScope.launch {
                val duplicateUserName = discoveredScale?.preferences?.displayName
                fetchUserList(duplicateUserName = duplicateUserName)
                handleIntent(
                  SetCurrentStep(BtWifiSetupStep.DUPLICATES_FOUND),
                )
              }
            }

            GGUserActionResponseType.MEMORY_FULL -> {
              viewModelScope.launch {
                fetchUserList()
                handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                handleIntent(
                  SetCurrentStep(BtWifiSetupStep.USER_LIMIT_REACHED),
                )
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
  }

  private suspend fun fetchUserList(duplicateUserName: String? = null) {
    try {
      val userList = suspendCoroutine<List<GGBTUser>> { continuation ->
        ggDeviceService.getUsers(discoveredScale!!.toGGBTDevice()) { response ->
          if (duplicateUserName != null) {
            val user = response.user.first { it.name == duplicateUserName }
            discoveredScale = discoveredScale?.copy(token = user.token)
            handleIntent(BtWifiScaleSetupIntent.SetDuplicateUser(user))
          }
          continuation.resume(response.user)
        }
      }

      handleIntent(BtWifiScaleSetupIntent.SetUserList(userList))
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during fetching user list", e.toString())
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
          val canRequestNotifPermission = AppPermissionsHelper.canRequestNotificationPermission(state.value.permissions)
          if (canRequestNotifPermission) {
            requestPermission(GGPermissionType.NOTIFICATION)
          }
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
            handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
            handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
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
      AppLog.e(TAG, "Error during wifi connection", e.toString())
      handleIntent(
        BtWifiScaleSetupIntent.SetStepConnectionState(
          BtWifiSetupStep.STEP_ON,
          ConnectionState.Error,
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
        AppLog.e(TAG, "Error during wifi connection", e.toString())
        handleIntent(
          BtWifiScaleSetupIntent.SetStepConnectionState(
            BtWifiSetupStep.MEASUREMENT,
            ConnectionState.Error,
          ),
        )
        handleIntent(BtWifiScaleSetupIntent.SetErrorCode("MEASUREMENT_002"))
        handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(false))
      }
    }
  }

  private fun saveEntry() {
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
  }

  private fun updateDevicePreferences(dashboardKeys: List<DashboardKey>? = null, preferences: Preferences? = null) {
    viewModelScope.launch {
      try {
        if (dashboardKeys != null) {
          dashboardService.updateVisibleKeys(keys = dashboardKeys)
        }
        if (preferences != null) {
          discoveredScale = discoveredScale!!.copy(preferences = preferences)
          ggDeviceService.updateAccount(
            discoveredScale!!.toGGBTDevice(),
          ) {
            when (it) {
              GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
                handleIntent(
                  BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.UPDATE_SETTINGS,
                    ConnectionState.Success,
                  ),
                )
                handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
                handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
              }

              else -> null
            }
          }
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
              handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                  BtWifiSetupStep.WAKEUP,
                  ConnectionState.Success,
                ),
              )
              customizeDevice(ggDeviceDetail)
              AppLog.d(TAG, "Wake up successful, proceeding to next step")
              delay(1000)
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
      GGScanResponseType.SINGLE_ENTRY, GGScanResponseType.MULTI_ENTRIES -> {
        viewModelScope.launch {
          handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
              BtWifiSetupStep.MEASUREMENT,
              ConnectionState.Success,
            ),
          )
          delay(1000)
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
    val updatePreference = ScaleMetricsHelper.getDefaultPreference(username)
    val device = Device(
      device = ggDeviceDetail,
      preferences = updatePreference,
      token = token,
    )
    discoveredScale = device
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
          viewModelScope.launch {
            val deleteDevice = discoveredScale?.copy(
              preferences = discoveredScale?.preferences?.copy(
                displayName = user.name,
                shouldMeasureImpedance = user.isBodyMetricsEnabled,
              ),
              token = user.token,
            )
            ggDeviceService.deleteAccount(deleteDevice!!.toGGBTDevice()) {
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
