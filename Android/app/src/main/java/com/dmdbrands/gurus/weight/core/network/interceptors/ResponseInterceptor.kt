package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * OkHttp interceptor that handles specific HTTP response codes for custom logic.
 * Intended for handling unauthorized, forbidden, bad request, and server error responses.
 */
class ResponseInterceptor
@Inject
constructor(private val appNavigationService: IAppNavigationService) : Interceptor {
    /**
     * Intercepts each HTTP response and handles specific status codes.
     *
     * @param chain The OkHttp interceptor chain.
     * @return The HTTP response, possibly after custom handling.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        return when (response.code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                // Unauthorised will handle the token authenticator itself
                response
            }

            HttpURLConnection.HTTP_FORBIDDEN -> {
                response
            }

            HttpURLConnection.HTTP_BAD_REQUEST -> {
                response
            }

            HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                response
            }

            else -> {
                response
            }
        }
    }
}
