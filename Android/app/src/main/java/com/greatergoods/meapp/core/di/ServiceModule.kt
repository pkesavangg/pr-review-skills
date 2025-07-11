package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.service.AccountService
import com.greatergoods.meapp.core.service.AppNavigationService
import com.greatergoods.meapp.core.service.BodyCompositionService
import com.greatergoods.meapp.core.service.DashboardService
import com.greatergoods.meapp.core.service.DeviceInfoService
import com.greatergoods.meapp.core.service.DeviceService
import com.greatergoods.meapp.core.service.GoalService
import com.greatergoods.meapp.core.service.IAppNavigationService
import com.greatergoods.meapp.core.service.IntegrationService
import com.greatergoods.meapp.core.service.NotificationService
import com.greatergoods.meapp.core.service.OfflineHandlerService
import com.greatergoods.meapp.core.service.StorageClearService
import com.greatergoods.meapp.core.service.UserSettingsService
import com.greatergoods.meapp.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.greatergoods.meapp.core.shared.utilities.logging.LogManager
import com.greatergoods.meapp.data.api.IExportAPI
import com.greatergoods.meapp.data.services.EntryService
import com.greatergoods.meapp.data.services.ExportService
import com.greatergoods.meapp.data.storage.datastore.BaseProtoDataStore
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.AppDatabase
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.repository.IDashboardRepository
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.repository.IDeviceRepository
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IGoalRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.domain.repository.INotificationRepository
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IBodyCompositionService
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.domain.services.IDeviceInfoService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.domain.services.IExportService
import com.greatergoods.meapp.domain.services.IGoalService
import com.greatergoods.meapp.domain.services.IIntegrationService
import com.greatergoods.meapp.domain.services.INotificationService
import com.greatergoods.meapp.domain.services.IOfflineHandlerService
import com.greatergoods.meapp.domain.services.IUserSettingsService
import com.greatergoods.meapp.features.common.service.DialogQueueService
import com.greatergoods.meapp.features.common.service.DialogUtility
import com.greatergoods.notification.NotificationService as GGNotificationService
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
   * Provides a singleton instance of [IAccountService].
   * @param accountService The implementation of AccountService.
   * @return [IAccountService] instance.
   */
  @Provides
  @Singleton
  fun provideAccountService(
    accountRepository: IAccountRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
    appNavigationService: IAppNavigationService,
    storageClearService: StorageClearService,
  ): IAccountService =
    AccountService(
      accountRepository,
      connectivityObserver,
      dialogQueueService,
      appNavigationService,
      storageClearService,
    )

  /**
   * Provides a singleton instance of [IAppEventService].
   * @return [AppEventService] instance.
   */
  @Provides
  @Singleton
  fun provideAppNavigationService(): IAppNavigationService = AppNavigationService()

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
    notificationService: GGNotificationService,
    appRepository: IAppRepository,
  ): GGNotificationManager = GGNotificationManager(context, notificationService, appRepository)

  /**
   * Provides a singleton instance of [LogManager] for logging operations.
   * @param logRepository The repository for storing logs.
   * @return [LogManager] instance.
   */
  @Provides
  @Singleton
  fun provideLogManager(
    logRepository: ILogRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService
  ): LogManager = LogManager(
    logRepository, connectivityObserver, dialogQueueService,
  )

  /**
   * Provides a singleton instance of [DialogQueueService] for managing dialog queues.
   * @return [DialogQueueService] instance.
   */
  @Provides
  @Singleton
  fun provideDialogQueueService(): IDialogQueueService = DialogQueueService()

  /**
   * Provides a singleton instance of [IDialogUtility] for common dialog operations.
   * @param dialogQueueService The dialog queue service dependency.
   * @return [DialogUtility] instance.
   */
  @Provides
  @Singleton
  fun provideDialogUtility(dialogQueueService: IDialogQueueService): IDialogUtility =
    DialogUtility(dialogQueueService)

  @Provides
  @Singleton
  fun provideEntryService(
    entryRepository: IEntryRepository,
    goalRepository: IGoalRepository,
    accountRepository: IAccountRepository,
  ): IEntryService = EntryService(entryRepository, goalRepository, accountRepository)

    /**
     * Provides a singleton instance of [IIntegrationService] for managing third-party integrations.
     * @param accountService The service for account management.
     * @return [IntegrationService] instance.
     */
    @Provides
    @Singleton
    fun provideIntegrationService(
      connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
        accountService: IAccountService,
      integrationRepository: IIntegrationRepository
    ): IIntegrationService = IntegrationService(connectivityObserver,dialogQueueService, accountService,integrationRepository)

    @Provides
    @Singleton
    fun provideDeviceInfoService(
        @ApplicationContext context: Context,
        deviceInfoRepository: IDeviceInfoRepository,
        connectivityObserver: IConnectivityObserver,
        offlineHandlerService: IOfflineHandlerService,
        appRepository: IAppRepository,
        accountRepository: IAccountRepository,
    ): IDeviceInfoService =
        DeviceInfoService(
            context,
            deviceInfoRepository,
            connectivityObserver,
            offlineHandlerService,
            appRepository,
            accountRepository,
        )

   /**
    * Provides a singleton instance of [IIntegrationService] for managing third-party integrations.
    * @param integrationRepository The repository for integration operations.
    * @param dialogQueueService The service for managing dialog queues.
    * @return [IntegrationService] instance.
    */
   @Provides
   @Singleton
   fun provideIntegrationService(
         connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
        accountService: IAccountService,
      integrationRepository: IIntegrationRepository
   ): IIntegrationService = IntegrationService(connectivityObserver,dialogQueueService, accountService,integrationRepository)

  /**
   * Provides the export service implementation.
   */
  @Provides
  @Singleton
  fun provideExportService(
    exportAPI: IExportAPI,
    accountService: IAccountService,
    dialogQueueService: IDialogQueueService,
  ): IExportService = ExportService(exportAPI, accountService, dialogQueueService)

  /**
   * Provides the offline handler service implementation.
   * Handles offline data synchronization and biological sex updates.
   */
  @Provides
  @Singleton
  fun provideOfflineHandlerService(
    accountRepository: IAccountRepository,
    bodyCompositionRepository: IBodyCompositionRepository,
    notificationRepository: INotificationRepository,
    userSettingsRepository: IUserSettingsRepository,
    goalRepository: IGoalRepository,
    connectivityObserver: IConnectivityObserver,
  ): IOfflineHandlerService =
    OfflineHandlerService(
      accountRepository,
      bodyCompositionRepository,
      notificationRepository,
      userSettingsRepository,
      goalRepository,
      connectivityObserver,
    )

  /**
   * Provides the body composition service implementation.
   * Handles activity level, weight unit, and height updates with offline support.
   */
  @Provides
  @Singleton
  fun provideBodyCompositionService(
    bodyCompositionRepository: IBodyCompositionRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
  ): IBodyCompositionService =
    BodyCompositionService(
      bodyCompositionRepository,
      connectivityObserver,
      dialogQueueService,
    )

  /**
   * Provides the notification service implementation.
   * Handles notification settings with offline support.
   */
  @Provides
  @Singleton
  fun provideNotificationService(
    notificationRepository: INotificationRepository,
    connectivityObserver: IConnectivityObserver,
  ): INotificationService =
    NotificationService(
      notificationRepository,
      connectivityObserver,
    )

  @Provides
  @Singleton
  fun provideUserSettingsService(
    userSettingsRepository: IUserSettingsRepository,
    connectivityObserver: IConnectivityObserver,
  ): IUserSettingsService = UserSettingsService(userSettingsRepository, connectivityObserver)

  /**
   * Provides the goal service implementation.
   * Handles goal management, percentage calculation, and goal completion alerts.
   */
  @Provides
  @Singleton
  fun provideGoalService(
    goalRepository: IGoalRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
  ): IGoalService = GoalService(goalRepository, connectivityObserver, dialogQueueService)

  @Provides
  @Singleton
  fun provideDashboardService(
    dashboardRepository: IDashboardRepository
  ): IDashboardService =
    DashboardService(dashboardRepository)

  /**
   * Provides the device service implementation.
   * Handles scale/device data operations with automatic synchronization.
   */
  @Provides
  @Singleton
  fun provideDeviceService(
    deviceRepository: IDeviceRepository
  ): IDeviceService = DeviceService(deviceRepository)

  @Provides
  @Singleton
  fun provideDataStores(
    userDataStore: UserDataStore,
    fcmDataStore: FcmDataStore,
    healthConnectDataStore: HealthConnectDataStore,
  ): Set<BaseProtoDataStore<*>> = setOf(
    userDataStore,
    fcmDataStore,
    healthConnectDataStore,
  )

  @Provides
  @Singleton
  fun provideStorageClearService(
    @ApplicationContext context: Context,
    appDatabase: AppDatabase,
    dataStores: Set<@JvmSuppressWildcards BaseProtoDataStore<*>>,
    navigationService: IAppNavigationService
  ): StorageClearService = StorageClearService(
    context = context,
    appDatabase = appDatabase,
    dataStores = dataStores,
    navigationService = navigationService,
  )
}
