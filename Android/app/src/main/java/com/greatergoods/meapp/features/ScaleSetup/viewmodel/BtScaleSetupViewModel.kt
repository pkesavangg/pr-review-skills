package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.meapp.features.ScaleSetup.enums.BtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.enums.ScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.SetupInitData
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtScaleSetupState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

/**
 * ViewModel for the BtScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = BtScaleSetupViewModel.Factory::class,
)
class BtScaleSetupViewModel
@AssistedInject
constructor(
  @Assisted private val scaleInit: SetupInitData<BtScaleSetupStep>,
  dependencies: BLESetupDependencies
) : BLESetupViewmodel<BtScaleSetupStep, BtScaleSetupState>(
  GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A3.value,
  scaleInit,
  reducer = BtScaleSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleInit: SetupInitData<BtScaleSetupStep>): BtScaleSetupViewModel
  }

  private val TAG = "BtScaleSetupViewModel"

  override fun provideInitialState(): BtScaleSetupState = BtScaleSetupState()
  override fun observePermissions() {
    TODO("Not yet implemented")
  }

  override fun onStepChange(step: ScaleSetupStep) {
    TODO("Not yet implemented")
  }

  override fun onNext() {
    TODO("Not yet implemented")
  }

  override fun onBack() {
    TODO("Not yet implemented")
  }

  override fun onSkip() {
    TODO("Not yet implemented")
  }
}
