package com.greatergoods.lib.wificonnect.utilities

import com.greatergoods.lib.wificonnect.helper.hexToByteArray
import com.greatergoods.lib.wificonnect.model.SmartConfigParams
import com.greatergoods.lib.wificonnect.model.SmartConfigResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import ogemray.android.smartconnection.SmartConfig
import javax.inject.Inject
import kotlin.coroutines.resume
import android.content.Context
import android.util.Log

/**
 * Handles SmartConfig (non-AP, non-Esptouch) operations for WiFi devices.
 *
 * @property context Android context for operations.
 */
class WifiSmartConfigConnector
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  private var smartConfig: SmartConfig? = null

  /**
   * Runs SmartConfig with the provided parameters.
   *
   * @param params Parameters for the SmartConfig connection.
   * @return [SmartConfigResult] representing success or failure.
   */
  suspend fun connect(params: SmartConfigParams): SmartConfigResult =
    suspendCancellableCoroutine { cont ->
      val smartConfig = SmartConfig()
      this.smartConfig = smartConfig
      val result =
        smartConfig.StartSmartConfig(
          params.ssid,
          params.password,
          params.userNumber.toByte(),
          params.tokenHexString.hexToByteArray(),
        )
      if (result == 0) {
        Log.i("WifiSmartConfigConnector", "SmartConfig success")
        cont.resume(SmartConfigResult.Success)
      } else {
        smartConfig.StopSmartConfig()
        Log.e("WifiSmartConfigConnector", "SmartConfig failed: $result")
        cont.resume(SmartConfigResult.Failure(result.toString()))
      }
    }

  /**
   * Stops any ongoing SmartConfig operation.
   */
  fun stop() {
    smartConfig?.StopSmartConfig()
    Log.i("WifiSmartConfigConnector", "SmartConfig stopped")
  }
}
