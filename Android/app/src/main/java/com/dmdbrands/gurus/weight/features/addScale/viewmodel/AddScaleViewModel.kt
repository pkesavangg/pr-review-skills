package com.dmdbrands.gurus.weight.features.addScale.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
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
      form = FormGroup(AddScaleFormControls.Companion.create()),
    )

  override fun handleIntent(intent: AddScaleIntent) {
    super.handleIntent(intent)
    when (intent) {
      is AddScaleIntent.Submit -> {
        onSubmitModelNumber()
      }

      is AddScaleIntent.ShowHelp -> {
        showModelNumberHelpPopup()
      }

      is AddScaleIntent.OpenScaleChooser -> {
        navigateTo(AppRoute.AccountSettings.ChooseScale)
      }

      is AddScaleIntent.OpenScaleSettings -> {
        navigateTo(AppRoute.AccountSettings.ScaleDetails(intent.scaleId))
      }

      is AddScaleIntent.OpenSelectedScaleSetup -> {
        checkAndNavigateToScaleSetup(intent.sku)
      }

      else -> {}
    }
  }

  init {
    viewModelScope.launch {
      // Collect saved scales from DeviceService
      deviceService.pairedScales.collect { devices ->
        handleIntent(AddScaleIntent.SetSavedScales(devices))
      }
    }
  }

  private fun onSubmitModelNumber() {
    val modelNumberForm = state.value.form
    modelNumberForm.validate()
    if (modelNumberForm.isValid) {
      val modelNumber = state.value.form.controls.modelNumber.value
      checkAndNavigateToScaleSetup(modelNumber)
    }
  }

  private fun checkAndNavigateToScaleSetup(sku: String) {
    val scaleInfo = SCALES.find { it.sku == sku }
    val setupType = scaleInfo?.setupType
    val isScaleAlreadyPaired = state.value.savedScales.any { it.sku == sku }
    if (setupType == ScaleSetupType.AppSync && isScaleAlreadyPaired) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = PairedScaleExistsAlert.Title,
          message = PairedScaleExistsAlert.Message(scaleInfo.productName),
          confirmText = PairedScaleExistsAlert.Pair,
          cancelText = PairedScaleExistsAlert.Cancel,
          onConfirm = {
            navigateToSelectedScaleSetup(sku)
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    } else {
      navigateToSelectedScaleSetup(sku)
    }
  }

  private fun navigateToSelectedScaleSetup(sku: String) {
    val setupType = SCALES.find { it.sku == sku }?.setupType
    if (setupType != null) {
      when (setupType) {
        ScaleSetupType.AppSync -> navigateTo(AppRoute.ScaleSetup.AppsyncScaleSetup(sku))
        ScaleSetupType.Bluetooth -> navigateTo(AppRoute.ScaleSetup.BtScaleSetup(sku))
        ScaleSetupType.Lcbt -> navigateTo(AppRoute.ScaleSetup.LcbtScaleSetup(sku))
        ScaleSetupType.BtWifiR4 -> navigateTo(AppRoute.ScaleSetup.BtWifiScaleSetup(sku))
        ScaleSetupType.Wifi,
        ScaleSetupType.EspTouchWifi,
          -> navigateTo(AppRoute.ScaleSetup.WifiScaleSetup(sku))
      }
    }
  }

  /**
   * Shows the Model number help popup.
   */
  private fun showModelNumberHelpPopup() {
    dialogUtility.showModelNumberHelpDialog()
  }

  private fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }
}
