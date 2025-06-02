package com.greatergoods.meapp.core.network.utility

import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Represents the current network state.
 *
 * @property available True if network is available.
 * @property unAvailable True if network is unavailable.
 */
data class NetworkState(
    val available: Boolean,
    val unAvailable: Boolean,
)

/**
 * Observes network connectivity changes and provides the current network state.
 * Implements [IConnectivityObserver].
 *
 * @constructor Injects the application context for system service access.
 */
@RequiresApi(Build.VERSION_CODES.M)
class NetworkConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : IConnectivityObserver {
        /**
         * Observes network state changes as a Flow.
         * @return Flow emitting the current [NetworkState].
         */
        override fun observe(): Flow<NetworkState> =
            callbackFlow {
                val connectivityManager = context.getSystemService<ConnectivityManager>()

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(NetworkState(available = true, unAvailable = false))
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            val updatedCapabilities = connectivityManager?.getNetworkCapabilities(network)
                            val state =
                                updatedCapabilities?.let { hasCapabilitiesChanged(it) }
                                    ?: NetworkState(available = false, unAvailable = true)
                            trySend(state)
                        }

                        override fun onLost(network: Network) {
                            trySend(NetworkState(available = false, unAvailable = true))
                        }
                    }

                val networkRequest =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()

                connectivityManager?.registerNetworkCallback(networkRequest, callback)

                // Emit initial state
                getCurrentNetworkState(connectivityManager)?.let { trySend(it) }

                awaitClose {
                    connectivityManager?.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged()

        /**
         * Gets the current network state synchronously.
         * @return The current [NetworkState].
         */
        override fun getCurrentNetworkState(): NetworkState {
            val connectivityManager = context.getSystemService<ConnectivityManager>()
            return getCurrentNetworkState(connectivityManager)
                ?: NetworkState(available = false, unAvailable = true) // fallback
        }

        /**
         * Helper to get the current network state from a ConnectivityManager.
         */
        private fun getCurrentNetworkState(connectivityManager: ConnectivityManager?): NetworkState? {
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            return capabilities?.let { hasCapabilitiesChanged(it) }
        }

        /**
         * Determines network state based on capabilities.
         */
        private fun hasCapabilitiesChanged(capabilities: NetworkCapabilities): NetworkState {
            val isConnected =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return NetworkState(available = isConnected, unAvailable = !isConnected)
        }
    }
