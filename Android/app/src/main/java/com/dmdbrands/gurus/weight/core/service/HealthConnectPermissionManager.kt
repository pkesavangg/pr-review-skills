package com.dmdbrands.gurus.weight.core.service

import android.app.Activity
import android.content.Intent
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult

/**
 * Availability / status / permission-authorization slice extracted from [HealthConnectService]
 * (MOB-1500). Thin wrappers over the HealthConnect library plus new-intent forwarding. Shared
 * mutable state (the HealthConnect instance, the current activity, the loaded flag) is read via
 * getters so the service can populate it lazily in [HealthConnectService.load].
 * Behaviour-preserving verbatim move.
 */
class HealthConnectPermissionManager(
  private val getHealthConnect: () -> HealthConnect,
  private val getCurrentActivity: () -> Activity,
  private val getIsLoaded: () -> Boolean,
  private val requireCurrentAccountId: () -> String,
  private val requestingPermissions: HealthConnectOptions,
  private val healthConnectRepository: IHealthConnectRepository,
) {

  private val tag = "HealthConnectService"
  private val healthConnect: HealthConnect get() = getHealthConnect()
  private val currentActivity: Activity get() = getCurrentActivity()
  private val isLoaded: Boolean get() = getIsLoaded()

  /**
   * Handles new intents for privacy policy and permissions rationale.
   * This method forwards the intent to the HealthConnect library for processing.
   * The library will handle the callback appropriately based on the intent action.
   *
   * @param intent The new intent to handle
   */
  fun handleOnNewIntent(intent: Intent?) {
    if (!isLoaded) {
      AppLog.w(tag, "Health Connect service not loaded, ignoring intent")
      return
    }

    try {
      when (intent?.action) {
        HealthConnect.ACTION_SHOW_PERMISSIONS_RATIONALE,
        HealthConnect.ACTION_VIEW_PERMISSION_USAGE -> {
          AppLog.i(tag, "Forwarding Health Connect intent: ${intent.action}")
          healthConnect.handleOnNewIntent(intent)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to handle Health Connect intent", e)
    }
  }

  /**
   * Checks if Health Connect is available on the device.
   */
  suspend fun checkAvailability(): Boolean {
    return try {
      healthConnect.isAvailable()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect availability", e)
      false
    }
  }

  /**
   * Gets the current Health Connect status.
   */
  suspend fun healthConnectStatus(): HealthConnectStatus {
    return try {
      healthConnect.getStatus()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get Health Connect status", e)
      HealthConnectStatus.UNAVAILABLE
    }
  }

  /**
   * Checks the current permission status.
   */
  suspend fun checkPermissionStatus(): HealthConnectPermissionStatus {
    return try {
      healthConnect.getPermissionStatus(requestingPermissions)
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission status", e)
      HealthConnectPermissionStatus.NONE
    }
  }

  /**
   * Requests Health Connect authorization using the callback-based approach.
   * This matches the library's async pattern and handles results properly.
   */
  suspend fun requestAuthorization(callback: (HealthConnectRequestStatus) -> Unit) {
    try {
      // Use the library's callback-based approach directly
      healthConnect.requestAuthorization(requestingPermissions) { result ->
        AppLog.i(tag, "Authorization completed with result: $result")
        callback(result)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to request authorization", e)
      callback(HealthConnectRequestStatus.CANCELLED)
    }
  }

  /**
   * Opens the Health Connect app or settings.
   */
  suspend fun openHealthConnect(isFromSetup: Boolean): Boolean {
    return try {
      val activity = currentActivity
      val accountId = requireCurrentAccountId()
      run {
        val result = healthConnect.launchHealthConnect(activity, false)
        if (isFromSetup) {
          healthConnectRepository.setOpen(accountId, true)
        }
        AppLog.i(tag, "Health Connect launch result: $result")
        result
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to open Health Connect", e)
      false
    }
  }

  /**
   * Revokes all Health Connect permissions.
   */
  suspend fun revokePermission(): Boolean {
    return try {
      val result = healthConnect.revokeAllPermissions()
      when (result) {
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully revoked Health Connect permissions")
          true
        }

        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to revoke permissions", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while revoking permissions", e)
      false
    }
  }

  /**
   * Gets the list of approved permissions from Health Connect.
   * This method returns the permissions that the user has granted to the app.
   */
  suspend fun getApprovedPermissionList(): List<String> {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return emptyList()
      }

      val approvedPermissions = healthConnect.getApprovedPermissionList()
      AppLog.i(tag, "Retrieved ${approvedPermissions.size} approved permissions")
      approvedPermissions.toList()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get approved permission list", e)
      emptyList()
    }
  }
}
