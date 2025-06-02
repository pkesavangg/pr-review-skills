package com.greatergoods.meapp.core.di

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.core.network.interceptors.AuthTokenInterceptor
import com.greatergoods.meapp.core.network.interceptors.BaseUrlInterceptor
import com.greatergoods.meapp.core.network.interceptors.NetworkInterceptor
import com.greatergoods.meapp.core.network.interceptors.ResponseInterceptor
import com.greatergoods.meapp.core.network.utility.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule  {

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(AppConfig.BASE_URL, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(@ApplicationContext context: Context): HttpLoggingInterceptor {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
        ) != 0
        return HttpLoggingInterceptor().apply {
            level = if (isDebuggable) {
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

    @Provides
    @Singleton
    fun provideAuthTokenInterceptor(): AuthTokenInterceptor {
        return AuthTokenInterceptor()
    }

    @Provides
    @Singleton
    fun provideResponseInterceptor(): ResponseInterceptor {
        return ResponseInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        responseInterceptor: ResponseInterceptor,
        networkInterceptor: NetworkInterceptor
    ): OkHttpClient {
        val okHttpClient = OkHttpClient.Builder()
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            okHttpClient.addInterceptor(loggingInterceptor)
        }
        okHttpClient.addInterceptor(networkInterceptor)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authTokenInterceptor)
            .addInterceptor(responseInterceptor)
        return okHttpClient.build()
    }
}
