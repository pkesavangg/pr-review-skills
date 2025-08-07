package com.greatergoods.lib.wificonnect.utilities

import com.greatergoods.lib.wificonnect.helper.hexToByteArray
import com.greatergoods.lib.wificonnect.model.ApConnectParams
import com.greatergoods.lib.wificonnect.model.ApConnectResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import ogemray.android.smartconnection.apconfig.ConfigByAP
import ogemray.android.smartconnection.apconfig.MyHandler
import javax.inject.Inject
import kotlin.coroutines.resume
import android.content.Context
import android.util.Log

/**
 * Handles AP mode smart connect operations for WiFi devices.
 *
 * @property context Android context for operations.
 */
class WifiApConnector
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  /**
   * Runs AP mode smart connect with the provided parameters.
   *
   * @param params Parameters for the AP connection.
   * @return [ApConnectResult] representing success or failure.
   */
  suspend fun connect(params: ApConnectParams): ApConnectResult =
    suspendCancellableCoroutine { cont ->
      val configByAP = ConfigByAP(context)
      val handler = ApHandler(cont)
      configByAP.asynSmartConfigByAP(
        params.ssid,
        params.password,
        params.userNumber.toByte(),
        params.tokenHexString.hexToByteArray(),
        handler,
      )
      // No explicit cancellation support in original API
    }

  /**
   * Stops any ongoing AP mode operation. (No-op if not supported by API)
   */
  fun stop() {
    // The original ConfigByAP API does not provide a stop/cancel method.
    Log.i("WifiApConnector", "AP mode stop called (no-op)")
  }

  /**
   * Static handler to avoid memory leaks.
   */
  private class ApHandler(
    private val cont: CancellableContinuation<ApConnectResult>,
  ) : MyHandler() {
    override fun onSuccess(buffer: ByteArray) {
      Log.i("WifiApConnector", "AP connect success")
      cont.resume(ApConnectResult.Success(buffer))
    }

    override fun onFailure(content: Any?) {
      Log.e("WifiApConnector", "AP connect failure: ${'$'}content")
      cont.resume(ApConnectResult.Failure(content?.toString() ?: "Unknown error"))
    }
  }
}
