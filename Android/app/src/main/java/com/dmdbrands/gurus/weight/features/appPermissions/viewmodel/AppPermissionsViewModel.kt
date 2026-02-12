package com.dmdbrands.gurus.weight.features.appPermissions.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.greatergoods.blewrapper.GGPermissionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the App Permissions screen.
 * Manages permission states and handles permission-related intents using PermissionService.
 */
@HiltViewModel
class AppPermissionsViewModel @Inject constructor(
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService,
) : BaseIntentViewModel<AppPermissionsState, AppPermissionsIntent>(
  reducer = AppPermissionReducer(),
) {
  override fun provideInitialState(): AppPermissionsState {
    return AppPermissionsState()
  }

  init {
    subscribePermissions()
    subscribePairedScales()
  }

  override fun handleIntent(intent: AppPermissionsIntent) {
    super.handleIntent(intent)
    when (intent) {
      is AppPermissionsIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      else -> null
    }
  }

  private fun subscribePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect { permissions ->
        handleIntent(AppPermissionsIntent.SetPermissions(permissions))
      }
    }
  }

  /**
   * Subscribes to paired scales to determine required permissions.
   */
  private fun subscribePairedScales() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { pairedScales ->
        AppLog.d("AppPermissionsViewModel", "Paired scales subscription: $pairedScales")
        val requiredPermissions = AppPermissionsHelper.getRequiredPermissionSets(pairedScales)
        AppLog.d("AppPermissionsViewModel", "Required permissions: $requiredPermissions")
        handleIntent(AppPermissionsIntent.SetRequiredPermissions(requiredPermissions))
      }
    }
  }

  /**
   * Requests a specific permission using the permission service.
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
        AppLog.e("AppPermissionsViewModel", "Error requesting permission ${permissionType}", e)
      }
    }
  }
}

