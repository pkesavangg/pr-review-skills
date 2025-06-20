package com.greatergoods.meapp.core.network.interceptors

import androidx.annotation.RequiresApi
import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import android.os.Build

/**
 * OkHttp interceptor that checks network connectivity before proceeding with a request.
 * If the network is unavailable, returns a custom error response instead of proceeding.
 * Requires API 23+.
 *
 * @property networkConnectivityObserver Observes the current network state.
 */
@RequiresApi(Build.VERSION_CODES.M)
class NetworkInterceptor
    @Inject
    constructor(
        private val networkConnectivityObserver: IConnectivityObserver,
    ) : Interceptor {
        /**
         * Intercepts each HTTP request to check for network availability.
         *
         * @param chain The OkHttp interceptor chain.
         * @return The HTTP response, or a custom error response if no connectivity.
         */
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // Get the current network state
            val networkState = networkConnectivityObserver.getCurrentNetworkState()

            if (networkState.unAvailable) {
                // TODO: need to show toast
                // Return a pre-made error response instead of proceeding
                val mediaType = "text/plain".toMediaType()
                val responseBody = "No internet connection".toResponseBody(mediaType)

                return Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(NO_INTERNET_CONNECTION) // Custom code for no connectivity
                    .message("No Internet Connection")
                    .body(responseBody)
                    .build()
            }

            return chain.proceed(chain.request())
        }
    }
