package com.greatergoods.meapp.core.di

import androidx.annotation.RequiresApi
import com.greatergoods.meapp.BuildConfig
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.core.network.interceptors.AuthTokenInterceptor
import com.greatergoods.meapp.core.network.interceptors.BaseUrlInterceptor
import com.greatergoods.meapp.core.network.interceptors.NetworkInterceptor
import com.greatergoods.meapp.core.network.interceptors.ResponseInterceptor
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.network.utility.LegacyNetworkConnectivityObserver
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
import android.content.pm.ApplicationInfo
import android.os.Build

/**
 * Dagger Hilt module for providing network-related dependencies such as OkHttpClient and interceptors.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Provides a singleton instance of [HttpClient] configured with the app's base URL and OkHttpClient.
     */
    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(AppConfig.BASE_URL, okHttpClient)

    /**
     * Provides a logging interceptor for HTTP requests. Logging is enabled only in debug builds.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(
        @ApplicationContext context: Context,
    ): HttpLoggingInterceptor {
        val isDebuggable = BuildConfig.DEBUG
        return HttpLoggingInterceptor().apply {
            level =
                if (isDebuggable) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }
    }

    /**
     * Provides a base URL interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideBaseUrlInterceptor(): BaseUrlInterceptor = BaseUrlInterceptor()

    /**
     * Provides a network interceptor that observes connectivity changes. Requires API 23+.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Provides
    @Singleton
    fun provideNetworkInterceptor(networkConnectivityObserver: NetworkConnectivityObserver): NetworkInterceptor =
        NetworkInterceptor(networkConnectivityObserver)

    /**
     * Provides an authentication token interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideAuthTokenInterceptor(): AuthTokenInterceptor = AuthTokenInterceptor()

    /**
     * Provides a response interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideResponseInterceptor(): ResponseInterceptor = ResponseInterceptor()

    /**
     * Provides the appropriate IConnectivityObserver implementation based on SDK version.
     */
    @Provides
    @Singleton
    fun provideConnectivityObserver(
        @ApplicationContext context: Context,
    ): IConnectivityObserver =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkConnectivityObserver(context)
        } else {
            LegacyNetworkConnectivityObserver(context)
        }

    /**
     * Provides a configured OkHttpClient with all required interceptors.
     * Logging is only added if the app is debuggable.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        responseInterceptor: ResponseInterceptor,
        networkInterceptor: NetworkInterceptor,
    ): OkHttpClient {
        val okHttpClient = OkHttpClient.Builder()
        // Only add logging interceptor if the app is debuggable
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            okHttpClient.addInterceptor(loggingInterceptor)
        }
        okHttpClient
            .addInterceptor(networkInterceptor)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authTokenInterceptor)
            .addInterceptor(responseInterceptor)
        return okHttpClient.build()
    }
}
