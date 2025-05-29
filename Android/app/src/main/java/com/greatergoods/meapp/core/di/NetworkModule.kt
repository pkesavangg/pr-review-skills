package com.greatergoods.meapp.core.di

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.core.network.interceptors.BaseUrlInterceptor
import com.greatergoods.meapp.core.network.interceptors.NetworkInterceptor
import com.greatergoods.meapp.core.network.utility.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule @Inject constructor() {

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(AppConfig.BASE_URL, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        interceptor: Interceptor,
        applicationInfo: ApplicationInfo,
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
    ): OkHttpClient {
        val okHttpClient = OkHttpClient.Builder()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            okHttpClient.addInterceptor(loggingInterceptor)
        }
        okHttpClient.addInterceptor(interceptor)
            .addInterceptor(baseUrlInterceptor)

        return okHttpClient.build()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(applicationInfo: ApplicationInfo): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideBaseUrlInterceptor(): BaseUrlInterceptor {
        return BaseUrlInterceptor()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Provides
    @Singleton
    fun provideNetworkInterceptor(
        networkConnectivityObserver: NetworkConnectivityObserver
    ): NetworkInterceptor {
        return NetworkInterceptor(networkConnectivityObserver)
    }
}