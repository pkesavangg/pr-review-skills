package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import javax.inject.Inject

class BaseUrlInterceptor @Inject constructor() :
    Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var originalRequest = chain.request()
        val dynamicBaseUrl = originalRequest.header(AppConfig.BASE_URL_HEADER)
        val originalUrl = originalRequest.url

        if (dynamicBaseUrl != null) {
            val newUrl = dynamicBaseUrl.toHttpUrlOrNull()
            if (newUrl != null) {
                val newFullUrl = newUrl.newBuilder()
                    .encodedPath(originalUrl.encodedPath)
                    .build()
                originalRequest = originalRequest.newBuilder()
                    .url(newFullUrl)
                    .removeHeader(AppConfig.BASE_URL_HEADER)
                    .build()
            }
        }
        return chain.proceed(originalRequest)
    }
}