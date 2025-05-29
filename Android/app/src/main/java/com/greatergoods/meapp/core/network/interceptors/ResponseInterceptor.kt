package com.greatergoods.meapp.core.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection
import javax.inject.Inject

class ResponseInterceptor @Inject constructor(): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        return when (response.code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                //TODO: Handle unauthorized response
                return  response
            }

            HttpURLConnection.HTTP_FORBIDDEN -> {
                //TODO: Handle forbidden response
                return  response
            }

            HttpURLConnection.HTTP_BAD_REQUEST -> {
                //TODO: Handle bad request response
                return  response
            }

            HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                //TODO: Handle internal server error response
                return  response
            }

            else -> {
                response
            }
        }
    }
}