package com.dmdbrands.gurus.weight.features.appPermissions.viewmodel

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import androidx.compose.runtime.Stable

/**
 * State class for the App Permissions screen.
 */
@Stable
data class AppPermissionsState(
  val permissionMap: GGPermissionStatusMap = mutableMapOf(),
  val requiredPermissions: Set<String> = emptySet(),
  val isLoading: Boolean = false,
  val error: String? = null
) : IReducer.State

/**
 * Intent class for the App Permissions screen.
 */
sealed class AppPermissionsIntent : IReducer.Intent {
  data class SetPermissions(val permissionMap: GGPermissionStatusMap) : AppPermissionsIntent()
  data class SetRequiredPermissions(val requiredPermissions: Set<String>) : AppPermissionsIntent()
  data class RequestPermission(val permissionType: String) : AppPermissionsIntent()
}

class AppPermissionReducer : IReducer<AppPermissionsState, AppPermissionsIntent> {
  override fun reduce(state: AppPermissionsState, intent: AppPermissionsIntent): AppPermissionsState {
    return when (intent) {
      is AppPermissionsIntent.SetPermissions -> state.copy(permissionMap = intent.permissionMap)
      is AppPermissionsIntent.SetRequiredPermissions -> state.copy(requiredPermissions = intent.requiredPermissions)
      else -> state
    }
  }
}
