package com.greatergoods.meapp.features.appPermissions.helper

import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.model.permission.PermissionState
import com.greatergoods.meapp.features.appPermissions.strings.AppPermissionsScreenStrings
import com.greatergoods.meapp.features.common.components.PermissionItemStatus

/**
 * UI model for displaying a permission item in the list.
 */
data class PermissionItem(
  val key: String,
  val checked: Boolean,
  val status: PermissionItemStatus,
  val enabled: Boolean,
  val required: Boolean,
  val title: String,
  val description: String?
)

/**
 * Metadata for a permission type (title, description, required).
 */
data class PermissionMeta(
  val title: String,
  val description: String?,
  val required: Boolean
)

/**
 * Helper for mapping permission state map to UI models for the permissions screen.
 */
object AppPermissionsHelper {
  /**
   * Maps the permission state map to a list of UI models for the permissions screen.
   */
  fun mapToPermissionItems(permissionMap: GGPermissionStatusMap): List<PermissionItem> {
    // Canonical list of permissions and their metadata, in display order
    val permissionMetas = listOf(
      GGPermissionType.NOTIFICATION to PermissionMeta(
        AppPermissionsScreenStrings.Notification,
        AppPermissionsScreenStrings.NotificationsDescription,
        false,
      ),
      GGPermissionType.BLUETOOTH_SWITCH to PermissionMeta(
        AppPermissionsScreenStrings.Bluetooth,
        AppPermissionsScreenStrings.BluetoothDescription,
        true,
      ),
      GGPermissionType.NEARBY_DEVICE to PermissionMeta(
        "Nearby Device",
        "Detect nearby compatible devices (Android 12+)",
        true,
      ),
      GGPermissionType.LOCATION_SWITCH to PermissionMeta(
        "Location Switch",
        "Location services must be enabled for Bluetooth scanning",
        true,
      ),
      GGPermissionType.LOCATION to PermissionMeta(
        AppPermissionsScreenStrings.Location,
        AppPermissionsScreenStrings.LocationDescription,
        true,
      ),
      GGPermissionType.CAMERA to PermissionMeta(
        AppPermissionsScreenStrings.Camera,
        AppPermissionsScreenStrings.CameraDescription,
        false,
      ),
    )

    return permissionMetas.map { (type, meta) ->
      val value = permissionMap[type] ?: PermissionState.NOT_DETERMINED
      PermissionItem(
        key = type,
        checked = value == PermissionState.ENABLED,
        status = when (value) {
          PermissionState.ENABLED -> PermissionItemStatus.Granted
          PermissionState.DISABLED, PermissionState.PERMANENTLY_DENIED -> PermissionItemStatus.Denied
          PermissionState.NOT_REQUESTED, PermissionState.NOT_DETERMINED -> PermissionItemStatus.NotRequested
          else -> PermissionItemStatus.NotRequested
        },
        enabled = true, // Could be refined per permission
        required = meta.required,
        title = meta.title,
        description = meta.description,
      )
    }
  }
}
