package com.greatergoods.meapp.features.appPermissions.helper

import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.model.permission.PermissionState
import com.greatergoods.meapp.features.appPermissions.strings.AppPermissionsScreenStrings
import com.greatergoods.meapp.features.common.components.PermissionItemStatus
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.SCALES
import android.os.Build

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
    GGPermissionType.WIFI_SWITCH to PermissionMeta(
      group = AppPermissionsScreenStrings.NetworkHeader,
      enabledDescription = AppPermissionsScreenStrings.NetworkEnabledDescription,
      disabledDescription = AppPermissionsScreenStrings.NetworkDisabledDescription,
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
      AppPermissionsScreenStrings.NotificationsHeader,
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

  /**
   * Gets the required permission types for the given scale setup type.
   *
   * @param scaleSetupType The scale setup type to get permission types for
   * @return List of required permission types
   */
  fun getRequiredPermissionTypes(scaleSetupType: ScaleSetupType): List<String> {
    return when (scaleSetupType) {
      ScaleSetupType.BtWifiR4 -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          listOf(
            GGPermissionType.BLUETOOTH_SWITCH,
            GGPermissionType.NEARBY_DEVICE,
            GGPermissionType.WIFI_SWITCH,
          )
        } else {
          listOf(
            GGPermissionType.BLUETOOTH_SWITCH,
            GGPermissionType.LOCATION_SWITCH,
            GGPermissionType.LOCATION,
            GGPermissionType.WIFI_SWITCH,
          )
        }
      }

      ScaleSetupType.AppSync -> {
        listOf(GGPermissionType.CAMERA)
      }

      ScaleSetupType.Lcbt,
      ScaleSetupType.Bluetooth -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          listOf(
            GGPermissionType.BLUETOOTH_SWITCH,
            GGPermissionType.NEARBY_DEVICE,
          )
        } else {
          listOf(
            GGPermissionType.BLUETOOTH_SWITCH,
            GGPermissionType.LOCATION_SWITCH,
            GGPermissionType.LOCATION,
          )
        }
      }

      ScaleSetupType.Wifi,
      ScaleSetupType.EspTouchWifi -> {
        listOf(
          GGPermissionType.LOCATION_SWITCH,
          GGPermissionType.LOCATION,
          GGPermissionType.WIFI_SWITCH,
        )
      }
    }
  }

  /**
   * Returns the appropriate permission groups for the given scale SKU.
   * The returned list contains only the permissions required for the specific scale setup type.
   *
   * @param sku The scale SKU to get permissions for
   * @param permissionMap The permission state map
   * @return List of permission groups containing only the required permissions for the setup type
   */
  fun getRequiredPermissionsForSetupType(
    sku: String,
    permissionMap: GGPermissionStatusMap
  ): List<PermissionGroup> {
    val scaleSetupType = SCALES.find { it.sku == sku }!!.setupType
    val requiredPermissionTypes = getRequiredPermissionTypes(scaleSetupType)

    // Build PermissionItems for only the required types
    val items = requiredPermissionTypes.mapNotNull { type ->
      val meta = permissionMetaMap[type] ?: return@mapNotNull null
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

    // Group items by their group header
    val groupedItems = items.groupBy { it.group }

    // Define the order of groups based on the setup type
    val groupOrder = when (scaleSetupType) {
      ScaleSetupType.BtWifiR4 -> listOf(
        AppPermissionsScreenStrings.BluetoothHeader,
        AppPermissionsScreenStrings.LocationHeader,
        AppPermissionsScreenStrings.NetworkHeader,
      )

      ScaleSetupType.AppSync -> listOf(
        AppPermissionsScreenStrings.CameraHeader,
      )

      ScaleSetupType.Lcbt,
      ScaleSetupType.Bluetooth -> listOf(
        AppPermissionsScreenStrings.BluetoothHeader,
        AppPermissionsScreenStrings.LocationHeader,
      )

      ScaleSetupType.Wifi,
      ScaleSetupType.EspTouchWifi -> listOf(
        AppPermissionsScreenStrings.LocationHeader,
        AppPermissionsScreenStrings.NetworkHeader,
      )
    }

    // Return groups in the defined order, only including groups that have items
    return groupOrder.mapNotNull { groupHeader ->
      groupedItems[groupHeader]?.let { groupItems ->
        PermissionGroup(
          header = groupHeader,
          items = groupItems,
        )
      }
    }
  }

  /**
   * Checks if all required permissions for the given scale SKU are enabled.
   *
   * @param sku The scale SKU to check permissions for
   * @param permissionMap The permission state map
   * @return true if all required permissions are enabled, false otherwise
   */
  fun areRequiredPermissionsEnabled(
    sku: String,
    permissionMap: GGPermissionStatusMap
  ): Boolean {
    val scaleSetupType = SCALES.find { it.sku == sku }!!.setupType
    val requiredPermissionTypes = getRequiredPermissionTypes(scaleSetupType)

    return requiredPermissionTypes.all { permissionType ->
      val permissionState = permissionMap[permissionType] ?: PermissionState.NOT_DETERMINED
      permissionState == PermissionState.ENABLED
    }
  }
}
