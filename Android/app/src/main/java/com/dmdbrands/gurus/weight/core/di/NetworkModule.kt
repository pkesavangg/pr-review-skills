package com.dmdbrands.gurus.weight.core.di

import androidx.annotation.RequiresApi
import com.dmdbrands.gurus.weight.BuildConfig
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.config.NetworkConfig
import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.core.network.ISecureTokenStore
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.SecureTokenStore
import com.dmdbrands.gurus.weight.core.network.TokenManager
import com.dmdbrands.gurus.weight.core.network.interceptors.AuthTokenInterceptor
import com.dmdbrands.gurus.weight.core.network.interceptors.BaseUrlInterceptor
import com.dmdbrands.gurus.weight.core.network.interceptors.NetworkInterceptor
import com.dmdbrands.gurus.weight.core.network.interceptors.ResponseInterceptor
import com.dmdbrands.gurus.weight.core.network.interceptors.TokenAuthenticator
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.qualifiers.RefreshClient
import com.dmdbrands.gurus.weight.core.network.utility.NetworkConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
import com.dmdbrands.gurus.weight.data.api.RefreshTokenAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
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
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(BuildConfig.BASE_URL, okHttpClient)

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
      NetworkConnectivityObserver(context)

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
     * Proactively refreshes tokens if they expire within 5 minutes.
     */
    @Provides
    @Singleton
    fun provideAuthTokenInterceptor(
        tokenManager: ITokenManager,
        refreshTokenAPI: RefreshTokenAPI,
    ): AuthTokenInterceptor =
        AuthTokenInterceptor(tokenManager, refreshTokenAPI)

    /**
     * Provides a response interceptor for OkHttp.
     */
    @Provides
    @Singleton
    fun provideResponseInterceptor(appNavigationService: IAppNavigationService): ResponseInterceptor =
        ResponseInterceptor(
            appNavigationService,
        )

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
            .baseUrl(BuildConfig.BASE_URL)
            .client(refreshOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefreshTokenAPI::class.java)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenManager: ITokenManager,
        refreshTokenAPI: RefreshTokenAPI,
        userDataStore: UserDataStore,
        navigationService: IAppNavigationService,
        crashReportingService: ICrashReportingService,
    ): TokenAuthenticator {
        return TokenAuthenticator(tokenManager, refreshTokenAPI, userDataStore, navigationService, crashReportingService)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
        networkInterceptor: NetworkInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // Only add logging interceptor if the app is debuggable
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            okHttpClient.addInterceptor(loggingInterceptor)
        }
        okHttpClient
            .addInterceptor(networkInterceptor)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authTokenInterceptor)
            .authenticator(tokenAuthenticator)
        return okHttpClient.build()
    }

    @Provides
    @Singleton
    fun provideSecureTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(context)

    @Provides
    @Singleton
    fun provideSecureTokenStoreInterface(secureTokenStore: SecureTokenStore): ISecureTokenStore =
        secureTokenStore

    @Provides
    @Singleton
    fun provideTokenManager(tokenManager: TokenManager): ITokenManager = tokenManager
}
