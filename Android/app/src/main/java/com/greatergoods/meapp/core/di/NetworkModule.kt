package com.greatergoods.meapp.core.di

import androidx.annotation.RequiresApi
import com.greatergoods.meapp.BuildConfig
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.core.network.interceptors.AuthTokenInterceptor
import com.greatergoods.meapp.core.network.interceptors.BaseUrlInterceptor
import com.greatergoods.meapp.core.network.interceptors.NetworkInterceptor
import com.greatergoods.meapp.core.network.interceptors.ResponseInterceptor
import com.greatergoods.meapp.core.network.interceptors.TokenAuthenticator
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.network.utility.LegacyNetworkConnectivityObserver
import com.greatergoods.meapp.core.network.utility.NetworkConnectivityObserver
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.core.service.AccountAuthService
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.RefreshTokenAPI
import com.greatergoods.meapp.core.network.qualifiers.RefreshClient

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
     * Provides a network interceptor that observes connectivity changes. Requires API 23+.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Provides
    @Singleton
    fun provideNetworkInterceptor(
        connectivityObserver: IConnectivityObserver
    ): NetworkInterceptor = NetworkInterceptor(connectivityObserver)

    /**
     * Provides an authentication token interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideAuthTokenInterceptor(tokenManager: ITokenManager): AuthTokenInterceptor = AuthTokenInterceptor(tokenManager)

    /**
     * Provides a response interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideResponseInterceptor(): ResponseInterceptor = ResponseInterceptor()

    /**
     * Provides a basic OkHttpClient for token refresh (no authenticator).
     */
    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    /**
     * Provides a minimal Retrofit API for token refresh.
     */
    @Provides
    @Singleton
    fun provideRefreshTokenAPI(@RefreshClient refreshOkHttpClient: OkHttpClient): RefreshTokenAPI =
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(refreshOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefreshTokenAPI::class.java)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenManager: ITokenManager,
        refreshTokenAPI: RefreshTokenAPI
    ): TokenAuthenticator {
        return TokenAuthenticator(tokenManager, refreshTokenAPI)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        responseInterceptor: ResponseInterceptor,
        networkInterceptor: NetworkInterceptor,
        tokenAuthenticator: TokenAuthenticator
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
            .authenticator(tokenAuthenticator)
        return okHttpClient.build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(
        okHttpClient: OkHttpClient
    ): AccountAuthService {
        return Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AccountAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenManager(tokenManager: TokenManager): ITokenManager = tokenManager
}
