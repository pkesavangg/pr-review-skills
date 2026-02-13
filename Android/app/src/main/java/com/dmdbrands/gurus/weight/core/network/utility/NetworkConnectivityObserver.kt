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
         *
         * Falls back to checking all registered networks when activeNetwork returns null,
         * which can happen transiently during background-to-foreground transitions before
         * the OS fully re-establishes the network binding.
         */
        private fun getCurrentNetworkState(connectivityManager: ConnectivityManager?): NetworkState? {
            if (connectivityManager == null) return null

            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            if (capabilities != null) {
                return networkStateFromCapabilities(capabilities)
            }

            // activeNetwork/capabilities can be null transiently when resuming from Doze mode.
            // Check allNetworks as a fallback before declaring unavailable.
            val hasAnyConnectedNetwork = connectivityManager.allNetworks.any { net ->
                connectivityManager.getNetworkCapabilities(net)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
            return if (hasAnyConnectedNetwork) {
                NetworkState(available = true, unAvailable = false)
            } else {
                null
            }
        }

        /**
         * Converts NetworkCapabilities to NetworkState.
         * Treats network as "available" only when both INTERNET and VALIDATED capabilities
         * are present, so we only report real connectivity (validated internet), which reduces
         * flapping on Samsung mobile data and avoids acting on transient states.
         * unAvailable is the inverse of available.
         */
        private fun networkStateFromCapabilities(capabilities: NetworkCapabilities): NetworkState {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val available = hasInternet && hasValidated
            return NetworkState(available = available, unAvailable = !available)
        }
    }
