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
                            val state = networkStateFromCapabilities(capabilities)
                            trySend(state)
                        }

                        override fun onLost(network: Network) {
                            trySend(NetworkState(available = false, unAvailable = true))
                        }
                    }
                // We only require NET_CAPABILITY_INTERNET in the NetworkRequest to avoid losing
                // the network during validation transitions. However, in networkStateFromCapabilities()
                // we check for both INTERNET and VALIDATED to ensure actual connectivity.
                // This approach: (1) keeps tracking the network during transitions (prevents false onLost),
                // (2) but only reports "available" when both capabilities are present (ensures real connectivity).
                val networkRequest =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
            return capabilities?.let { networkStateFromCapabilities(it) }
        }

        /**
         * Converts NetworkCapabilities to NetworkState.
         * Uses NET_CAPABILITY_INTERNET as the signal for availability.
         * 
         * Tradeoff: We don't require NET_CAPABILITY_VALIDATED here because during network
         * transitions (e.g., switching from WiFi to mobile data), validation may be temporarily
         * pending. Requiring VALIDATED would cause false "unavailable" states during these
         * transitions. While this means we might report "available" for networks that claim
         * internet but aren't validated yet, the NetworkInterceptor and actual HTTP requests
         * will fail gracefully if connectivity isn't real, and we avoid the more problematic
         * false "unavailable" signals that were causing the original bug.
         */
        private fun networkStateFromCapabilities(capabilities: NetworkCapabilities): NetworkState {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            return NetworkState(available = hasInternet, unAvailable = !hasInternet)
        }
    }
