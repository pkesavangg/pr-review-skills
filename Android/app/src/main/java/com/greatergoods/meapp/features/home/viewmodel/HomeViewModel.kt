package com.greatergoods.meapp.features.home.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.home.reducer.HomeIntent
import com.greatergoods.meapp.features.home.reducer.HomeReducer
import com.greatergoods.meapp.features.home.reducer.HomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val deviceService: IDeviceService,
  private val ggPermissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility
) : BaseIntentViewModel<HomeState, HomeIntent>(HomeReducer()) {
  override fun provideInitialState(): HomeState = HomeState()

  init {
    observeAppSyncStatus()
    observePermissions()
  }

  override fun handleIntent(intent: HomeIntent) {
    super.handleIntent(intent)
    when (intent) {
      is HomeIntent.CheckAndRequestPermission -> {
        checkAndRequestCameraPermission(intent.onResult)
      }

      is HomeIntent.HandleAppSyncResult -> handleAppSyncResult(intent.result)

      // Handle toggle appsync logic if needed

      else -> {}
    }
  }

  private fun observeAppSyncStatus() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { savedScales ->
        val hasAppSyncScales = savedScales.any { savedScale ->
          val scaleInfo = SCALES.find { it.sku == savedScale.sku }
          scaleInfo?.setupType == ScaleSetupType.AppSync
        } && savedScales.isNotEmpty()

        handleIntent(HomeIntent.SetShowAppsync(hasAppSyncScales))

      }
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      ggPermissionService.permissionCallBackFlow.collect {
        val isAppSyncPermissionsEnabled =
          AppPermissionsHelper.areRequiredPermissionsEnabled(
            it, setupType = ScaleSetupType.AppSync,
          )
        handleIntent(HomeIntent.isAppSyncPermissionsEnabled(isAppSyncPermissionsEnabled))
      }
    }
  }

  private fun checkAndRequestCameraPermission(onResult: (Boolean) -> Unit) {
    if (state.value.isAppSyncPermissionsEnabled) {
      onResult(true)
      return
    }
    viewModelScope.launch {

      dialogUtility.permissionAlert(
        GGPermissionType.CAMERA,
        onRequest = {
          viewModelScope.launch {
            ggPermissionService.requestPermission(GGPermissionType.CAMERA) {
              val permissionStatus = it as GGPermissionStatusMap
              val isEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                permissionStatus, setupType = ScaleSetupType.AppSync,
              )
              onResult(isEnabled)
            }
          }
        },
        onDismiss = { onResult(false) },
      )
    }
  }

  private fun handleAppSyncResult(result: AppSyncResult) {
    Log.d("HomeViewModel 1", "handleAppSyncResult: $result")

    if (result.manual) {
      navigateToManualEntry()
    } else {
      Log.d("HomeViewModel", "handleAppSyncResult: $result")
    }
  }

  private fun navigateToManualEntry() {
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home)
    }
  }
}
