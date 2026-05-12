package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.ScaleSetup.ScaleSetupConstants
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.MonitorSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.DeviceSearchInfo
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.MonitorSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.MonitorSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.MonitorSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.MonitorSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Unified ViewModel for BPM monitor setup — handles both A3 and A6 protocols.
 * Protocol, step list, and companion scale support are all derived from the SKU.
 */
@HiltViewModel(
  assistedFactory = MonitorSetupViewModel.Factory::class,
)
class MonitorSetupViewModel
@AssistedInject
constructor(
    @Assisted private val monitorInit: SetupInitData<MonitorSetupStep>,
    dependencies: BLESetupDependencies,
) : BLESetupViewmodel<MonitorSetupStep, MonitorSetupState>(
  MonitorSetupStepHelper.protocolForSku(monitorInit.sku),
  monitorInit,
  reducer = MonitorSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(monitorInit: SetupInitData<MonitorSetupStep>): MonitorSetupViewModel
  }

  private val sku get() = monitorInit.sku
  private val primarySku = DeviceHelper.primaryBpmSku(monitorInit.sku)
  private val isA6 = MonitorSetupStepHelper.isA6Sku(monitorInit.sku)
  private val setupType = MonitorSetupStepHelper.setupTypeForSku(monitorInit.sku)
  private var deviceInfo: GGDeviceDetail? = null
  private var monitorToDelete: Device? = null
  private var existingDevices = listOf<Device>()

  init {
    AppLog.d(TAG, "MonitorSetupViewModel initialized for SKU: $sku, isA6: $isA6")
    lazyInit()

    val steps = MonitorSetupStepHelper.stepsForSku(sku).toImmutableList()
    handleIntent(MonitorSetupIntent.SetSteps(steps))

    val hasNumericUsers = primarySku == "0603"
    handleIntent(MonitorSetupIntent.SetHasNumericUsers(hasNumericUsers))
    handleIntent(MonitorSetupIntent.SetSelectedUser(if (hasNumericUsers) "1" else "A"))
    handleIntent(MonitorSetupIntent.SetMonitorNickname(MonitorSetupStrings.MonitorNameBySku(primarySku)))

    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        existingDevices = devices
      }
    }
  }

  override fun provideInitialState(): MonitorSetupState = MonitorSetupState()

  override fun handleIntent(intent: ScaleSetupIntent) {
    super.handleIntent(intent)
    when (intent) {
      MonitorSetupIntent.TutorialLinkClicked -> navigateToTutorial()
      else -> {}
    }
  }

  private fun navigateToTutorial() {
    val nextStep = if (isA6) MonitorSetupStep.SCALE_INTRO else MonitorSetupStep.INSTRUCTION_CUFF
    AppLog.d(TAG, "Tutorial link clicked — navigating to $nextStep")
    handleIntent(ScaleSetupIntent.SetNewStep(nextStep))
  }

  // ── Setup finish ──────────────────────────────────────────────────────────

  override suspend fun onSetupFinished() {
    dialogQueueService.showLoader(ScaleSetupStrings.SaveScaleLoader)
    try {
      if (discoveredScale != null) {
        saveMonitor(deviceInfo)
        delay(ScaleSetupConstants.DELAY_AFTER_SAVE_MS)
        AppLog.i(TAG, "Successfully saved BPM monitor")
      } else {
        AppLog.w(TAG, "No discovered monitor to save")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving BPM monitor", e)
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  // ── Permissions ───────────────────────────────────────────────────────────

  override fun observePermissions() {
    AppLog.d(TAG, "Starting permission observation for BPM monitor setup")
    viewModelScope.launch {
      try {
        subscribePermissions().collect { newPermissions ->
          viewModelScope.launch {
            val arePermissionsEnabled =
              AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, setupType = setupType)
            handleIntent(ScaleSetupIntent.NextEnabled(arePermissionsEnabled))
            handleIntent(ScaleSetupIntent.SetPermissions(newPermissions))
            if (isPermissionGranted != arePermissionsEnabled) {
              isPermissionGranted = arePermissionsEnabled
            }
            if (!arePermissionsEnabled) {
              if (currentSetupState.step != MonitorSetupStep.PERMISSIONS && currentSetupState.step != MonitorSetupStep.MONITOR_DETAIL) {
                handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
              }
            }
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing permissions", e)
      }
    }
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  override fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.step}")

    if (currentState.isLastStep || currentState.step == MonitorSetupStep.SUCCESS_SCREEN) {
      AppLog.d(TAG, "Completing setup from: ${currentState.step}")
      handleIntent(ScaleSetupIntent.ExitSetup(true))
      return
    }

    when (currentState.step) {
      MonitorSetupStep.MONITOR_DETAIL -> {
        val arePermissionsEnabled = AppPermissionsHelper
          .areRequiredPermissionsEnabled(state.value.permissions, setupType = setupType)
        if (arePermissionsEnabled) {
          handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.USER_SELECTION))
        } else {
          handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
          permissionAccess()
        }
      }

      MonitorSetupStep.MONITOR_NICKNAME -> {
        handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.SUCCESS_SCREEN))
      }

      MonitorSetupStep.SCALE_INTRO -> {
        handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.SCALE_PAIRING_INSTRUCTION))
      }

      else -> currentState.nextStep?.let { handleIntent(ScaleSetupIntent.SetNewStep(it)) }
    }
  }

  override fun onBack() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to previous step from: ${currentState.step}")

    when {
      currentState.isFirstStep -> navigateTo(AppRoute.AccountSettings.MyDevices)

      currentState.step == MonitorSetupStep.USER_SELECTION && isPermissionGranted ->
        handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_DETAIL))

      currentState.step == MonitorSetupStep.MONITOR_PAIRING -> {
        stopPairingDevices()
        currentState.previousStep?.let { handleIntent(ScaleSetupIntent.SetNewStep(it)) }
      }

      currentState.step == MonitorSetupStep.SCALE_PAIRING_INSTRUCTION -> {
        handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.SCALE_INTRO))
      }

      else -> currentState.previousStep?.let { handleIntent(ScaleSetupIntent.SetNewStep(it)) }
    }
  }

  override fun onSkip() {
    val currentStep = state.value.step
    AppLog.d(TAG, "Skipping current step: $currentStep")

    when (currentStep) {
      MonitorSetupStep.SCALE_INTRO,
      MonitorSetupStep.SCALE_PAIRING_INSTRUCTION -> showSkipScalePairingDialog()

      else -> onNext()
    }
  }

  override fun onStepChange(step: ScaleSetupStep) {
    AppLog.d(TAG, "Step changed to: $step")
    when (step) {
      MonitorSetupStep.MONITOR_PAIRING -> connectToBluetooth()
      MonitorSetupStep.SCALE_PAIRING_INSTRUCTION -> Unit // Instruction-only by design — see enum doc.
      else -> AppLog.d(TAG, "No specific action for step: $step")
    }
  }

  override fun handleButtonChanges(step: MonitorSetupStep) {
    val backEnabled = when (step) {
      MonitorSetupStep.MONITOR_DETAIL,
      MonitorSetupStep.MONITOR_NICKNAME,
      MonitorSetupStep.SUCCESS_SCREEN,
      MonitorSetupStep.INSTRUCTION_CUFF,
      MonitorSetupStep.INSTRUCTION_START,
      MonitorSetupStep.SETUP_COMPLETED -> false
      else -> true
    }
    val nextEnabled = when (step) {
      MonitorSetupStep.MONITOR_PAIRING -> false

      MonitorSetupStep.USER_SELECTION -> _state.value.selectedUser != null

      MonitorSetupStep.MONITOR_NICKNAME -> _state.value.monitorNickname.isNotBlank()

      MonitorSetupStep.PERMISSIONS ->
        AppPermissionsHelper.areRequiredPermissionsEnabled(
          state.value.permissions,
          setupType = setupType,
        )

      // Instruction-only step — Next is always enabled so the user can advance past it.
      MonitorSetupStep.SCALE_PAIRING_INSTRUCTION -> true

      else -> true
    }
    handleIntent(ScaleSetupIntent.BackEnabled(backEnabled))
    handleIntent(ScaleSetupIntent.NextEnabled(nextEnabled))
  }

  override fun onTryAgain() {
    when (state.value.step) {
      MonitorSetupStep.MONITOR_PAIRING -> connectToBluetooth()
      MonitorSetupStep.SCALE_PAIRING_INSTRUCTION -> Unit // Instruction-only by design — no retry action.
      else -> AppLog.w(TAG, "Try again not applicable for step: ${state.value.step}")
    }
  }

  // ── BPM pairing ───────────────────────────────────────────────────────────

  private fun connectToBluetooth(retryCount: Int = 0) {
    clearBluetoothTimeout()
    stopObservingDevices()

    AppLog.d(TAG, "Scanning for BPM monitor: $sku (attempt ${retryCount + 1})")
    ggDeviceService.scanForPairing()
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    try {
      startObservingDevices { data ->
        if (_state.value.step != MonitorSetupStep.MONITOR_PAIRING) {
          AppLog.d(TAG, "Ignoring device found — not on pairing step (current: ${_state.value.step})")
          return@startObservingDevices
        }
        AppLog.d(TAG, "BPM device found: ${data.deviceName}")
        discoveredScale = Device(
            device = data,
            deviceType = setupType.value,
            sku = sku,
            userNumber = selectedUserToNumber(),
        )
        clearBluetoothTimeout()

        val pairedMonitor = discoveredScale ?: return@startObservingDevices
        ggDeviceService.pairDevice(pairedMonitor.toGGBTDevice()) {
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED -> {
              AppLog.d(TAG, "BPM device pairing completed successfully")
              checkIsKnownMonitor()
            }

            GGUserActionResponseType.DIFFERENT_USER -> {
              AppLog.w(TAG, "BPM reported different user than selected")
              showDifferentUserDialog()
            }

            else -> {
              AppLog.w(TAG, "BPM device pairing failed with response: $it")
              showRetryDialog()
              handleIntent(
                  ScaleSetupIntent.AlterConnectionState(
                      ConnectionState.Failed.ErrorWithMessage(
                          ScaleSetupConstants.ERROR_WAKEUP_001)))
            }
          }
        }
      }

      bluetoothTimeoutJob = viewModelScope.launch {
        delay(bluetoothTimeout)
        if (discoveredScale == null) {
          AppLog.d(TAG, "Bluetooth scan timeout reached")
          showRetryDialog()
          handleIntent(
              ScaleSetupIntent.AlterConnectionState(
                  ConnectionState.Failed.ErrorWithMessage(
                      ScaleSetupConstants.ERROR_WAKEUP_001)))
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during BPM pairing process", e)
      clearBluetoothTimeout()
      if (retryCount < MAX_SCAN_RETRIES) {
        AppLog.d(TAG, "Retrying scan after error (attempt ${retryCount + 1}/$MAX_SCAN_RETRIES)")
        viewModelScope.launch {
          delay(SCAN_RETRY_DELAY_MS)
          connectToBluetooth(retryCount + 1)
        }
      } else {
        AppLog.e(TAG, "Max scan retries reached")
        showRetryDialog()
        handleIntent(
            ScaleSetupIntent.AlterConnectionState(
                ConnectionState.Failed.ErrorWithMessage(
                    ScaleSetupConstants.ERROR_WAKEUP_002)))
      }
    }
  }

  private fun checkIsKnownMonitor() {
    viewModelScope.launch {
      try {
        val monitorForInfo = discoveredScale ?: return@launch
        ggDeviceService.getDeviceInfo(monitorForInfo.toGGBTDevice()) { deviceDetails ->
          if (deviceDetails != null) {
            deviceInfo = deviceDetails
          }
          val peripheralIdentifier = deviceInfo?.macAddress?.replace(":", "") ?: ""
          val selectedUser = selectedUserToNumber()
          val searchInfo = checkIfDeviceExists(
            peripheralIdentifier = peripheralIdentifier,
            isSameUser = true,
            userNumber = selectedUser,
          )
          when {
            searchInfo.isMonitorExistsWithSameUser -> {
              // Re-pairing same user on same device — replace existing entry
              monitorToDelete = searchInfo.deviceInfo
              AppLog.d(TAG, "Monitor exists for same user, confirming re-pair")
              confirmSameUserPair()
            }
            searchInfo.isMonitorExistsWithDifferentUser && !isA6 -> {
              // A3: one entry per device — replacing across users is valid
              monitorToDelete = searchInfo.deviceInfo
              AppLog.d(TAG, "A3 monitor exists for different user, confirming user switch")
              confirmDifferentUserPair(searchInfo.deviceInfo!!)
            }
            else -> {
              // A6 with different user: additive — do NOT delete existing user's pairing
              // A6 new device, or A3 new device: just pair
              monitorToDelete = null
              AppLog.d(TAG, "Proceeding with new pairing (isA6=$isA6)")
              onFoundNewA6Monitor()
            }
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error checking existing monitors", e)
      }
    }
  }

  /**
   * Checks whether a device with the given peripheralIdentifier already exists for this user.
   * Ported from Angular DeviceService.checkIfDeviceExists().
   *
   * @param peripheralIdentifier MAC address without colons (A6) or with colons (A3).
   * @param isSameUser Whether to check for an exact user-number match.
   * @param userNumber The selected user number to match against.
   */
  private fun checkIfDeviceExists(
    peripheralIdentifier: String,
    isSameUser: Boolean = false,
    userNumber: Int? = null,
  ): DeviceSearchInfo {
    val normalizedId = peripheralIdentifier.replace(":", "")
    val matchingDevices = existingDevices.filter { device ->
      device.device?.macAddress?.replace(":", "") == normalizedId
    }
    if (matchingDevices.isEmpty()) {
      return DeviceSearchInfo()
    }
    val deviceFound = matchingDevices.find { it.userNumber == userNumber }
    return if (isSameUser && deviceFound != null) {
      DeviceSearchInfo(
        isMonitorExists = true,
        isMonitorExistsWithSameUser = true,
        deviceInfo = deviceFound,
      )
    } else {
      DeviceSearchInfo(
        isMonitorExists = true,
        isMonitorExistsWithDifferentUser = true,
        deviceInfo = matchingDevices.find { it.userNumber != userNumber },
      )
    }
  }

  private fun onFoundNewA6Monitor() {
    AppLog.d(TAG, "Proceeding with pairing flow (isA6=$isA6)")
    successfullyPaired()
  }

  private fun confirmSameUserPair() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = MonitorSetupStrings.ConfirmPairDialog.Title,
        message = MonitorSetupStrings.ConfirmPairDialog.Message,
        confirmText = MonitorSetupStrings.ConfirmPairDialog.ConfirmButton,
        cancelText = MonitorSetupStrings.ConfirmPairDialog.CancelButton,
        onConfirm = {
          AppLog.d(TAG, "User confirmed pairing - replacing existing monitor (same user)")
          successfullyPaired()
        },
        onCancel = {
          viewModelScope.launch {
            AppLog.d(TAG, "User cancelled pairing")
            navigationService.navigateBack()
          }
        },
      ),
    )
  }

  private fun confirmDifferentUserPair(existingDevice: Device) {
    val hasNumericUsers = _state.value.hasNumericUsers
    val existingUserNumber = existingDevice.userNumber
    val userLabel = if (hasNumericUsers) {
      existingUserNumber?.toString() ?: "?"
    } else {
      if (existingUserNumber == 1) "A" else "B"
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = MonitorSetupStrings.ConfirmDifferentUserPairDialog.Title,
        message = MonitorSetupStrings.ConfirmDifferentUserPairDialog.Message(userLabel),
        confirmText = MonitorSetupStrings.ConfirmDifferentUserPairDialog.ReplaceButton,
        cancelText = MonitorSetupStrings.ConfirmDifferentUserPairDialog.CancelButton,
        onConfirm = {
          AppLog.d(TAG, "User confirmed replacing existing monitor (different user)")
          successfullyPaired()
        },
        onCancel = {
          viewModelScope.launch {
            AppLog.d(TAG, "User cancelled different-user pairing")
            navigationService.navigateBack()
          }
        },
      ),
    )
  }

  private fun successfullyPaired() {
    viewModelScope.launch {
      try {
        val monitor = discoveredScale ?: return@launch
        AppLog.d(TAG, "Monitor paired successfully: ${monitor.id}")
        discoveredScale = monitor.copy(
          connectionStatus = BLEStatus.CONNECTED,
          nickname = monitor.nickname.ifBlank { MonitorSetupStrings.DefaultMonitorNickname },
          userNumber = selectedUserToNumber(),
          device = deviceInfo,
        )
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        ggDeviceService.resumeScan()
        val pairedMonitor = discoveredScale ?: return@launch
        ggDeviceService.syncDevices(listOf(pairedMonitor.toGGBTDevice()))
        delay(ScaleSetupConstants.DELAY_AFTER_SAVE_MS)
        onNext()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed during monitor pairing", e)
      }
    }
  }

  private fun showSkipScalePairingDialog() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = MonitorSetupStrings.SkipScaleDialog.Title,
        message = MonitorSetupStrings.SkipScaleDialog.Message,
        confirmText = MonitorSetupStrings.SkipScaleDialog.SkipButton,
        cancelText = MonitorSetupStrings.SkipScaleDialog.CancelButton,
        onConfirm = {
          AppLog.d(TAG, "User skipped companion scale steps")
          handleIntent(MonitorSetupIntent.SetHasSkippedScalePairing(true))
          handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.SUCCESS_SCREEN))
        },
        onCancel = {
          AppLog.d(TAG, "User cancelled skip scale dialog")
        },
      ),
    )
  }

  // ── Persist devices ───────────────────────────────────────────────────────

  private suspend fun saveMonitor(deviceInfo: GGDeviceDetail?) {
    try {
      if (monitorToDelete != null) {
        deviceService.deleteScale(monitorToDelete?.id ?: "")
      }
      val scaleInfo = state.value.scaleSetupState.scaleInfo
      val nickname = _state.value.monitorNickname.ifBlank {
        scaleInfo?.productName ?: MonitorSetupStrings.DefaultMonitorNickname
      }
      discoveredScale = discoveredScale?.copy(
        deviceType = setupType.value,
        device = deviceInfo?.copy(
          deviceName = deviceInfo.deviceName.ifEmpty { scaleInfo?.productName ?: "" },
        ),
        nickname = nickname,
        sku = DeviceHelper.primaryBpmSku(sku),
        createdAt = Instant.now().toString(),
      )
      val updatedMonitor = discoveredScale ?: return
      deviceService.saveScale(updatedMonitor)
      AppLog.d(TAG, "Monitor saved successfully")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed saving monitor", e)
    }
  }

  // ── Dialogs ───────────────────────────────────────────────────────────────

  private fun showRetryDialog() {
    AppLog.d(TAG, "Showing retry dialog")
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = MonitorSetupStrings.RetryAlert.Title,
        message = MonitorSetupStrings.RetryAlert.Message,
        confirmText = MonitorSetupStrings.RetryAlert.RetryButton,
        cancelText = MonitorSetupStrings.RetryAlert.DismissButton,
        onConfirm = {
          AppLog.d(TAG, "User chose to retry scanning")
          connectToBluetooth()
        },
        onCancel = {
          AppLog.d(TAG, "User dismissed retry, returning to monitor detail")
          handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_DETAIL))
        },
      ),
    )
  }

  private fun showDifferentUserDialog() {
    AppLog.d(TAG, "Showing different user dialog")
    handleIntent(
      ScaleSetupIntent.AlterConnectionState(
        ConnectionState.Failed.ErrorWithMessage(ScaleSetupConstants.ERROR_WAKEUP_001),
      ),
    )
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = MonitorSetupStrings.DifferentUserDialog.Title,
        message = MonitorSetupStrings.DifferentUserDialog.Message,
        confirmText = MonitorSetupStrings.DifferentUserDialog.ChangeUserButton,
        cancelText = MonitorSetupStrings.DifferentUserDialog.DismissButton,
        onConfirm = {
          AppLog.d(TAG, "User chose to change user selection")
          stopPairingDevices()
          handleIntent(ScaleSetupIntent.SetNewStep(MonitorSetupStep.USER_SELECTION))
        },
        onCancel = {
          AppLog.d(TAG, "User dismissed different-user dialog, exiting setup")
          handleIntent(ScaleSetupIntent.ExitSetup(false))
        },
      ),
    )
  }

  /**
   * Converts the UI user selection (A/B/1/2) to the numeric user number (1/2)
   * that the BLE protocol requires. A→1, B→2, 1→1, 2→2.
   */
  private fun selectedUserToNumber(): Int? {
    return when (_state.value.selectedUser?.uppercase()) {
      "A", "1" -> 1
      "B", "2" -> 2
      else -> null
    }
  }

  companion object {
    private const val TAG = "MonitorSetupViewModel"
    private const val MAX_SCAN_RETRIES = 10
    private const val SCAN_RETRY_DELAY_MS = 1000L
  }
}
