package com.greatergoods.lib.wificonnect.utilities

import com.greatergoods.ggesptouchlib.GGEsptouchCallback
import com.greatergoods.ggesptouchlib.GGEsptouchHelper
import com.greatergoods.ggesptouchlib.GGScaleData
import com.greatergoods.ggesptouchlib.GGScanError
import com.greatergoods.ggesptouchlib.tcp.TCPCodeResult
import com.greatergoods.lib.wificonnect.model.EsptouchParams
import com.greatergoods.lib.wificonnect.model.EsptouchResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Handles Esptouch smart connect operations for WiFi devices.
 *
 * @property context Android context for operations.
 */
class WifiEsptouchConnector
    @Inject
    constructor(
        private val context: Context,
    ) {
        /**
         * Runs Esptouch smart connect with the provided parameters.
         *
         * @param params Parameters for the Esptouch connection.
         * @return [EsptouchResult] representing success or failure.
         */
        suspend fun connect(params: EsptouchParams): EsptouchResult =
            suspendCancellableCoroutine { cont ->
                val helper = GGEsptouchHelper.getInstance()
                val data =
                    GGScaleData(
                        params.ssid,
                        params.bssid,
                        params.token,
                        params.password,
                        params.userNumber,
                    )
                val callback = EsptouchCallback(cont)
                val activity =
                    context as? Activity
                        ?: throw IllegalArgumentException("Context must be an Activity")
                helper.beginSmartConnect(activity, data, callback)
                cont.invokeOnCancellation {
                    GGEsptouchHelper.stopTask()
                    Log.i("WifiEsptouchConnector", "Esptouch task cancelled")
                }
            }

        /**
         * Static callback to avoid memory leaks.
         */
        private class EsptouchCallback(
            private val cont: CancellableContinuation<EsptouchResult>,
        ) : GGEsptouchCallback() {
            override fun onSuccess(tcpCodeResult: TCPCodeResult) {
                Log.i(
                    "WifiEsptouchConnector",
                    "Esptouch success: ${'$'}{tcpCodeResult.getDevtype()} - ${'$'}{tcpCodeResult.getDmac()}",
                )
                cont.resume(
                    EsptouchResult.Success(
                        tcpCodeResult.devtype.toString(),
                        tcpCodeResult.dmac,
                    ),
                )
            }

            override fun onFailure(
                error: GGScanError,
                errorMessage: String,
            ) {
                Log.e("WifiEsptouchConnector", "Esptouch failed: ${'$'}errorMessage")
                cont.resume(EsptouchResult.Failure(errorMessage))
            }
        }

        /**
         * Stops any ongoing Esptouch operation.
         */
        fun stop() {
            GGEsptouchHelper.stopTask()
            Log.i("WifiEsptouchConnector", "Esptouch task stopped")
        }
    }
