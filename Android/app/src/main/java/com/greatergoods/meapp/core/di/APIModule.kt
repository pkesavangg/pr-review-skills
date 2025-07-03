package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IBodyCompAPI
import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.data.api.IDeviceInfoAPI
import com.greatergoods.meapp.data.api.IExportAPI
import com.greatergoods.meapp.data.api.IGoalAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.api.INotificationAPI
import com.greatergoods.meapp.data.api.ISupportAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.api.IUserSettingsAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing API interface dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideEntryApi(httpClient: HttpClient): EntryApi = httpClient.createService(EntryApi::class.java)

    /**
     * Provides the IAuthAPI implementation using Retrofit.
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
     * Provides the IUserSettingsAPI implementation using Retrofit.
     */
    @Provides
    @Singleton
    fun provideUserSettingsAPI(httpClient: HttpClient): IUserSettingsAPI =
        httpClient.createService(IUserSettingsAPI::class.java)

    /**
     * Provides a singleton instance of [IDeviceInfoAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IDeviceInfoAPI] instance.
     */
    @Provides
    @Singleton
    fun provideDeviceAPI(httpClient: HttpClient): IDeviceInfoAPI = httpClient.createService(IDeviceInfoAPI::class.java)

    @Provides
    @Singleton
    fun provideIntegrationAPI(httpClient: HttpClient): IIntegrationAPI =
        httpClient.createService(IIntegrationAPI::class.java)

    @Provides
    @Singleton
    fun provideExportAPI(httpClient: HttpClient): IExportAPI = httpClient.createService(IExportAPI::class.java)

    /**
     * Provides a singleton instance of [IBodyCompAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IBodyCompAPI] instance.
     */
    @Provides
    @Singleton
    fun provideBodyCompAPI(httpClient: HttpClient): IBodyCompAPI = httpClient.createService(IBodyCompAPI::class.java)

    /**
     * Provides a singleton instance of [INotificationAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [INotificationAPI] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationAPI(httpClient: HttpClient): INotificationAPI =
        httpClient.createService(INotificationAPI::class.java)

    /**
     * Provides the IGoalAPI implementation using Retrofit.
     */
    @Provides
    @Singleton
    fun provideGoalAPI(httpClient: HttpClient): IGoalAPI = httpClient.createService(IGoalAPI::class.java)

    /**
     * Provides a singleton instance of [IDeviceAPI] using the provided [HttpClient].
     * @param httpClient The HTTP client for network operations.
     * @return [IDeviceAPI] instance.
     */
    @Provides
    @Singleton
    fun provideScaleAPI(httpClient: HttpClient): IDeviceAPI = httpClient.createService(IDeviceAPI::class.java)
     * Provides the ISupportAPI implementation using Retrofit.
     * Used for sending logs and other support-related operations.
     */
    @Provides
    @Singleton
    fun provideSupportAPI(httpClient: HttpClient): ISupportAPI = httpClient.createService(ISupportAPI::class.java)
}
