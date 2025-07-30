package com.dmdbrands.gurus.weight.features.home.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.dmdbrands.gurus.weight.features.home.reducer.HomeReducer
import com.dmdbrands.gurus.weight.features.home.reducer.HomeState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleApiEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val deviceService: IDeviceService,
  private val ggPermissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  private val entryService: com.dmdbrands.gurus.weight.domain.services.IEntryService,
  private val appSyncService: IAppSyncService,
  private val accountService: IAccountService,
  dialogQueueService: IDialogQueueService
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
    if(result.weight != null && !result.canceled){
      handleNewEntry(result)
    }
    else if (result.manual) {
      navigateToManualEntry()
    } else {
    }
  }

  private fun handleNewEntry(result: AppSyncResult){
    viewModelScope.launch {
      try {
        val currentAccount = accountService.activeAccountFlow.first()
        val accountId = currentAccount?.id ?: return@launch
        val storedEntry = result.toScaleApiEntry(accountId)
        // Create ScaleEntry directly from AppSyncResult with calculated BMI
        val scaleEntry = result.toScaleEntry(accountId, currentAccount.weightUnit.value.toString(), currentAccount.height)
        appSyncService.setAppSyncDataForEditing(scaleEntry)
        appSyncService.setAppSyncData(storedEntry)
        dialogUtility.showEntrySyncPopup(
          entry = scaleEntry,
          apiEntry = scaleEntry.toScaleApiEntry(),
          onEdit = {
            viewModelScope.launch {
              appSyncService.handleEditAppSyncData(scaleEntry)
            }
          },
          onSave = {
            viewModelScope.launch {
              val saveEntry = result.toScaleEntry(accountId, currentAccount.weightUnit.value.toString(), currentAccount.height, true)
              appSyncService.handleSaveAppSyncData(saveEntry)
            }
          }
        )
      } catch (e: Exception) {
        AppLog.e("HomeViewModel", "Error handling new entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast(message = "Failed to process AppSync data: ${e.message}")
        )
      }
    }
  }

  private fun navigateToManualEntry() {
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home)
    }
  }
}
