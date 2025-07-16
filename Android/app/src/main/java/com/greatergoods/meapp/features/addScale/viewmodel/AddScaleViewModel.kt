package com.greatergoods.meapp.features.addScale.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.addScale.reducer.AddScaleFormControls
import com.greatergoods.meapp.features.addScale.reducer.AddScaleIntent
import com.greatergoods.meapp.features.addScale.reducer.AddScaleReducer
import com.greatergoods.meapp.features.addScale.reducer.AddScaleState
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
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
        navigateToSelectedScaleSetup(intent.sku)
      }

      else -> {}
    }
  }

  init {
    viewModelScope.launch {
      // Collect saved scales from DeviceService
      deviceService.savedScales.collect { devices ->
        handleIntent(AddScaleIntent.SetSavedScales(devices))
      }
    }
  }

  private fun onSubmitModelNumber() {
    val modelNumberForm = state.value.form
    modelNumberForm.validate()
    if (modelNumberForm.isValid) {
      val modelNumber = state.value.form.controls.modelNumber.value
      navigateToSelectedScaleSetup(modelNumber)
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
