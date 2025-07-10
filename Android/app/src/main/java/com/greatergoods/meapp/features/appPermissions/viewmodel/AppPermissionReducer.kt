package com.greatergoods.meapp.features.appPermissions.viewmodel

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * State class for the App Permissions screen.
 */
data class AppPermissionsState(
  val permissionMap: GGPermissionStatusMap = mutableMapOf(),
  val isLoading: Boolean = false,
  val error: String? = null
) : IReducer.State

/**
 * Intent class for the App Permissions screen.
 */
sealed class AppPermissionsIntent : IReducer.Intent {
  data class SetPermissions(val permissionMap: GGPermissionStatusMap) : AppPermissionsIntent()
  data class RequestPermission(val permissionType: String) : AppPermissionsIntent()
}

class AppPermissionReducer : IReducer<AppPermissionsState, AppPermissionsIntent> {
  override fun reduce(state: AppPermissionsState, intent: AppPermissionsIntent): AppPermissionsState {
    return when (intent) {
      is AppPermissionsIntent.SetPermissions -> state.copy(permissionMap = intent.permissionMap)
      else -> state
    }
  }
}
