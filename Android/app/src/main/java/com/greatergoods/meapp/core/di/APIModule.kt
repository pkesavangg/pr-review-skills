package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.api.IUserAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing API service dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
class APIModule {

    @Provides
    @Singleton
    fun provideEntryApi(httpClient: HttpClient): EntryApi =
        httpClient.createService(EntryApi::class.java)

    /**
     * Provides a singleton instance of [IAuthAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IAuthAPI] instance.
     */
    @Provides
    @Singleton
    fun provideAuthAPI(httpClient: HttpClient): IAuthAPI = httpClient.createService(IAuthAPI::class.java)

    /**
     * Provides a singleton instance of [IUserAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IUserAPI] instance.
     */
    @Provides
    @Singleton
    fun provideUserAPI(httpClient: HttpClient): IUserAPI = httpClient.createService(IUserAPI::class.java)

    /**
     * Provides a singleton instance of [IDeviceAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IDeviceAPI] instance.
     */
    @Provides
    @Singleton
    fun provideDeviceAPI(httpClient: HttpClient): IDeviceAPI = httpClient.createService(IDeviceAPI::class.java)

    @Provides
    @Singleton
    fun provideIntegrationAPI(
        httpClient: HttpClient
    ): IIntegrationAPI = httpClient.createService(IIntegrationAPI::class.java)
}
