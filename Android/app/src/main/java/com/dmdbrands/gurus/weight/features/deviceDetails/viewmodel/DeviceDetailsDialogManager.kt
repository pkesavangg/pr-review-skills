package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the dialog / modal slice of [DeviceDetailsViewModel] (MOB-1500) — delete-scale confirm,
 * time-format & clear-data radio pickers, enable-body-metrics alert and the scale-name modal.
 * Behaviour-preserving verbatim move.
 */
class DeviceDetailsDialogManager(
  private val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  private val scope: CoroutineScope,
  private val scaleId: String,
  private val getState: () -> DeviceDetailsState,
  private val onIntent: (DeviceDetailsIntent) -> Unit,
  private val getDialogQueueService: () -> IDialogQueueService,
  private val getActiveAccountId: () -> String?,
  private val navigateBack: suspend () -> Unit,
) {

  private val TAG = "DeviceDetailsViewModel"

  fun deleteScaleAlert() {
    val dialogQueueService = getDialogQueueService()
    try {
      scope.launch {
        dialogQueueService.showDialog(
          DialogModel.Confirm(
            message = DeviceDetailsStrings.DeleteConfirmation,
            confirmText = DeviceDetailsStrings.Delete,
            cancelText = DeviceDetailsStrings.Cancel,
            primaryActionType = com.dmdbrands.gurus.weight.features.common.components.ButtonType.ErrorText,
            onConfirm = {
              scope.launch {
                val scale = getState().scale ?: return@launch
                dialogQueueService.dismissCurrent()
                dialogQueueService.showLoader(message = DeviceDetailsStrings.DeleteLoaderMessage)
                try {
                  if (scale.deviceType == DeviceSetupType.BtWifiR4.value && scale.connectionStatus == BLEStatus.CONNECTED) {
                    ggDeviceService.deleteAccount(scale.toGGBTDevice()) {
                      if (it == GGUserActionResponseType.DELETE_COMPLETED) {
                        ggDeviceService.disconnectDevice(scale.toGGBTDevice())
                      } else {
                        dialogQueueService.showToast(
                          Toast.Simple(
                            message = DeviceDetailsStrings.DeleteErrorMessage,
                          ),
                        )
                      }
                    }
                  }
                  deviceService.deleteScale(scale.id)
                  dialogQueueService.showToast(
                    Toast.Simple(
                      message = DeviceDetailsStrings.DeleteSuccessMessage,
                    ),
                  )
                } catch (e: Exception) {
                  AppLog.e(TAG, "Error deleting scale: ${e.message}")
                  dialogQueueService.showToast(
                    Toast.Simple(
                      message = DeviceDetailsStrings.DeleteErrorMessage,
                    ),
                  )
                } finally {
                  dialogQueueService.dismissLoader()
                  navigateBack()
                }
              }
            },
            onDismiss = {
              dialogQueueService.dismissCurrent()
            },
          ),
        )
      }
    } catch (e: Exception) {
      dialogQueueService.dismissLoader()
      AppLog.d(TAG, "Error while deleting an scale: ${e.message}")
    }
  }

  /**
   * Opens the Scale Name modal.
   */
  fun openScaleNameModal() {
    getDialogQueueService().enqueue(
      DialogModel.Custom(
        contentKey = DialogType.DeviceName,
        params = mapOf(
          "scaleId" to (getState().scale?.id ?: scaleId),
          "accountId" to (getActiveAccountId() ?: ""),
        ),
        dismissOnBackPress = true,
      ),
    )
  }

  /**
   * Shows the time format selection modal.
   */
  fun showTimeFormatModal() {
    val currentSelection = getState().currentTimeFormat
    showRadioGroupModal(
      dialogService = getDialogQueueService(),
      title = DeviceDetailsStrings.TimeFormat,
      options = listOf(
        RadioButtonOption(DeviceDetailsStrings.TimeFormat12H, DeviceDetailsStrings.TimeFormat12H),
        RadioButtonOption(DeviceDetailsStrings.TimeFormat24H, DeviceDetailsStrings.TimeFormat24H),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        val is12Hour = selectedValue == DeviceDetailsStrings.TimeFormat12H
        onIntent(DeviceDetailsIntent.ChangeTimeFormat(is12Hour))
      },
    )
  }

  /**
   * Shows the clear data selection modal.
   */
  fun showClearDataModal() {
    val currentSelection = getState().currentClearDataSelection
    showRadioGroupModal(
      dialogService = getDialogQueueService(),
      title = DeviceDetailsStrings.ClearData,
      options = listOf(
        RadioButtonOption("ALL", DeviceDetailsStrings.All),
        RadioButtonOption("WIFI", DeviceDetailsStrings.WiFi),
        RadioButtonOption("SETTINGS", DeviceDetailsStrings.Settings),
        RadioButtonOption("HISTORY", DeviceDetailsStrings.History),
        RadioButtonOption("ACCOUNT", DeviceDetailsStrings.Account),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        onIntent(DeviceDetailsIntent.ClearScaleData(selectedValue ?: ""))
      },
    )
  }

  /**
   * Shows the enable body metrics alert dialog.
   */
  fun showEnableBodyMetricsAlert() {
    val dialogQueueService = getDialogQueueService()
    try {
      scope.launch {
        dialogQueueService.showDialog(
          DialogModel.Confirm(
            title = DeviceDetailsStrings.EnableBodyMetricsAlertTitle,
            message = DeviceDetailsStrings.EnableBodyMetricsAlertMessage,
            confirmText = DeviceDetailsStrings.EnableBodyMetricsAlertConfirm,
            cancelText = DeviceDetailsStrings.EnableBodyMetricsAlertCancel,
            onConfirm = {
              scope.launch {
                dialogQueueService.dismissCurrent()
                onIntent(DeviceDetailsIntent.EnableBodyMetrics)
              }
            },
            onDismiss = {
              dialogQueueService.dismissCurrent()
            },
          ),
        )
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error showing enable body metrics alert", e.toString())
    }
  }
}
