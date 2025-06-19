package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.service.AccountAuthService
import com.greatergoods.meapp.core.service.AppEventService
import com.greatergoods.meapp.core.service.DeviceInfoService
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.core.service.IntegrationService
import com.greatergoods.meapp.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.greatergoods.meapp.core.shared.utilities.logging.LogManager
import com.greatergoods.meapp.data.services.EntryService
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.domain.services.IDeviceService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.domain.services.IIntegrationService
import com.greatergoods.meapp.features.common.service.DialogQueueService
import com.greatergoods.notification.NotificationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Dagger Hilt module for providing core service dependencies such as event and notification managers.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    /**
     * Provides a singleton instance of [IAccountAuthService].
     * @param accountAuthService The implementation of AccountAuthService.
     * @return [IAccountAuthService] instance.
     */
    @Provides
    @Singleton
    fun provideAccountAuthService(
        accountRepository: IAccountRepository,
        connectivityObserver: IConnectivityObserver,
        tokenManager: ITokenManager,
        dialogQueueService: IDialogQueueService
    ): IAccountAuthService = AccountAuthService(
        accountRepository,
        connectivityObserver,
        tokenManager,
        dialogQueueService,
    )

    /**
     * Provides a singleton instance of [IAppEventService].
     * @return [AppEventService] instance.
     */
    @Provides
    @Singleton
    fun provideAppEventService(): IAppEventService = AppEventService()

    /**
     * Provides a singleton instance of [GGNotificationManager] for notification operations.
     * @param context The application context.
     * @param notificationService The notification service dependency.
     * @return [GGNotificationManager] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        notificationService: NotificationService,
        appRepository: IAppRepository,
    ): GGNotificationManager = GGNotificationManager(context, notificationService, appRepository)

    /**
     * Provides a singleton instance of [LogManager] for logging operations.
     * @param logRepository The repository for storing logs.
     * @return [LogManager] instance.
     */
    @Provides
    @Singleton
    fun provideLogManager(logRepository: ILogRepository): LogManager = LogManager(logRepository)

    /**
     * Provides a singleton instance of [DialogQueueService] for managing dialog queues.
     * @return [DialogQueueService] instance.
     */
    @Provides
    @Singleton
    fun provideDialogQueueService(): IDialogQueueService {
        return DialogQueueService()
    }

    @Provides
    @Singleton
    fun provideEntryService(
        entryRepository: com.greatergoods.meapp.domain.repository.IEntryRepository,
        accountRepository: IAccountRepository
    ): IEntryService = EntryService(entryRepository, accountRepository)

    @Provides
    @Singleton
    fun provideDeviceInfoService(
        @ApplicationContext context: Context,
        deviceRepository: IDeviceInfoRepository,
        notificationService: NotificationService,
    ): IDeviceService = DeviceInfoService(context, deviceRepository, notificationService)

    /**
     * Provides a singleton instance of [IIntegrationService] for managing third-party integrations.
     * @param integrationRepository The repository for integration operations.
     * @param dialogQueueService The service for managing dialog queues.
     * @return [IntegrationService] instance.
     */
    @Provides
    @Singleton
    fun provideIntegrationService(
        integrationRepository: IIntegrationRepository,
        dialogQueueService: DialogQueueService,
    ): IIntegrationService = IntegrationService(integrationRepository, dialogQueueService)
}
