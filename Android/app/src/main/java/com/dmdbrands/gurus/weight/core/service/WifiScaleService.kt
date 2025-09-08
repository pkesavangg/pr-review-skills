package com.dmdbrands.gurus.weight.core.service

import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.greatergoods.lib.wificonnect.WifiSmartConnectManager
import com.greatergoods.lib.wificonnect.model.ApConnectParams
import com.greatergoods.lib.wificonnect.model.EsptouchParams
import com.greatergoods.lib.wificonnect.model.EsptouchResult
import com.greatergoods.lib.wificonnect.model.SmartConfigParams
import com.greatergoods.lib.wificonnect.model.SmartConfigResult
import com.greatergoods.lib.wificonnect.model.WifiConnectRequest
import com.greatergoods.lib.wificonnect.model.WifiConnectResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Represents the status of WiFi connection.
 */
enum class WifiConnectionStatus {
  UNKNOWN,
  ENABLED,
  DISABLED,
  CONNECTED
}

/**
 * Represents the type of WiFi setup operation.
 */
enum class WifiSetupType {
  FIRST,
  JOIN,
  CHANGE,
  ESP_TOUCH_WIFI
}

/**
 * Data class representing WiFi information.
 */
data class WifiInfo(
  val ssid: String? = null,
  val bssid: String? = null
)

/**
 * Data class representing WiFi setup information.
 * Equivalent to TypeScript WifiSetupInfo interface.
 */
data class WifiSetupInfo(
  var ssid: String? = null,
  var bssid: String? = null,
  val password: String? = null,
  val userNumber: Int? = null,
  val token: String? = null
)

/**
 * Data class representing WiFi status information.
 */
data class WifiStatus(
  val status: WifiConnectionStatus,
  val locationStatus: String,
  val ssid: String,
  val bssid: String
)

/**
 * Android service for WiFi scale operations, implementing functionality from WifiSmartConfigWrapper.
 * Provides SmartConfig, Esptouch, and AP Mode WiFi setup capabilities.
 */
