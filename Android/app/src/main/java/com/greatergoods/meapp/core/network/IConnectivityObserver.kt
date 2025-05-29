package com.greatergoods.meapp.core.network

import com.greatergoods.meapp.core.network.utility.NetworkState
import kotlinx.coroutines.flow.Flow

interface IConnectivityObserver {
        fun observe(): Flow<NetworkState>
        fun getCurrentNetworkState(): NetworkState
}