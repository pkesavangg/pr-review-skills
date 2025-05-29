package com.greatergoods.meapp.core.network.interceptors

import android.os.Build
import androidx.annotation.RequiresApi
import com.greatergoods.meapp.core.network.utility.NetworkConnectivityObserver
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.M)
class NetworkInterceptor @Inject constructor(
    private val networkConnectivityObserver: NetworkConnectivityObserver
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Get the current network state
        val networkState = networkConnectivityObserver.getCurrentNetworkState()

        if (networkState.available) {
            //TODO: need to show toast
            // Return a pre-made error response instead of proceeding
            val mediaType = "text/plain".toMediaType()
            val responseBody = "No internet connection".toResponseBody(mediaType)

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(599) // Custom code for no connectivity
                .message("No Internet Connection")
                .body(responseBody)
                .build()
        }

        return chain.proceed(chain.request())
    }
}