@Singleton
class WifiScaleService @Inject constructor(
  private val wifiSmartConnectManager: WifiSmartConnectManager,
  private val deviceService: IDeviceService,
  @ApplicationContext private val context: Context
) {
  private lateinit var currentActivity: ComponentActivity
  private var TAG = "WifiScaleService"

  fun initialise(activity: ComponentActivity) {
    currentActivity = activity
    wifiSmartConnectManager.initialise(activity)
  }

  /**
   * Unified connect function that chooses the correct connection method based on WifiSetupType.
   * Calls the appropriate WifiConnectRequest and handles the result.
   */
  fun connect(
    setupInfo: WifiSetupInfo,
    setupType: WifiSetupType,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    Log.d("connectwifi4", setupType.toString())
    CoroutineScope(Dispatchers.IO).launch {
      try {
        // Validate setup data
        validateSetupDataOrThrow(setupInfo, setupType)

        val request = when (setupType) {
          WifiSetupType.ESP_TOUCH_WIFI -> {
            val params = EsptouchParams(
              ssid = setupInfo.ssid ?: "",
              bssid = setupInfo.bssid ?: "",
              password = setupInfo.password ?: "",
              userNumber = setupInfo.userNumber ?: 0,
              token = setupInfo.token ?: "",
            )
            WifiConnectRequest.Esptouch(params)
          }

          WifiSetupType.FIRST, WifiSetupType.JOIN -> {
            val params = SmartConfigParams(
              ssid = setupInfo.ssid ?: "",
              password = setupInfo.password ?: "",
              userNumber = setupInfo.userNumber ?: 0,
              tokenHexString = setupInfo.token ?: "",
            )
            WifiConnectRequest.SmartConfig(params)
          }

          WifiSetupType.CHANGE -> {
            // CHANGE is AP Mode
            val params = ApConnectParams(
              ssid = setupInfo.ssid ?: "",
              password = setupInfo.password ?: "",
              userNumber = setupInfo.userNumber ?: 1,
              tokenHexString = setupInfo.token ?: "",
            )
            WifiConnectRequest.ApMode(params)
          }
        }

        val result = wifiSmartConnectManager.connect(request, currentActivity)

        when (result) {
          is WifiConnectResult.Esptouch -> {
            when (result.result) {
              is EsptouchResult.Success -> {
                AppLog.d(
                  TAG,
                  "Esptouch connection successful",
                )
                onSuccess()
              }

              is EsptouchResult.Failure -> {
                val errorMsg =
                  "Esptouch connection failed: ${(result.result as EsptouchResult.Failure).errorMessage}"
                AppLog.e(TAG, "Esptouch connection failed: ${(result.result as EsptouchResult.Failure).errorMessage}")
                onError(errorMsg)
              }
            }
          }

          is WifiConnectResult.SmartConfig -> {
            when (result.result) {
              is SmartConfigResult.Success -> {
                AppLog.d(
                  TAG,
                  "SmartConfig connection successful",
                )
                onSuccess()
              }

              is SmartConfigResult.Failure -> {
                val errorMsg =
                  "SmartConfig connection failed: ${(result.result as SmartConfigResult.Failure).errorMessage}"
                AppLog.e(
                  TAG,
                  "SmartConfig connection failed: ${(result.result as SmartConfigResult.Failure).errorMessage}",
                )
                onError(errorMsg)
              }
            }
          }

          is WifiConnectResult.ApMode -> {
            // You may want to handle ApMode result here if needed
            AppLog.d(TAG, "AP Mode connection result: $result")
            onSuccess() // Or handle error if needed
          }

          else -> {
          }
        }
      } catch (e: Exception) {
        Log.d("connectwifiexception", e.toString())
        val errorMsg = "Connect failed: ${e.message}"
        AppLog.e(TAG, "Connect failed: ${e.message}", e)
        onError(errorMsg)
      }
    }
  }

  /**
   * Get scale token from the API - equivalent to WifiSmartConfigWrapper.getScaleToken()
   *
   * @param r Optional parameter for the API request (defaults to null)
   * @return The scale token as a string
   * @throws Exception if the API call fails
   */
  suspend fun getScaleToken(r: String? = null): String? {
    AppLog.d(TAG, "Getting scale token from API")
    return try {
      val token = deviceService.getScaleToken(false)
      AppLog.d(TAG, "Scale token retrieved successfully")
      token
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting scale token", e)
      null
    }
  }

  /**
   * Stops all ongoing WiFi operations - equivalent to WifiSmartConfigWrapper.stop()
   */
  fun stop() {
    wifiSmartConnectManager.stopAll()
  }

  /**
   * Get WiFi scan results, checking for location permission first.
   * Equivalent to device.service.ts getScanResults()
   *
   * @return List of WiFi scan results, or empty list if permission is not granted
   */
  suspend fun getScanResults(): List<android.net.wifi.ScanResult> {
    // Check location permission
    val hasLocationPermission =
      context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasLocationPermission) {
      AppLog.w(
        TAG,
        "Location permission not granted. Returning empty scan results.",
      )
      return emptyList()
    }
    return try {
      val wifiManager =
        currentActivity.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
      wifiManager.scanResults ?: emptyList()
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting WiFi scan results", e)
      emptyList()
    }
  }

  /**
   * Get the SSID of the currently connected WiFi network.
   * Equivalent to device.service.ts getConnectedSsid()
   *
   * @return The SSID of the connected network, or empty string if not connected
   */
  fun getConnectedSsid(): String {
    AppLog.d(TAG, "Getting connected SSID")
    return try {
      val ssid = wifiSmartConnectManager.getConnectedSsid(currentActivity)
      AppLog.d(TAG, "Connected SSID: $ssid")
      ssid
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting connected SSID", e)
      ""
    }
  }

  /**
   * Get the BSSID (MAC address) of the currently connected WiFi network.
   * Equivalent to device.service.ts getConnectedBssid()
   *
   * @return The BSSID of the connected network, or empty string if not connected
   */
  fun getConnectedBssid(): String {
    AppLog.d(TAG, "Getting connected BSSID")
    return try {
      val bssid = wifiSmartConnectManager.getConnectedBssid(currentActivity)
      AppLog.d(TAG, "Connected BSSID: $bssid")
      bssid
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting connected BSSID", e)
      ""
    }
  }

  /**
   * Opens the WiFi settings screen.
   * This allows users to manually change their WiFi network.
   */
  fun openWifiSettings() {
    try {
      val wifiSettingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
      wifiSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(wifiSettingsIntent)
      AppLog.d(TAG, "Opened WiFi settings")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to open WiFi settings", e)
    }
  }

  /**
   * Gets the current WiFi connection information including SSID, BSSID, and connection status.
   * Equivalent to WifiSmartConfigWrapper.getConnectedWifiInfo()
   *
   * @param hasLocationPermission Whether location permissions are granted
   * @return WifiStatus object containing connection information
   */
  suspend fun getConnectedWifiInfo(hasLocationPermission: Boolean = false): WifiStatus {
    var ssid = ""
    var bssid = ""
    var status: WifiConnectionStatus = WifiConnectionStatus.UNKNOWN
    var locationStatus: String = GGPermissionState.NOT_DETERMINED

    try {
      val isWifiEnabled = wifiSmartConnectManager.isWifiEnabled(currentActivity)
      if (isWifiEnabled) {
        status = WifiConnectionStatus.ENABLED

        if (hasLocationPermission) {
          ssid = wifiSmartConnectManager.getConnectedSsid(currentActivity)
          if (ssid == "<unknown ssid>") {
            ssid = ""
          }
          bssid = wifiSmartConnectManager.getConnectedBssid(currentActivity)

          if (ssid.isNotEmpty()) {
            status = WifiConnectionStatus.CONNECTED
          }
        }
      } else {
        status = WifiConnectionStatus.DISABLED
      }

      AppLog.d(
        TAG,
        "getConnectedWifiInfo - Status: $status, SSID: $ssid, BSSID: $bssid, hasLocationPermission: $hasLocationPermission",
      )

      return WifiStatus(status, locationStatus, ssid, bssid)
    } catch (e: Exception) {
      AppLog.e(
        TAG,
        "getConnectedWifiInfo - Error getting wifi connection status",
        e,
      )
      return WifiStatus(status, locationStatus, ssid, bssid)
    }
  }

  /**
   * Validates the setup data based on the setup type.
   * Equivalent to TypeScript validateSetupData() method.
   *
   * @param setupInfo The WiFi setup information to validate
   * @param setupType The type of setup operation
   * @return true if the setup data is valid for the given setup type, false otherwise
   * @throws IllegalArgumentException if no setup type is provided
   */
  private fun validateSetupData(setupInfo: WifiSetupInfo, setupType: WifiSetupType): Boolean {
    AppLog.d(TAG, "Validating setup data for type: $setupType")

    val isValid = when (setupType) {
      WifiSetupType.FIRST -> {
        setupInfo.ssid != null &&
          setupInfo.userNumber != null &&
          setupInfo.token != null
      }

      WifiSetupType.JOIN -> {
        setupInfo.userNumber != null &&
          setupInfo.token != null
      }

      WifiSetupType.CHANGE -> {
        setupInfo.ssid != null
      }

      WifiSetupType.ESP_TOUCH_WIFI -> {
        setupInfo.ssid != null &&
          setupInfo.bssid != null &&
          setupInfo.userNumber != null &&
          setupInfo.token != null
      }
    }

    AppLog.d(TAG, "Setup data validation result: $isValid for type: $setupType")
    return isValid
  }

  /**
   * Validates setup data and throws an exception if invalid.
   * Helper method for public setup methods.
   *
   * @param setupInfo The WiFi setup information to validate
   * @param setupType The type of setup operation
   * @throws IllegalArgumentException if the setup data is invalid
   */
  private fun validateSetupDataOrThrow(setupInfo: WifiSetupInfo, setupType: WifiSetupType) {
    if (!validateSetupData(setupInfo, setupType)) {
      val errorMessage =
        "Data for ${setupType.name.lowercase()} setup type '$setupType' is invalid: $setupInfo"
      AppLog.e(TAG, errorMessage)
      throw IllegalArgumentException(errorMessage)
    }
  }
}
