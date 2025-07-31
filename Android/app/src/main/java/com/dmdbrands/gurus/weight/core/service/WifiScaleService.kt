package com.dmdbrands.gurus.weight.core.service

import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.greatergoods.lib.wificonnect.WifiSmartConnectManager
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

  fun initialise(activity: ComponentActivity) {
    currentActivity = activity
    wifiSmartConnectManager.initialise(activity)
  }

  /**
   * SmartConnect functionality - equivalent to WifiSmartConfigWrapper.smartConnect()
   *
   * @param ssid WiFi network SSID
   * @param password WiFi network password
   * @param userNumber User number for the scale
   * @param tokenHexString Token in hex string format
   * @param onSuccess Callback for successful connection
   * @param onError Callback for connection errors
   */
  fun smartConnect(
    ssid: String,
    password: String,
    userNumber: Int,
    tokenHexString: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val params = SmartConfigParams(
          ssid = ssid,
          password = password,
          userNumber = userNumber,
          tokenHexString = tokenHexString,
        )

        val result = wifiSmartConnectManager.connect(
          WifiConnectRequest.SmartConfig(params),
          currentActivity,
        )

        when (result) {
          is WifiConnectResult.SmartConfig -> {
            when (result.result) {
              is SmartConfigResult.Success -> {
                onSuccess()
              }

              is SmartConfigResult.Failure -> {
                onError("result.result.errorMessage")
              }
            }
          }

          else -> {
            onError("Unexpected result type: ${result::class.simpleName}")
          }
        }
      } catch (e: Exception) {
        onError("SmartConnect failed: ${e.message}")
      }
    }
  }

  /**
   * Esptouch functionality - equivalent to WifiSmartConfigWrapper.espSmartConnect()
   *
   * @param ssid WiFi network SSID
   * @param bssid WiFi network BSSID (MAC address)
   * @param password WiFi network password
   * @param userNumber User number for the scale
   * @param tokenHexString Token in hex string format
   * @param onSuccess Callback for successful connection
   * @param onError Callback for connection errors
   */
  fun esptouch(
    ssid: String,
    bssid: String,
    password: String,
    userNumber: Int,
    tokenHexString: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        AppLog.d(
          "WifiScaleService",
          "Starting Esptouch connection with SSID: $ssid, BSSID: $bssid",
        )

        val params = EsptouchParams(
          ssid = ssid,
          bssid = bssid,
          password = password,
          userNumber = userNumber,
          token = tokenHexString,
        )

        val result = wifiSmartConnectManager.connect(
          WifiConnectRequest.Esptouch(params),
          currentActivity,
        )

        when (result) {
          is WifiConnectResult.Esptouch -> {
            when (result.result) {
              is EsptouchResult.Success -> {
                AppLog.d("WifiScaleService", "Esptouch connection successful")
                onSuccess()
              }

              is EsptouchResult.Failure -> {
                AppLog.e("WifiScaleService", "Esptouch connection failed: $")
                onError("something went wrong")
              }
            }
          }

          else -> {
            val errorMsg = "Unexpected result type: ${result::class.simpleName}"
            AppLog.e("WifiScaleService", errorMsg)
            onError(errorMsg)
          }
        }
      } catch (e: Exception) {
        val errorMsg = "Esptouch failed: ${e.message}"
        AppLog.e("WifiScaleService", errorMsg, e.toString())
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
  suspend fun getScaleToken(r: String? = null): String {
    AppLog.d("WifiScaleService", "getScaleToken - Getting scale token from API")
    return try {
      val token = deviceService.getScaleToken()
      AppLog.d("WifiScaleService", "getScaleToken - Scale token retrieved successfully")
      token
    } catch (e: Exception) {
      AppLog.e("WifiScaleService", "getScaleToken - Error getting scale token", e.toString())
      throw e
    }
  }

  /**
   * Stops all ongoing WiFi operations - equivalent to WifiSmartConfigWrapper.stop()
   */
  fun stop() {
    wifiSmartConnectManager.stopAll()
  }

  /**
   * Stops only SmartConfig operations
   */
  fun stopSmartConfig() {
    wifiSmartConnectManager.stopSmartConfig()
  }

  /**
   * Stops only Esptouch operations
   */
  fun stopEsptouch() {
    wifiSmartConnectManager.stopEsptouch()
  }

  /**
   * Stops only AP Mode operations
   */
  fun stopApMode() {
    wifiSmartConnectManager.stopApMode()
  }
}
