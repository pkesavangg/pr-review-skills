package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.api.IAccountFlagAPI
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IBabyAPI
import com.dmdbrands.gurus.weight.data.api.IBodyCompAPI
import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.api.IDeviceInfoAPI
import com.dmdbrands.gurus.weight.data.api.IExportAPI
import com.dmdbrands.gurus.weight.data.api.IFeedAPI
import com.dmdbrands.gurus.weight.data.api.IGoalAPI
import com.dmdbrands.gurus.weight.data.api.IHealthConnectAPI
import com.dmdbrands.gurus.weight.data.api.IIntegrationAPI
import com.dmdbrands.gurus.weight.data.api.INotificationAPI
import com.dmdbrands.gurus.weight.data.api.ISupportAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.api.IUserSettingsAPI
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
     * Provides a singleton instance of [IBabyAPI] for Baby Profile CRUD.
     */
    @Provides
    @Singleton
    fun provideBabyAPI(httpClient: HttpClient): IBabyAPI = httpClient.createService(IBabyAPI::class.java)

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

    /**
     * Provides the IHealthConnectAPI implementation using Retrofit.
     * Used for Health Connect integration operations.
     */
    @Provides
    @Singleton
    fun provideHealthConnectAPI(httpClient: HttpClient): IHealthConnectAPI =
      httpClient.createService(IHealthConnectAPI::class.java)

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

    /**
     * Provides the ISupportAPI implementation using Retrofit.
     * Used for sending logs and other support-related operations.
     */
    @Provides
    @Singleton
    fun provideSupportAPI(httpClient: HttpClient): ISupportAPI = httpClient.createService(ISupportAPI::class.java)

    /**
     * Provides the IFeedAPI implementation using Retrofit.
     * Used for feed-related operations.
     */
    @Provides
    @Singleton
    fun provideFeedAPI(httpClient: HttpClient): IFeedAPI = httpClient.createService(IFeedAPI::class.java)
    /** Provides the IAccountFlagAPI implementation using Retrofit.
     * Used for account flag operations and app review flows.
     */
    @Provides
    @Singleton
    fun provideAccountFlagAPI(httpClient: HttpClient): IAccountFlagAPI = httpClient.createService(IAccountFlagAPI::class.java)
}
