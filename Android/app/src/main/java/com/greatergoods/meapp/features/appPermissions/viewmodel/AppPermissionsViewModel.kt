package com.greatergoods.meapp.features.appPermissions.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
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
) : BaseIntentViewModel<AppPermissionsState, AppPermissionsIntent>(
  reducer = AppPermissionReducer(),
) {
  override fun provideInitialState(): AppPermissionsState {
    return AppPermissionsState()
  }

  init {
    subscribePermissions()
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
   * Requests a specific permission using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    viewModelScope.launch {
      try {
        permissionService.requestPermission(permissionType)
      } catch (e: Exception) {
        AppLog.e("AppPermissionsViewModel", "Error requesting permission ${permissionType}", e.toString())
      }
    }
  }
}

