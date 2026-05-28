package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.network.ISecureTokenStore
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.repository.EntryReadRepository
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryReadDao
import com.dmdbrands.gurus.weight.domain.repository.IEntryReadRepository
import com.dmdbrands.gurus.weight.data.api.IAccountFlagAPI
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IBodyCompAPI
import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.api.IDeviceInfoAPI
import com.dmdbrands.gurus.weight.data.api.IFeedAPI
import com.dmdbrands.gurus.weight.data.api.IGoalAPI
import com.dmdbrands.gurus.weight.data.api.IHealthConnectAPI
import com.dmdbrands.gurus.weight.data.api.IIntegrationAPI
import com.dmdbrands.gurus.weight.data.api.INotificationAPI
import com.dmdbrands.gurus.weight.data.api.ISupportAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.api.IUserSettingsAPI
import com.dmdbrands.gurus.weight.data.repository.AccountFlagRepository
import com.dmdbrands.gurus.weight.data.repository.AccountRepository
import com.dmdbrands.gurus.weight.data.repository.BabyProfileRepository
import com.dmdbrands.gurus.weight.data.repository.ProductSelectionRepository
import com.dmdbrands.gurus.weight.data.storage.datastore.ProductSelectionDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.data.repository.AppRepository
import com.dmdbrands.gurus.weight.data.repository.BodyCompositionRepository
import com.dmdbrands.gurus.weight.data.repository.DashboardRepository
import com.dmdbrands.gurus.weight.data.repository.DeviceInfoRepository
import com.dmdbrands.gurus.weight.data.repository.DeviceRepository
import com.dmdbrands.gurus.weight.data.repository.EntryRepository
import com.dmdbrands.gurus.weight.data.repository.FeedRepository
import com.dmdbrands.gurus.weight.data.repository.GoalRepository
import com.dmdbrands.gurus.weight.data.repository.HealthConnectRepository
import com.dmdbrands.gurus.weight.data.repository.IntegrationRepository
import com.dmdbrands.gurus.weight.data.repository.LogRepository
import com.dmdbrands.gurus.weight.data.repository.NotificationRepository
import com.dmdbrands.gurus.weight.data.repository.UserSettingsRepository
import com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceInfoRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAppRepository(
      userDataStore: UserDataStore,
      fcmDataStore: FcmDataStore,
    ): IAppRepository = AppRepository(userDataStore, fcmDataStore)

    @Provides
    @Singleton
    fun provideHealthConnectRepository(
      accountRepository: IAccountRepository,
      healthConnectDataStore: HealthConnectDataStore,
      healthConnectAPI: IHealthConnectAPI,
    ): IHealthConnectRepository =
      HealthConnectRepository(accountRepository, healthConnectAPI, healthConnectDataStore)

    @Provides
    @Singleton
    fun provideAccountRepository(
      accountDao: AccountDao,
      babyProfileDao: BabyProfileDao,
      userDataStore: UserDataStore,
      tokenManager: ITokenManager,
      secureTokenStore: ISecureTokenStore,
      authAPI: IAuthAPI,
      userAPI: IUserAPI,
    ): IAccountRepository =
      AccountRepository(accountDao, babyProfileDao, userDataStore, tokenManager, secureTokenStore, authAPI, userAPI)

    @Provides
    @Singleton
    fun provideIntegrationRepository(
      integrationAPI: IIntegrationAPI,
      accountDao: AccountDao,
      accountRepository: IAccountRepository,
      userAPI: IAuthAPI,
      healthConnectRepository: IHealthConnectRepository,
      @ApplicationScope appScope: CoroutineScope,
    ): IIntegrationRepository = IntegrationRepository(accountRepository, userAPI, integrationAPI, accountDao, healthConnectRepository, appScope)

    @Provides
    @Singleton
    fun provideDeviceInfoRepository(
      deviceAPI: IDeviceInfoAPI,
    ): IDeviceInfoRepository = DeviceInfoRepository(deviceAPI)

    @Provides
    fun provideCurrentAccountId(): String = "current_account_id"

    @Provides
    @Singleton
    fun provideEntryRepository(
      entryDao: EntryDao,
      entryApi: EntryApi,
    ): IEntryRepository = EntryRepository(entryDao, entryApi)

    @Provides
    @Singleton
    fun provideLogRepository(
      logDao: LogDao,
      supportAPI: ISupportAPI,
      accountService: IAccountService,
      @ApplicationScope appScope: CoroutineScope,
    ): ILogRepository = LogRepository(logDao, supportAPI, accountService, appScope)

    @Provides
    @Singleton
    fun provideBodyCompositionRepository(
      accountDao: AccountDao,
      bodyCompAPI: IBodyCompAPI,
    ): IBodyCompositionRepository = BodyCompositionRepository(accountDao, bodyCompAPI)

    @Provides
    @Singleton
    fun provideNotificationRepository(
      accountDao: AccountDao,
      notificationAPI: INotificationAPI,
    ): INotificationRepository = NotificationRepository(notificationAPI, accountDao)

    @Provides
    @Singleton
    fun provideUserSettingsRepository(
      userSettingsAPI: IUserSettingsAPI,
      accountDao: AccountDao,
      userDataStore: UserDataStore,
    ): IUserSettingsRepository = UserSettingsRepository(userSettingsAPI, accountDao, userDataStore)

    @Provides
    @Singleton
    fun provideGoalRepository(
      goalAPI: IGoalAPI,
      accountDao: AccountDao,
      accountRepository: IAccountRepository,
    ): IGoalRepository = GoalRepository(goalAPI, accountDao, accountRepository)

    @Provides
    @Singleton
    fun provideDashboardRepository(
      accountDao: AccountDao,
      accountRepository: IAccountRepository
    ): IDashboardRepository =
      DashboardRepository(accountDao, accountRepository)

    @Provides
    @Singleton
    fun provideDeviceRepository(
      deviceAPI: IDeviceAPI,
      deviceDao: DeviceDao,
    ): IDeviceRepository = DeviceRepository(deviceAPI, deviceDao)

    @Provides
    @Singleton
    fun provideFeedRepository(
      feedAPI: IFeedAPI,
      accountService: IAccountService,
    ): IFeedRepository = FeedRepository(feedAPI, accountService)

    @Provides
    @Singleton
    fun provideAccountFlagRepository(
        accountFlagAPI: IAccountFlagAPI,
    ): IAccountFlagRepository = AccountFlagRepository(accountFlagAPI)

    @Provides
    @Singleton
    fun provideBabyProfileRepository(
        babyProfileDao: BabyProfileDao,
    ): IBabyProfileRepository = BabyProfileRepository(babyProfileDao)

    @Provides
    @Singleton
    fun provideProductSelectionRepository(
        productSelectionDataStore: ProductSelectionDataStore,
        babyProfileDao: BabyProfileDao,
        deviceDao: DeviceDao,
    ): IProductSelectionRepository = ProductSelectionRepository(productSelectionDataStore, babyProfileDao, deviceDao)

    @Provides
    @Singleton
    fun provideEntryReadRepository(
        entryReadDao: EntryReadDao,
    ): IEntryReadRepository = EntryReadRepository(entryReadDao)
}
