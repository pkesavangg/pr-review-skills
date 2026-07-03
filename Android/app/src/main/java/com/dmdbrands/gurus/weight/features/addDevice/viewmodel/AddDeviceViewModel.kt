package com.dmdbrands.gurus.weight.features.addDevice.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.util.DeviceSetupNavigationUtils
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleFormControls
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddDeviceIntent
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddDeviceReducer
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleState
import com.dmdbrands.gurus.weight.features.addDevice.strings.PairedScaleExistsAlert
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel
@Inject
constructor(
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService,
) : BaseIntentViewModel<AddScaleState, AddDeviceIntent>(AddDeviceReducer()) {
  override fun provideInitialState(): AddScaleState =
    AddScaleState(
      form = FormGroup(AddScaleFormControls.create()),
    )

  override fun handleIntent(intent: AddDeviceIntent) {
    AppLog.d(TAG, "Handling add scale intent: ${intent::class.simpleName}")
    super.handleIntent(intent)
    when (intent) {
      is AddDeviceIntent.Submit -> {
        AppLog.d(TAG, "Submit intent received")
        onSubmitModelNumber()
      }

      is AddDeviceIntent.ShowHelp -> {
        AppLog.d(TAG, "Show help intent received")
        showModelNumberHelpPopup()
      }

      is AddDeviceIntent.OpenScaleChooser -> {
        AppLog.d(TAG, "Open scale chooser intent received")
        navigateTo(AppRoute.AccountSettings.ChooseDevice)
      }

      is AddDeviceIntent.OpenDeviceSettings -> {
        AppLog.d(TAG, "Open scale settings intent received for scaleId: ${intent.scaleId}")
        navigateTo(AppRoute.AccountSettings.DeviceDetails(intent.scaleId))
      }

      is AddDeviceIntent.OpenSelectedScaleSetup -> {
        AppLog.d(TAG, "Open selected scale setup intent received for SKU: ${intent.sku}")
        checkAndNavigateToScaleSetup(intent.sku)
      }

      is AddDeviceIntent.ResetForm -> _state.value.form.resetForm()

      else -> {}
    }
  }

  init {
    AppLog.d(TAG, "AddDeviceViewModel initialized")
    viewModelScope.launch {
      // Collect saved scales from DeviceService
      deviceService.pairedScales.collect { devices ->
        handleIntent(AddDeviceIntent.SetSavedScales(devices))
      }
    }
  }

  /**
   * Re-pull paired devices from the server when the My Devices screen is shown, so a scale
   * paired on another phone appears without an app restart. (MOB-1201)
   */
  fun refreshSavedScales() {
    AppLog.d(TAG, "Refreshing saved scales on My Devices open")
    deviceService.refreshPairedDevices()
  }

  private fun onSubmitModelNumber() {
    AppLog.d(TAG, "Submitting model number form")
    val modelNumberForm = state.value.form
    val isValid = modelNumberForm.validate()
    AppLog.d(TAG, "Form validation result: $isValid")

    if (isValid) {
      val modelNumber = state.value.form.controls.modelNumber.value
      AppLog.d(TAG, "Model number submitted: $modelNumber")
      checkAndNavigateToScaleSetup(modelNumber, replaceLast = false)
    } else {
      AppLog.w(TAG, "Form validation failed")
    }
  }

  private fun checkAndNavigateToScaleSetup(sku: String, replaceLast: Boolean = true) {
    AppLog.d(TAG, "Checking and navigating to scale setup for SKU: $sku")
    val scaleInfo = DeviceDataHelper.findScaleInfoBySku(sku)
    val setupType = scaleInfo?.setupType
    // Check saved scales using mapped SKU (savedScales contains DeviceModelInfo with mapped SKUs via toScaleInfo())
    val isScaleAlreadyPaired = state.value.savedScales.any { it.sku == scaleInfo?.sku }

    AppLog.d(
      TAG,
      "Scale info found: ${scaleInfo?.productName}, setup type: $setupType, already paired: $isScaleAlreadyPaired",
    )

    if (setupType == DeviceSetupType.AppSync && isScaleAlreadyPaired) {
      AppLog.d(TAG, "Scale is already paired, showing confirmation dialog")
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = PairedScaleExistsAlert.Title,
          message = PairedScaleExistsAlert.Message(scaleInfo.sku),
          confirmText = PairedScaleExistsAlert.Pair,
          cancelText = PairedScaleExistsAlert.Return,
          onConfirm = {
            AppLog.d(TAG, "User confirmed pairing existing scale")
            navigateToSelectedScaleSetup(sku, replaceLast)
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    } else {
      AppLog.d(TAG, "Proceeding to scale setup")
      navigateToSelectedScaleSetup(sku, replaceLast)
    }
  }

  private fun navigateToSelectedScaleSetup(sku: String, replaceLast: Boolean) {
    AppLog.d(TAG, "Navigating to selected scale setup for SKU: $sku")
    val scaleInfo = DeviceDataHelper.findScaleInfoBySku(sku)
    if (scaleInfo != null) {
      AppLog.d(TAG, "Scale info found: ${scaleInfo.productName}, setup type: ${scaleInfo.setupType}")
      // Pass original SKU to routes (not mapped), setup will save original SKU
      val route = when (scaleInfo.setupType) {
        DeviceSetupType.AppSync -> {
          AppLog.d(TAG, "Navigating to AppSync scale setup")
          AppRoute.DeviceSetup.AppsyncScaleSetup(sku)
        }

        DeviceSetupType.Bluetooth -> {
          AppLog.d(TAG, "Navigating to Bluetooth scale setup")
          AppRoute.DeviceSetup.BtScaleSetup(sku, scaleInfo)
        }

        DeviceSetupType.Lcbt -> {
          AppLog.d(TAG, "Navigating to Lcbt scale setup")
          AppRoute.DeviceSetup.LcbtScaleSetup(sku, scaleInfo = scaleInfo)
        }

        DeviceSetupType.BabyScale -> {
          AppLog.d(TAG, "Navigating to Baby Scale setup")
          AppRoute.DeviceSetup.BabyScaleSetup(sku)
        }

        DeviceSetupType.BtWifiR4 -> {
          AppLog.d(TAG, "Navigating to BtWifiR4 scale setup")
          AppRoute.DeviceSetup.BtWifiScaleSetup(sku)
        }

        DeviceSetupType.Wifi,
        DeviceSetupType.EspTouchWifi -> {
          AppLog.d(TAG, "Navigating to WiFi scale setup")
          // Use the DeviceSetupNavigationUtils to determine the correct route with DeviceModelInfo and wifiSetupType
          val route = DeviceSetupNavigationUtils.createWifiSetupRoute(scaleInfo)
          route
        }

        DeviceSetupType.BpmBluetooth, DeviceSetupType.BpmA6Bluetooth -> {
          AppLog.d(TAG, "Navigating to BPM monitor setup")
          AppRoute.DeviceSetup.BpmSetup(sku)
        }
      }
      replaceLastAndNavigate(route, replaceLast)
    } else {
      AppLog.w(TAG, "Scale info not found for SKU: $sku")
    }
  }

  /**
   * Shows the Model number help popup.
   */
  private fun showModelNumberHelpPopup() {
    AppLog.d(TAG, "Showing model number help popup")
    dialogUtility.showModelNumberHelpDialog()
  }

  private fun navigateTo(route: AppRoute) {
    AppLog.d(TAG, "Navigating to route: $route")
    viewModelScope.launch {
      try {
        navigationService.navigateTo(route)
        AppLog.d(TAG, "Successfully navigated to route: $route")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error navigating to route: $route", e)
      }
    }
  }

  private fun replaceLastAndNavigate(route: AppRoute, replaceLast: Boolean = true) {
    AppLog.d(TAG, "Replacing last route and navigating to: $route")
    viewModelScope.launch {
      try {
        if (replaceLast)
          navigationService.replaceLastAndNavigate(route)
        else
          navigationService.navigateTo(route)
        AppLog.d(TAG, "Successfully replaced last route and navigated to: $route")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error replacing last route and navigating to: $route", e)
      }
    }
  }

  companion object {
    private const val TAG = "AddDeviceViewModel"
  }
}
