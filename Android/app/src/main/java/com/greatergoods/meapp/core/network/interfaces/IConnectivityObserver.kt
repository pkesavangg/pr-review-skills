package com.greatergoods.meapp.core.network.interfaces

import com.greatergoods.meapp.core.network.utility.NetworkState
import kotlinx.coroutines.flow.Flow

interface IConnectivityObserver {
        fun observe(): Flow<NetworkState>
        fun getCurrentNetworkState(): NetworkState
}