package com.greatergoods.meapp.features.scaleDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsReducer
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
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
        // TODO: Handle delete scale
      }

      ScaleDetailsIntent.OpenProductGuide -> {
        openProductGuide()
        // TODO: Handle open product guide
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
      deviceService.savedScales.collect { devices ->
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
