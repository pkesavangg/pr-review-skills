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
  val status: PermissionItemStatus,
  val enabledDescription: String?,
  val disabledDescription: String?,
  val group: String
)

/**
 * UI model for a group of permission items.
 */
data class PermissionGroup(
  val header: String,
  val items: List<PermissionItem>
)

/**
 * Helper for mapping permission state map to grouped UI models for the permissions screen.
 */
object AppPermissionsHelper {
  /**
   * Metadata for each permission type.
   */
  private data class PermissionMeta(
    val group: String,
    val enabledDescription: String?,
    val disabledDescription: String?
  )

  private val permissionMetaMap = mapOf(
    GGPermissionType.BLUETOOTH_SWITCH to PermissionMeta(
      group = AppPermissionsScreenStrings.BluetoothHeader,
      enabledDescription = AppPermissionsScreenStrings.BluetoothEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.BluetoothDisabledDescription,
    ),
    GGPermissionType.NEARBY_DEVICE to PermissionMeta(
      group = AppPermissionsScreenStrings.BluetoothHeader,
      enabledDescription = AppPermissionsScreenStrings.NearbyDevicesEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.NearbyDevicesDisabledDescription,
    ),
    GGPermissionType.LOCATION_SWITCH to PermissionMeta(
      group = AppPermissionsScreenStrings.LocationHeader,
      enabledDescription = AppPermissionsScreenStrings.LocationSwitchEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.LocationSwitchDisabledDescription,
    ),
    GGPermissionType.LOCATION to PermissionMeta(
      group = AppPermissionsScreenStrings.LocationHeader,
      enabledDescription = AppPermissionsScreenStrings.LocationEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.LocationDisabledDescription,
    ),
    GGPermissionType.NOTIFICATION to PermissionMeta(
      group = AppPermissionsScreenStrings.NotificationsHeader,
      enabledDescription = AppPermissionsScreenStrings.NotificationsEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.NotificationsDisabledDescription,
    ),
    GGPermissionType.CAMERA to PermissionMeta(
      group = AppPermissionsScreenStrings.CameraHeader,
      enabledDescription = AppPermissionsScreenStrings.CameraEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.CameraDisabledDescription,
    ),
  )

  /**
   * Maps the permission state map to a grouped list of UI models for the permissions screen.
   */
  fun mapToPermissionGroups(permissionMap: GGPermissionStatusMap): List<PermissionGroup> {
    // Build PermissionItems for all known types
    val items = permissionMetaMap.mapNotNull { (type, meta) ->
      val value = permissionMap[type] ?: PermissionState.NOT_DETERMINED
      val status = when (value) {
        PermissionState.ENABLED -> PermissionItemStatus.Granted
        PermissionState.DISABLED, PermissionState.PERMANENTLY_DENIED -> PermissionItemStatus.Denied
        PermissionState.NOT_REQUESTED, PermissionState.NOT_DETERMINED -> PermissionItemStatus.NotRequested
        else -> PermissionItemStatus.NotRequested
      }
      PermissionItem(
        key = type,
        status = status,
        enabledDescription = meta.enabledDescription,
        disabledDescription = meta.disabledDescription,
        group = meta.group,
      )
    }
    // Explicit group order: Bluetooth, Location, Camera, Notifications
    val groupOrder = listOf(
      AppPermissionsScreenStrings.BluetoothHeader,
      AppPermissionsScreenStrings.LocationHeader,
      AppPermissionsScreenStrings.CameraHeader,
      AppPermissionsScreenStrings.NotificationsHeader
    )
    val groupedItems = items.groupBy { it.group }
    return groupOrder.mapNotNull { groupHeader ->
      groupedItems[groupHeader]?.let { groupItems ->
        PermissionGroup(
          header = groupHeader,
          items = groupItems,
        )
      }
    }
  }
}
