package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.AccountFlagService
import com.dmdbrands.gurus.weight.core.service.AccountService
import com.dmdbrands.gurus.weight.core.service.AppNavigationService
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.service.AppSyncService
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.BodyCompositionService
import com.dmdbrands.gurus.weight.core.service.DashboardService
import com.dmdbrands.gurus.weight.core.service.DeviceInfoService
import com.dmdbrands.gurus.weight.core.service.DeviceService
import com.dmdbrands.gurus.weight.core.service.FeedService
import com.dmdbrands.gurus.weight.core.service.GoalService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.IntegrationService
import com.dmdbrands.gurus.weight.core.service.NotificationService
import com.dmdbrands.gurus.weight.core.service.OfflineHandlerService
import com.dmdbrands.gurus.weight.core.service.StorageClearService
import com.dmdbrands.gurus.weight.core.service.UserSettingsService
import com.dmdbrands.gurus.weight.core.service.WifiScaleService
import com.dmdbrands.gurus.weight.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.data.api.IExportAPI
import com.dmdbrands.gurus.weight.data.services.EntryService
import com.dmdbrands.gurus.weight.data.services.ExportService
import com.dmdbrands.gurus.weight.data.storage.datastore.BaseProtoDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.BluetoothPreferencesDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.GoalAlertDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceInfoRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.domain.services.INotificationService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.service.DialogQueueService
import com.dmdbrands.gurus.weight.features.common.service.DialogUtility
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.lib.wificonnect.WifiSmartConnectManager
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
   * Provides a singleton instance of [AppStatusService].
   * @return [AppStatusService] instance.
   */
  @Provides
  @Singleton
  fun provideAppStatusService(): AppStatusService = AppStatusService

  /**
   * Provides a singleton instance of [BluetoothPreferencesService].
   * @param bluetoothPreferencesDataStore The Bluetooth preferences DataStore.
   * @param appStatusService The app status service for testing features.
   * @return [BluetoothPreferencesService] instance.
   */
  @Provides
  @Singleton
  fun provideBluetoothPreferencesService(
    bluetoothPreferencesDataStore: BluetoothPreferencesDataStore,
  ): BluetoothPreferencesService = BluetoothPreferencesService(bluetoothPreferencesDataStore)

  /**
   * Provides a singleton instance of [IWifiscaleService].
   * @param context The application context.
   * @param wifiSmartConnectManager The WiFi smart connect manager.
   * @param deviceService The device service.
   * @return [WifiScaleService] instance.
   */
  @Provides
  @Singleton
  fun provideWifiScaleService(
    @ApplicationContext context: Context,
    wifiSmartConnectManager: WifiSmartConnectManager,
    deviceService: IDeviceService
  ) = WifiScaleService(
    wifiSmartConnectManager,
    deviceService,
    context,
  )

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
    entryService: IEntryService
  ): GGNotificationManager = GGNotificationManager(context, notificationService, appRepository, entryService)

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
    dialogQueueService: IDialogQueueService,
    appNavigationService: IAppNavigationService
  ): LogManager = LogManager(
    logRepository, connectivityObserver, dialogQueueService, appNavigationService,
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
    healthConnectService: IHealthConnectService,
    healthConnectRepository: IHealthConnectRepository,
    goalService: IGoalService
  ): IEntryService = EntryService(entryRepository, goalRepository, accountRepository, goalService, healthConnectService, healthConnectRepository)

  @Provides
  @Singleton
  fun provideDeviceInfoService(
    @ApplicationContext context: Context,
    deviceInfoRepository: IDeviceInfoRepository,
    dialogQueueService: IDialogQueueService,
    connectivityObserver: IConnectivityObserver,
    offlineHandlerService: IOfflineHandlerService,
    appNavigationService: IAppNavigationService,
    appRepository: IAppRepository,
    accountRepository: IAccountRepository,
    healthConnectRepository: IHealthConnectRepository,
    integrationRepository: IIntegrationRepository
  ): IDeviceInfoService =
    DeviceInfoService(
      context,
      deviceInfoRepository,
      connectivityObserver,
      dialogQueueService,
      appNavigationService,
      offlineHandlerService,
      appRepository,
      accountRepository,
      healthConnectRepository,
      integrationRepository,
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
      integrationRepository: IIntegrationRepository,
      appNavigationService: IAppNavigationService,
      healthConnectRepository: IHealthConnectRepository,
    ): IIntegrationService =
      IntegrationService(
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
        accountService,
        integrationRepository,
        healthConnectRepository,
      )

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
      deviceService: IDeviceService,
      notificationRepository: INotificationRepository,
      userSettingsRepository: IUserSettingsRepository,
      goalRepository: IGoalRepository,
      connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
    ): IOfflineHandlerService =
      OfflineHandlerService(
        accountRepository,
        bodyCompositionRepository,
        deviceService,
        notificationRepository,
        userSettingsRepository,
        goalRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
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
      appNavigationService: IAppNavigationService,
    ): IBodyCompositionService =
      BodyCompositionService(
        bodyCompositionRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
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
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
    ): INotificationService =
      NotificationService(
        notificationRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
      )

    @Provides
    @Singleton
    fun provideUserSettingsService(
      userSettingsRepository: IUserSettingsRepository,
      connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
    ): IUserSettingsService =
      UserSettingsService(userSettingsRepository, connectivityObserver, dialogQueueService, appNavigationService)

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
      appNavigationService: IAppNavigationService,
      goalAlertDataStore: GoalAlertDataStore,
      accountRepository: IAccountRepository,
      deviceService: IDeviceService
    ): IGoalService =
      GoalService(
        goalRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
        goalAlertDataStore,
        accountRepository,
        deviceService
      )

    @Provides
    @Singleton
    fun provideDashboardService(
      dashboardRepository: IDashboardRepository,
      accountRepository: IAccountRepository
    ): IDashboardService =
      DashboardService(dashboardRepository, accountRepository)

    @Provides
    @Singleton
    fun provideSelectedFeedItemHolder(): SelectedFeedItemHolder = SelectedFeedItemHolder()

    @Provides
    @Singleton
    fun provideFeedService(
      feedRepository: IFeedRepository,
      accountService: IAccountService,
      ggIAMService: GGInAppMessagingService,
      connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
      selectedFeedItemHolder: SelectedFeedItemHolder,
      @ApplicationContext context: Context
    ): IFeedService = FeedService(feedRepository, accountService, ggIAMService, connectivityObserver, dialogQueueService, appNavigationService, selectedFeedItemHolder, context)

    /**
     * Provides the device service implementation.
     * Handles scale/device data operations with automatic synchronization.
     */
    @Provides
    @Singleton
    fun provideDeviceService(
      @ApplicationContext context: Context,
      deviceRepository: IDeviceRepository,
      connectivityObserver: IConnectivityObserver,
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
    ): IDeviceService =
      DeviceService(deviceRepository, connectivityObserver, dialogQueueService, appNavigationService, context)

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

    /**
     * Provides the AppSync service implementation.
     * Handles AppSync data conversion, editing, and saving operations.
     */
    @Provides
    @Singleton
    fun provideAppSyncService(
      entryService: IEntryService,
      accountService: IAccountService,
      navigationService: IAppNavigationService,
      dialogQueueService: IDialogQueueService
    ): IAppSyncService = AppSyncService(
      entryService = entryService,
      accountService = accountService,
      appNavigationService = navigationService,
      dialogQueueService = dialogQueueService,
    )


     /**
   * Provides the AccountFlag service implementation.
   * Handles account flag operations and app review flows.
   */
  @Provides
  @Singleton
  fun provideAccountFlagService(
    @ApplicationContext context: Context,
    accountFlagRepository: IAccountFlagRepository,
    appReviewManager: com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager,
  ): IAccountFlagService = AccountFlagService(context, accountFlagRepository, appReviewManager)

}
