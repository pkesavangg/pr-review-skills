package com.greatergoods.meapp.features.integration.model

/**
 * Health Connect specific state management.
 * @property isCheckingAvailability Whether checking Health Connect availability.
 * @property isRequestingPermissions Whether requesting Health Connect permissions.
 * @property isSyncingData Whether syncing data with Health Connect.
 * @property permissionStatus Current permission status.
 * @property syncProgress Progress of data synchronization (0-100).
 * @property syncError Error message if sync failed.
 */
data class HealthConnectState(
  val isCheckingAvailability: Boolean = false,
  val isRequestingPermissions: Boolean = false,
  val isSyncingData: Boolean = false,
  val permissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NONE,
  val syncProgress: Int = 0,
  val syncError: String? = null,
) {
  companion object {
    val Idle = HealthConnectState()
  }
}
