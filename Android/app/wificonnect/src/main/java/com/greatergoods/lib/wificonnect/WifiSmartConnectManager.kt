package com.greatergoods.lib.wificonnect

import androidx.activity.ComponentActivity
import com.greatergoods.lib.wificonnect.model.WifiConnectRequest
import com.greatergoods.lib.wificonnect.model.WifiConnectResult
import com.greatergoods.lib.wificonnect.utilities.WifiApConnector
import com.greatergoods.lib.wificonnect.utilities.WifiEsptouchConnector
import com.greatergoods.lib.wificonnect.utilities.WifiSmartConfigConnector
import javax.inject.Inject

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
  suspend fun connect(request: WifiConnectRequest, activity: ComponentActivity): WifiConnectResult =
    when (request) {
      is WifiConnectRequest.Esptouch -> WifiConnectResult.Esptouch(esptouchConnector.connect(request.params, activity))
      is WifiConnectRequest.SmartConfig ->
        WifiConnectResult.SmartConfig(
          smartConfigConnector.connect(request.params),
        )

      is WifiConnectRequest.ApMode -> WifiConnectResult.ApMode(apConnector.connect(request.params))
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
}
