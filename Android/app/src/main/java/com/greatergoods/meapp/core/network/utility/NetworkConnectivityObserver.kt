package com.greatergoods.meapp.core.network.utility

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.greatergoods.meapp.core.network.`interface`.IConnectivityObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject


data class NetworkState (
    val available : Boolean,
    val unAvailable : Boolean
)
@RequiresApi(Build.VERSION_CODES.M)
class NetworkConnectivityObserver @Inject constructor(
    private val context: Context
): IConnectivityObserver {

    override fun observe(): Flow<NetworkState> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(NetworkState(available = true, unAvailable = false))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                val capabilities = connectivityManager?.getNetworkCapabilities(network)
                val state = capabilities?.let { hasCapabilitiesChanged(it) }
                    ?: NetworkState(available = false, unAvailable = true) // fallback when capabilities are null
                trySend(state)
            }


            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(NetworkState(available = false, unAvailable = true))
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager?.registerNetworkCallback(networkRequest, callback)

        // Set initial value
        val currentState = getCurrentNetworkState(connectivityManager)
        trySend(currentState)

        awaitClose {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    override fun getCurrentNetworkState(): NetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return getCurrentNetworkState(connectivityManager)
    }

    private fun getCurrentNetworkState(connectivityManager: ConnectivityManager?): NetworkState {
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        return hasCapabilitiesChanged(capabilities!!)
    }

    private fun hasCapabilitiesChanged(capabilities: NetworkCapabilities): NetworkState{
        val isConnected  = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return NetworkState(available = isConnected, unAvailable = isConnected)
    }

}