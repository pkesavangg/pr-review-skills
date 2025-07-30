package com.dmdbrands.gurus.weight.core.network.utility

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager

/**
 * Observes network connectivity changes using BroadcastReceiver for API 21–22 (Lollipop).
 * Implements [IConnectivityObserver].
 *
 * @property context The application context.
 */
class LegacyNetworkConnectivityObserver(
    private val context: Context,
) : IConnectivityObserver {
    /**
     * Observes network state changes as a Flow.
     * @return Flow emitting the current [NetworkState].
     */
    override fun observe(): Flow<NetworkState> =
        callbackFlow {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        val activeNetwork = connectivityManager.activeNetworkInfo
                        val isConnected = activeNetwork?.isConnected == true
                        trySend(NetworkState(available = isConnected, unAvailable = !isConnected))
                    }
                }

            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            context.registerReceiver(receiver, filter)

            // Emit initial state
            val activeNetwork = connectivityManager.activeNetworkInfo
            val isConnected = activeNetwork?.isConnected == true
            trySend(NetworkState(available = isConnected, unAvailable = !isConnected))

            awaitClose { context.unregisterReceiver(receiver) }
        }

    /**
     * Gets the current network state synchronously.
     * @return The current [NetworkState].
     */
    override fun getCurrentNetworkState(): NetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        val isConnected = activeNetwork?.isConnected == true
        return NetworkState(available = isConnected, unAvailable = !isConnected)
    }
}
