package com.dmdbrands.gurus.weight.features.addScale.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.util.ScaleSetupNavigationUtils
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleFormControls
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleIntent
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleReducer
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleState
import com.dmdbrands.gurus.weight.features.addScale.strings.PairedScaleExistsAlert
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddScaleViewModel
@Inject
constructor(
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService,
) : BaseIntentViewModel<AddScaleState, AddScaleIntent>(AddScaleReducer()) {
  override fun provideInitialState(): AddScaleState =
    AddScaleState(
      form = FormGroup(AddScaleFormControls.create()),
    )

  override fun handleIntent(intent: AddScaleIntent) {
    AppLog.d(TAG, "Handling add scale intent: ${intent::class.simpleName}")
    super.handleIntent(intent)
    when (intent) {
      is AddScaleIntent.Submit -> {
        AppLog.d(TAG, "Submit intent received")
        onSubmitModelNumber()
      }

      is AddScaleIntent.ShowHelp -> {
        AppLog.d(TAG, "Show help intent received")
        showModelNumberHelpPopup()
      }

      is AddScaleIntent.OpenScaleChooser -> {
        AppLog.d(TAG, "Open scale chooser intent received")
        navigateTo(AppRoute.AccountSettings.ChooseScale)
      }

      is AddScaleIntent.OpenScaleSettings -> {
        AppLog.d(TAG, "Open scale settings intent received for scaleId: ${intent.scaleId}")
        navigateTo(AppRoute.AccountSettings.ScaleDetails(intent.scaleId))
      }

      is AddScaleIntent.OpenSelectedScaleSetup -> {
        AppLog.d(TAG, "Open selected scale setup intent received for SKU: ${intent.sku}")
        checkAndNavigateToScaleSetup(intent.sku)
      }

      is AddScaleIntent.ResetForm -> _state.value.form.resetForm()

      else -> {}
    }
  }

  init {
    AppLog.d(TAG, "AddScaleViewModel initialized")
    viewModelScope.launch {
      // Collect saved scales from DeviceService
      deviceService.pairedScales.collect { devices ->
        handleIntent(AddScaleIntent.SetSavedScales(devices))
      }
    }
  }

  private fun onSubmitModelNumber() {
    AppLog.d(TAG, "Submitting model number form")
    val modelNumberForm = state.value.form
    val isValid = modelNumberForm.validate()
    AppLog.d(TAG, "Form validation result: $isValid")

    if (isValid) {
      val modelNumber = state.value.form.controls.modelNumber.value
      AppLog.d(TAG, "Model number submitted: $modelNumber")
      checkAndNavigateToScaleSetup(modelNumber)
    } else {
      AppLog.w(TAG, "Form validation failed")
    }
  }

  private fun checkAndNavigateToScaleSetup(sku: String) {
    AppLog.d(TAG, "Checking and navigating to scale setup for SKU: $sku")
    val scaleInfo = SCALES.find { it.sku == sku }
    val setupType = scaleInfo?.setupType
    val isScaleAlreadyPaired = state.value.savedScales.any { it.sku == sku }

    AppLog.d(
      TAG,
      "Scale info found: ${scaleInfo?.productName}, setup type: $setupType, already paired: $isScaleAlreadyPaired",
    )

    if (setupType == ScaleSetupType.AppSync && isScaleAlreadyPaired) {
      AppLog.d(TAG, "Scale is already paired, showing confirmation dialog")
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = PairedScaleExistsAlert.Title,
          message = PairedScaleExistsAlert.Message(scaleInfo.sku),
          confirmText = PairedScaleExistsAlert.Pair,
          cancelText = PairedScaleExistsAlert.Cancel,
          onConfirm = {
            AppLog.d(TAG, "User confirmed pairing existing scale")
            navigateToSelectedScaleSetup(sku)
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    } else {
      AppLog.d(TAG, "Proceeding to scale setup")
      navigateToSelectedScaleSetup(sku)
    }
  }

  private fun navigateToSelectedScaleSetup(sku: String) {
    AppLog.d(TAG, "Navigating to selected scale setup for SKU: $sku")
    val scaleInfo = SCALES.find { it.sku == sku }
    if (scaleInfo != null) {
      AppLog.d(TAG, "Scale info found: ${scaleInfo.productName}, setup type: ${scaleInfo.setupType}")
      when (scaleInfo.setupType) {
        ScaleSetupType.AppSync -> {
          AppLog.d(TAG, "Navigating to AppSync scale setup")
          replaceLastAndNavigate(AppRoute.ScaleSetup.AppsyncScaleSetup(sku))
        }

        ScaleSetupType.Bluetooth -> {
          AppLog.d(TAG, "Navigating to Bluetooth scale setup")
          replaceLastAndNavigate(AppRoute.ScaleSetup.BtScaleSetup(sku, scaleInfo))
        }

        ScaleSetupType.Lcbt -> {
          AppLog.d(TAG, "Navigating to Lcbt scale setup")
          replaceLastAndNavigate(AppRoute.ScaleSetup.LcbtScaleSetup(sku, scaleInfo = scaleInfo))
        }

        ScaleSetupType.BtWifiR4 -> {
          AppLog.d(TAG, "Navigating to BtWifiR4 scale setup")
          replaceLastAndNavigate(AppRoute.ScaleSetup.BtWifiScaleSetup(sku))
        }

        ScaleSetupType.Wifi,
        ScaleSetupType.EspTouchWifi -> {
          AppLog.d(TAG, "Navigating to WiFi scale setup")
          // Use the ScaleSetupNavigationUtils to determine the correct route with ScaleInfo and wifiSetupType
          val route = ScaleSetupNavigationUtils.createWifiSetupRoute(scaleInfo)
          replaceLastAndNavigate(route)
        }

      }
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

  private fun replaceLastAndNavigate(route: AppRoute) {
    AppLog.d(TAG, "Replacing last route and navigating to: $route")
    viewModelScope.launch {
      try {
        navigationService.replaceLastAndNavigate(route)
        AppLog.d(TAG, "Successfully replaced last route and navigated to: $route")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error replacing last route and navigating to: $route", e)
      }
    }
  }

  companion object {
    private const val TAG = "AddScaleViewModel"
  }
}
