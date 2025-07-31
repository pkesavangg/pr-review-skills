package com.dmdbrands.gurus.weight.core.network.interfaces

import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import kotlinx.coroutines.flow.Flow

/**
 * Interface for observing network connectivity changes and retrieving current network state.
 */
interface IConnectivityObserver {
    /**
     * Observes network state changes as a Flow.
     * @return Flow emitting the current [NetworkState].
     */
    fun observe(): Flow<NetworkState>

    /**
     * Gets the current network state synchronously.
     * @return The current [NetworkState].
     */
    fun getCurrentNetworkState(): NetworkState
}
