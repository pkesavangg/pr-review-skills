package com.greatergoods.meapp.features.scaleDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.helper.StringUtil.cleanCorruptedChars
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsReducer
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleNameDialogFormControls
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleDetailsStrings
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleNameDialogStrings
import com.greatergoods.meapp.features.scaleDetails.strings.WifiMacAddressStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the ScaleDetails screen. Handles scale details logic and navigation.
 */
@HiltViewModel(
  assistedFactory = ScaleDetailsViewModel.Factory::class,
)
class ScaleDetailsViewModel
@AssistedInject
constructor(
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleDetailsState, ScaleDetailsIntent>(
  reducer = ScaleDetailsReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleDetailsViewModel
  }

  override fun provideInitialState(): ScaleDetailsState = ScaleDetailsState(
    scaleNameForm = FormGroup(ScaleNameDialogFormControls.Companion.create()),
  )

  override fun handleIntent(intent: ScaleDetailsIntent) {
    super.handleIntent(intent)
    when (intent) {
      ScaleDetailsIntent.EditName -> {
        // TODO: Handle edit name
      }

      ScaleDetailsIntent.DeleteScale -> {
        deleteScaleAlert()
      }

      ScaleDetailsIntent.OpenProductGuide -> {
        openProductGuide()
      }

      ScaleDetailsIntent.Back -> {
        navigateBack()
      }

      ScaleDetailsIntent.OpenScaleMode -> {
        openScaleMode()
      }

      ScaleDetailsIntent.OpenScaleDisplayMetrics -> {
        openScaleDisplayMetrics()
      }

      ScaleDetailsIntent.OpenScaleUsers -> openScaleUsers()

      ScaleDetailsIntent.OpenWiFiSetup -> openWiFiSetup()

      ScaleDetailsIntent.ShowScaleNameModal -> openScaleNameModal()
      ScaleDetailsIntent.UpdateScaleName -> updateScaleName()
      is ScaleDetailsIntent.OnCopyMacAddress -> onCopyMacAddress(intent.isCopied)
      is ScaleDetailsIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      else -> {}
    }
  }

  init {
    setScaleDetails()
    observePermissions()
    val scaleName = state.value.scale?.nickname ?: SCALES.find { it.sku == state.value.scale?.sku }!!.productName
    handleIntent(ScaleDetailsIntent.SetScaleName(scaleName))
    configureR4ScaleDetails()
  }

  private fun configureR4ScaleDetails() {
    viewModelScope.launch {
      if (state.value.scale?.device?.wifiMacAddress != null) {
        ggDeviceService.getConnectedWifiSSID(state.value.scale!!.toGGBTDevice()) { ssid ->
          handleIntent(ScaleDetailsIntent.SetConnectedSSID(ssid.cleanCorruptedChars()))
        }
      }
    }
  }

  private fun openWiFiSetup() {
    viewModelScope.launch {
      val scale = state.value.scale
      if (scale != null) {
        ggDeviceService.addCacheDevice(scale.device?.broadcastId, scale)
        navigationService.navigateTo(
          AppRoute.ScaleSetup.BtWifiScaleSetup(
            scale.sku ?: "0412",
            BtWifiSetupStep.GATHERING_NETWORK,
            scale.device?.broadcastId,
          ),
        )
      }
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect {
        handleIntent(ScaleDetailsIntent.SetPermissions(it))
      }
    }
  }

  private fun setScaleDetails() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        val device = devices.find { it.id == scaleId }
        device?.let { scaleDevice ->
          handleIntent(ScaleDetailsIntent.SetScaleInfo(scaleDevice))
        }
      }
    }
  }

  private fun openProductGuide() {
    val sku = state.value.scale?.getSKU()
    if (!sku.isNullOrEmpty()) {
      val url = "${AppConfig.PRODUCT_URL}/$sku"
      openInAppBrowser(url)
    }
  }

  private fun openScaleMode() {
    viewModelScope.launch {
      if (!state.value.scale
          ?.id
          .isNullOrEmpty()
      ) {
        navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(state.value.scale!!.id))
      }
    }
  }

  private fun openScaleDisplayMetrics() {
    viewModelScope.launch {
      if (!state.value.scale
          ?.id
          .isNullOrEmpty()
      ) {
        navigationService.navigateTo(AppRoute.ScaleDetails.ScaleDisplayMetrics(state.value.scale!!.id))
      }
    }
  }

  private fun deleteScaleAlert() {
    viewModelScope.launch {
      dialogQueueService.showDialog(
        DialogModel.Confirm(
          message = ScaleDetailsStrings.DeleteScaleConfirmation,
          confirmText = ScaleDetailsStrings.Delete,
          cancelText = ScaleDetailsStrings.Cancel,
          onConfirm = {
            dialogQueueService.dismissCurrent()
            dialogQueueService.showLoader(message = ScaleDetailsStrings.DeleteLoaderMessage)
            viewModelScope.launch {
              deviceService.deleteScale(state.value.scale!!.id)
              ggDeviceService.deleteAccount(state.value.scale!!.toGGBTDevice(), true) {
                if (it == GGUserActionResponseType.DELETE_COMPLETED) {
                  dialogQueueService.showToast(
                    Toast(
                      message = ScaleDetailsStrings.DeleteSuccessMessage,
                    ),
                  )
                } else {
                  dialogQueueService.showToast(
                    Toast(
                      message = ScaleDetailsStrings.DeleteErrorMessage,
                    ),
                  )
                }
              }
              dialogQueueService.dismissLoader()
              navigateBack()
            }
          },
          onDismiss = {
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    }
  }

  private fun openScaleUsers() {
    viewModelScope.launch {
      if (!state.value.scale
          ?.id
          .isNullOrEmpty()
      ) {
        navigationService.navigateTo(AppRoute.ScaleDetails.ScaleUsers(state.value.scale!!.id))
      }
    }
  }

  /**
   * Opens the Forgot Password modal.
   */
  private fun openScaleNameModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.ScaleName,
        params = mapOf(
          "scaleId" to scaleId,
        ),
      ),
    )
  }

  /**
   * Handles scale name update with loader and error handling.
   * @param scaleName The scale name to update scale nickname for btwifi scale.
   */
  private fun updateScaleName() {
    if (!state.value.scaleNameForm.isValid) {
      return
    }
    val scaleName = state.value.scaleNameForm.controls.name.value
    dialogQueueService.showLoader(
      message = ScaleNameDialogStrings.LoaderMessage,
    )
    viewModelScope.launch {
      try {
        deviceService.updateScaleNickname(state.value.scale!!.id, scaleName)
        AppLog.i("SaveScaleName", "Updated scale name: $scaleName")
        showToast(ScaleNameDialogStrings.Toast.Success)
        dialogQueueService.dismissCurrent()
      } catch (e: Exception) {
        AppLog.e("SaveScaleName", "Reset Password failed", e.toString())
        showToast(ScaleNameDialogStrings.Toast.Error)
      } finally {
        dialogQueueService.dismissLoader()
        state.value.scaleNameForm.resetForm()
      }
    }
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
        AppLog.e("requestPermission", "Error requesting permission ${permissionType}", e.toString())
      }
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

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }
}
