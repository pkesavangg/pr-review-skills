package com.dmdbrands.gurus.weight.features.settings.manager

import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.DeviceProfileConstants
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

interface IDeviceSettingsManager {
  suspend fun updateR4Profile(profile: GGBTUserProfile): GGUserActionResponseType

  fun handleScaleUpdateResult(scaleResult: GGUserActionResponseType)

  fun loadMacAddressSettings(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun onMacAddressFilterClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  )
}

class DeviceSettingsManager
@Inject
constructor(
  private val bluetoothPreferencesService: BluetoothPreferencesService,
  private val dialogQueueService: IDialogQueueService,
  private val ggDeviceService: GGDeviceService,
) : IDeviceSettingsManager {
  companion object {
    private const val TAG = "DeviceSettingsManager"
  }

  override fun handleScaleUpdateResult(scaleResult: GGUserActionResponseType) {
    when (scaleResult) {
      GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
        dialogQueueService.enqueue(
          DialogModel.Alert(
            title = AppPopupStrings.R4ProfileUpdatePending.Title,
            message = AppPopupStrings.R4ProfileUpdatePending.Message,
            onDismiss = { dialogQueueService.dismissCurrent() },
          ),
        )
      }

      GGUserActionResponseType.CREATION_COMPLETED,
      GGUserActionResponseType.UPDATE_COMPLETED,
      GGUserActionResponseType.CREATION_FAILED,
      -> {
        dialogQueueService.dismissLoader()
        dialogQueueService.showToast(
          Toast.Simple(
            ToastStrings.Success.UpdateProfileSuccess.Message,
            ToastStrings.Success.UpdateProfileSuccess.Header,
          ),
        )
      }

      else -> {
        dialogQueueService.dismissLoader()
        dialogQueueService.showToast(
          Toast.Simple(
            ToastStrings.Success.UpdateProfileSuccess.Message,
            ToastStrings.Success.UpdateProfileSuccess.Header,
          ),
        )
      }
    }
  }

  override suspend fun updateR4Profile(profile: GGBTUserProfile): GGUserActionResponseType {
    val result = CompletableDeferred<GGUserActionResponseType>()
    try {
      ggDeviceService.updateProfile(
        profile,
      ) { responseType ->
        result.complete(responseType)
      }
    } catch (e: Exception) {
      AppLog.d(TAG, "updateR4Profile - Error updating profile to scale: ${e.message}")
      result.complete(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
    }

    return withTimeoutOrNull(DeviceProfileConstants.SCALE_PROFILE_UPDATE_TIMEOUT_MS) { result.await() }
      ?: run {
        AppLog.d(TAG, "updateR4Profile - Timeout or no callback from scale; dismissing loader")
        GGUserActionResponseType.EXCEPTION_ENCOUNTERED
      }
  }

  override fun loadMacAddressSettings(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      try {
        bluetoothPreferencesService.selectedMacAddress.collect { selectedMac ->
          dispatch(SettingsIntent.UpdateSelectedMacAddress(selectedMac))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error loading MAC address settings", e.toString())
      }
    }

    scope.launch {
      try {
        val testingEnabled = bluetoothPreferencesService.enableTestingFeatures
        dispatch(SettingsIntent.UpdateTestingFeatures(testingEnabled))
      } catch (e: Exception) {
        AppLog.e(TAG, "Error loading testing features state", e.toString())
      }
    }
  }

  override fun onMacAddressFilterClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    AppLog.d(TAG, "MAC address filter clicked")

    if (!stateProvider().enableTestingFeatures) {
      AppLog.d(TAG, "Testing features disabled, MAC address filter not available")
      return
    }

    showMacAddressFilterModal(scope, stateProvider, dispatch)
  }

  private fun showMacAddressFilterModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    val knownMacAddresses = bluetoothPreferencesService.knownMacAddresses
    val macAddressOptions = knownMacAddresses.map { macAddress ->
      RadioButtonOption(macAddress, macAddress)
    }

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = "MAC Address Filter (0412 Scales)",
      options = macAddressOptions,
      selectedItem = stateProvider().selectedMacAddress,
      maxHeight = 400.dp,
      onConfirm = { selectedMacAddress ->
        selectedMacAddress?.let { macAddress ->
          onMacAddressSelectionChange(scope, stateProvider, dispatch, macAddress.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "MAC address filter selection cancelled")
      },
    )
  }

  private fun onMacAddressSelectionChange(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
    selectedMacAddress: String,
  ) {
    AppLog.d(TAG, "MAC address selection changed to: $selectedMacAddress")

    if (!stateProvider().enableTestingFeatures) {
      AppLog.w(TAG, "Testing features disabled, ignoring MAC address selection")
      return
    }

    dialogQueueService.showLoader("Updating MAC address filter...")

    scope.launch {
      try {
        bluetoothPreferencesService.setSelectedMacAddressLocally(selectedMacAddress)
        dispatch(SettingsIntent.UpdateSelectedMacAddress(selectedMacAddress))
        AppLog.i(TAG, "Successfully updated MAC address filter to: $selectedMacAddress")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating MAC address filter", e.toString())
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }
}
