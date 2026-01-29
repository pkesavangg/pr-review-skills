package com.dmdbrands.gurus.weight.core.network.utility

import androidx.core.content.getSystemService
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
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
                            // Use the capabilities parameter directly instead of re-fetching.
                            // Re-fetching can return null during network transitions and incorrectly
                            // emit unavailable state when network is actually available.
                            val state = hasCapabilitiesChanged(capabilities)
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
         * Uses NET_CAPABILITY_INTERNET as the primary signal; NET_CAPABILITY_VALIDATED
         * may be temporarily false during network transitions, so we use internet capability
         * only to prevent false "unavailable" states.
         */
        private fun hasCapabilitiesChanged(capabilities: NetworkCapabilities): NetworkState {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            return NetworkState(available = hasInternet, unAvailable = !hasInternet)
        }
    }
