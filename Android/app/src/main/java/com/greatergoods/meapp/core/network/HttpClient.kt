package com.greatergoods.meapp.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HttpClient  (val baseUrl: String, okHttpClient: OkHttpClient ) {
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl) // can be updated dynamically via interceptor
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    fun <T> createService(serviceClass: Class<T>): T =
        retrofit.create(serviceClass)
}
