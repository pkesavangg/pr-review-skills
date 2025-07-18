package com.greatergoods.meapp.features.scaleDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsReducer
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleDetailsStrings
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
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleDetailsState, ScaleDetailsIntent>(
  reducer = ScaleDetailsReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleDetailsViewModel
  }

  override fun provideInitialState(): ScaleDetailsState = ScaleDetailsState()

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

      else -> {}
    }
  }

  init {
    setScaleDetails()
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
            viewModelScope.launch {
              dialogQueueService.showLoader(message = ScaleDetailsStrings.DeleteLoaderMessage)
              deviceService.deleteScale(scaleId)
              ggDeviceService.disconnectDevice(state.value.scale!!.toGGBTDevice())
              dialogQueueService.dismissLoader()
              dialogQueueService.dismissCurrent()
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

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }
}
