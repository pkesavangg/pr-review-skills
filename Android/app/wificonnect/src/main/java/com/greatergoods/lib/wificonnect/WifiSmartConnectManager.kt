package com.greatergoods.lib.wificonnect

import androidx.activity.ComponentActivity
import com.greatergoods.lib.wificonnect.model.WifiConnectRequest
import com.greatergoods.lib.wificonnect.model.WifiConnectResult
import com.greatergoods.lib.wificonnect.utilities.WifiApConnector
import com.greatergoods.lib.wificonnect.utilities.WifiEsptouchConnector
import com.greatergoods.lib.wificonnect.utilities.WifiSmartConfigConnector
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Manager for WiFi smart connect operations, exposing a unified connect API and stop methods.
 *
 * @property esptouchConnector Connector for Esptouch smart connect.
 * @property apConnector Connector for AP mode smart connect.
 * @property smartConfigConnector Connector for SmartConfig.
 */
class WifiSmartConnectManager
@Inject
constructor(
  private val esptouchConnector: WifiEsptouchConnector,
  private val apConnector: WifiApConnector,
  private val smartConfigConnector: WifiSmartConfigConnector,
) {
  private lateinit var currentActivity: ComponentActivity
  fun initialise(activity: ComponentActivity) {
    currentActivity = activity
  }

  /**
   * Runs a WiFi smart connect operation based on the request type.
   * @param request The WiFi connect request (Esptouch, SmartConfig, or AP mode).
   * @param activity Activity context required for Esptouch operations.
   * @return The result of the operation, wrapped in [WifiConnectResult].
   */
  suspend fun connect(request: WifiConnectRequest, activity: ComponentActivity): WifiConnectResult? {
    Log.d("connectwifi", request.toString())
    try {

      return when (request) {
        is WifiConnectRequest.Esptouch -> WifiConnectResult.Esptouch(
          esptouchConnector.connect(
            request.params,
            activity,
          ),
        )

        is WifiConnectRequest.SmartConfig ->
          WifiConnectResult.SmartConfig(
            smartConfigConnector.connect(request.params),
          )

        is WifiConnectRequest.ApMode -> withContext(kotlinx.coroutines.Dispatchers.Main) {
          WifiConnectResult.ApMode(apConnector.connect(request.params))
        }
      }
    } catch (e: Exception) {
      Log.d("connectwifimanager", e.toString())
      return null
    }
  }

  /**
   * Stops any ongoing Esptouch operation.
   */
  fun stopEsptouch() = esptouchConnector.stop()

  /**
   * Stops any ongoing SmartConfig operation.
   */
  fun stopSmartConfig() = smartConfigConnector.stop()

  /**
   * Stops any ongoing AP mode operation.
   */
  fun stopApMode() = apConnector.stop()

  /**
   * Stops all ongoing operations (Esptouch, SmartConfig, AP mode).
   */
  fun stopAll() {
    esptouchConnector.stop()
    smartConfigConnector.stop()
    apConnector.stop()
  }

  /**
   * Check if WiFi is enabled on the device.
   * Equivalent to device.service.ts isWifiEnabled()
   *
   * @param context The application context.
   * @return true if WiFi is enabled, false otherwise
   */
  fun isWifiEnabled(context: Context): Boolean {
    return try {
      val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
      wifiManager.isWifiEnabled
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Get the SSID of the currently connected WiFi network.
   * Equivalent to device.service.ts getConnectedSsid()
   *
   * @param context The application context.
   * @return The SSID of the connected network, or empty string if not connected
   */
  fun getConnectedSsid(context: Context): String {
    return try {
      val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
      val connectionInfo = wifiManager.connectionInfo
      connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
    } catch (e: Exception) {
      ""
    }
  }

  /**
   * Get the BSSID (MAC address) of the currently connected WiFi network.
   * Equivalent to device.service.ts getConnectedBssid()
   *
   * @param context The application context.
   * @return The BSSID of the connected network, or empty string if not connected
   */
  fun getConnectedBssid(context: Context): String {
    return try {
      val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
      val connectionInfo = wifiManager.connectionInfo
      connectionInfo?.bssid ?: ""
    } catch (e: Exception) {
      ""
    }
  }
}
