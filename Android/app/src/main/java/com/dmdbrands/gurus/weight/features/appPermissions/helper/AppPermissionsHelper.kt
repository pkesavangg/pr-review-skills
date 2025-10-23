package com.dmdbrands.gurus.weight.features.appPermissions.helper

import com.dmdbrands.gurus.weight.domain.enum.CustomPermissionType
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.appPermissions.strings.AppPermissionsScreenStrings
import com.dmdbrands.gurus.weight.features.common.components.PermissionItemStatus
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import android.os.Build

/**
 * UI model for displaying a permission item in the list.
 */
data class PermissionItem(
  val key: String,
  val status: PermissionItemStatus,
  val isDisabled: Boolean = false,
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
    CustomPermissionType.WIFI_SWITCH_LOCATION.value to PermissionMeta(
      group = AppPermissionsScreenStrings.LocationHeader,
      enabledDescription = AppPermissionsScreenStrings.EnabledWifiDescription,
      disabledDescription = AppPermissionsScreenStrings.DisabledWifiDescription,
    ),
  )

  /**
   * Maps the permission state map to a grouped list of UI models for the permissions screen.
   */
  fun mapToPermissionGroups(permissionMap: GGPermissionStatusMap): List<PermissionGroup> {
    // Build PermissionItems for all known types, excluding WIFI_SWITCH_LOCATION which is only for WiFi scale setup
    val items = permissionMetaMap.mapNotNull { (type, meta) ->
      // Skip WIFI_SWITCH_LOCATION in general permissions screen - it's only for WiFi scale setup
      if (type == CustomPermissionType.WIFI_SWITCH_LOCATION.value) {
        return@mapNotNull null
      }

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
          CustomPermissionType.WIFI_SWITCH_LOCATION.value,
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
    permissionMap: GGPermissionStatusMap,
    requiredPermissionTypes: List<String>? = null,
    wifiName: String? = null
  ): List<PermissionGroup> {
    val scaleSetupType = SCALES.find { it.sku == sku }!!.setupType
    val requiredPermissionTypes = requiredPermissionTypes ?: getRequiredPermissionTypes(scaleSetupType)

    // Build PermissionItems for only the required types
    val items = getPermissionItems(requiredPermissionTypes, permissionMap, sku, wifiName)

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
   * Gets the required permission groups for the given permission types.
   *
   * @param permissionMap The permission state map
   * @param requiredPermissionTypes The list of permission types to get groups for
   * @return List of permission groups for the specified permission types
   */
  fun getRequiredPermissions(
    permissionMap: GGPermissionStatusMap,
    requiredPermissionTypes: List<String>
  ): List<PermissionGroup> {
    val items = getPermissionItems(requiredPermissionTypes, permissionMap)

    // Group items by their group header
    val groupedItems = items.groupBy { it.group }

    // Define the order of groups based on the permission types
    val groupOrder = listOf(
      AppPermissionsScreenStrings.BluetoothHeader,
      AppPermissionsScreenStrings.LocationHeader,
      AppPermissionsScreenStrings.CameraHeader,
      AppPermissionsScreenStrings.NotificationsHeader,
      AppPermissionsScreenStrings.NetworkHeader,
    )

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
    permissionMap: GGPermissionStatusMap,
    sku: String? = null,
    setupType: ScaleSetupType? = null,
    requiredPermissionTypes: List<String>? = null
  ): Boolean {
    if (sku == null && setupType == null) {
      return false
    }
    val scaleSetupType = setupType ?: SCALES.find { it.sku == sku }!!.setupType
    val requiredPermissionTypes = requiredPermissionTypes ?: getRequiredPermissionTypes(scaleSetupType)

    return requiredPermissionTypes.all { permissionType ->
      // For WIFI_SWITCH_LOCATION, use the actual WIFI_SWITCH permission state
      // But only for WiFi scale types (Wifi, EspTouchWifi)
      val actualPermissionType = if (permissionType == CustomPermissionType.WIFI_SWITCH_LOCATION.value &&
        (scaleSetupType == ScaleSetupType.Wifi || scaleSetupType == ScaleSetupType.EspTouchWifi)) {
        GGPermissionType.WIFI_SWITCH
      } else {
        permissionType
      }
      val permissionState = permissionMap[actualPermissionType] ?: PermissionState.NOT_DETERMINED
      permissionState == PermissionState.ENABLED
    }
  }

  /**
   * Checks if the scan permissions are enabled or not.
   * @param permissions The map of permissions to check.
   */
  fun checkScanPermissions(permissions: GGPermissionStatusMap): Boolean {
    val scanPermissions = getScanPermissions(permissions)
    val isEnabled = scanPermissions.all { it.value == GGPermissionState.ENABLED }
    return isEnabled
  }

  fun getScanPermissions(permissions: GGPermissionStatusMap): GGPermissionStatusMap {
    return permissions.filter {
      it.key == GGPermissionType.BLUETOOTH_SWITCH ||
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
          it.key == GGPermissionType.NEARBY_DEVICE
        } else {
          it.key == GGPermissionType.LOCATION || it.key == GGPermissionType.LOCATION_SWITCH
        }
    } as GGPermissionStatusMap
  }

  fun canRequestNotificationPermission(permissions: GGPermissionStatusMap): Boolean {
    return permissions[GGPermissionType.NOTIFICATION] != GGPermissionState.ENABLED &&
      permissions[GGPermissionType.NOTIFICATION] != GGPermissionState.PERMANENTLY_DENIED
  }

  private fun getPermissionItems(
    requiredPermissionTypes: List<String>,
    permissionMap: GGPermissionStatusMap,
    sku: String? = null,
    wifiName: String? = null
  ): List<PermissionItem> {
    // Build PermissionItems for only the required types
    val items = requiredPermissionTypes.mapNotNull { type ->
      val meta = permissionMetaMap[type] ?: return@mapNotNull null

      // For WIFI_SWITCH_LOCATION, use the actual WIFI_SWITCH permission state
      val actualPermissionType = if (type == CustomPermissionType.WIFI_SWITCH_LOCATION.value) GGPermissionType.WIFI_SWITCH else type
      val value = permissionMap[actualPermissionType] ?: PermissionState.NOT_DETERMINED

      // Check if WIFI_SWITCH_LOCATION should be disabled due to missing location permissions
      val isWifiSwitchLocation = type == CustomPermissionType.WIFI_SWITCH_LOCATION.value
      val hasLocationPermissions = permissionMap[GGPermissionType.LOCATION_SWITCH] == PermissionState.ENABLED &&
        permissionMap[GGPermissionType.LOCATION] == PermissionState.ENABLED

      val status = when {
        isWifiSwitchLocation && !hasLocationPermissions -> PermissionItemStatus.Denied
        value == PermissionState.ENABLED -> PermissionItemStatus.Granted
        value == PermissionState.DISABLED || value == PermissionState.PERMANENTLY_DENIED -> PermissionItemStatus.Denied
        else -> PermissionItemStatus.NotRequested
      }

      // Custom descriptions for SKU 0384
      val (enabledDescription, disabledDescription) =
        // For WIFI_SWITCH_LOCATION, use dynamic WiFi name if available
        if (type == CustomPermissionType.WIFI_SWITCH_LOCATION.value && wifiName != null) {
          val enabledDesc = "${AppPermissionsScreenStrings.EnabledWifiDescription} $wifiName"
          val disabledDesc = AppPermissionsScreenStrings.DisabledWifiDescription
          enabledDesc to disabledDesc
        } else {
          meta.enabledDescription to meta.disabledDescription
        }

      PermissionItem(
        key = type,
        status = status,
        isDisabled = isWifiSwitchLocation && !hasLocationPermissions,
        enabledDescription = enabledDescription,
        disabledDescription = disabledDescription,
        group = meta.group,
      )
    }
    return items
  }

  /**
   * Returns custom permission descriptions for SKU 0384.
   */
  private fun getCustomDescriptionsForSku0384(
    permissionType: String,
    defaultEnabled: String,
    defaultDisabled: String,
    wifiName: String? = null
  ): Pair<String, String> {
    return when (permissionType) {
      GGPermissionType.WIFI_SWITCH -> {
        val enabledDesc = if (wifiName != null && wifiName.isNotEmpty()) {
          "${AppPermissionsScreenStrings.EnabledWifiDescription} + $wifiName"
        } else {
          AppPermissionsScreenStrings.EnabledWifiDescription
        }
        enabledDesc to AppPermissionsScreenStrings.DisabledWifiDescription
      }

      else -> defaultEnabled to defaultDisabled
    }
  }

  /**
   * Gets the required permission sets based on paired scales.
   * This determines which permissions are required for the current setup.
   * Based on the Angular implementation in permissions.service.ts
   *
   * @param pairedScales List of paired scale devices
   * @return Set of permission types that are required
   */
  fun getRequiredPermissionSets(pairedScales: List<Device>): Set<String> {
    val requiredPermissions = mutableSetOf<String>()
    if (pairedScales.isEmpty()) return emptySet()
    pairedScales.forEach { scale ->
      val scaleSetupType = ScaleSetupType.fromString(scale.deviceType)
      when (scaleSetupType) {
        ScaleSetupType.Bluetooth, ScaleSetupType.Lcbt -> {
          requiredPermissions.add(GGPermissionType.BLUETOOTH_SWITCH)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(GGPermissionType.NEARBY_DEVICE)
          } else {
            requiredPermissions.add(GGPermissionType.LOCATION_SWITCH)
            requiredPermissions.add(GGPermissionType.LOCATION)
          }
        }

        ScaleSetupType.AppSync -> {
          requiredPermissions.add(GGPermissionType.CAMERA)
        }

        ScaleSetupType.BtWifiR4 -> {
          requiredPermissions.add(GGPermissionType.BLUETOOTH_SWITCH)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(GGPermissionType.NEARBY_DEVICE)
          } else {
            requiredPermissions.add(GGPermissionType.LOCATION_SWITCH)
            requiredPermissions.add(GGPermissionType.LOCATION)
          }
          requiredPermissions.add(GGPermissionType.NOTIFICATION)
        }

        else -> {
          // Handle unknown setup type - default to basic Bluetooth permissions
          return requiredPermissions
        }
      }
    }

    return requiredPermissions
  }
}
