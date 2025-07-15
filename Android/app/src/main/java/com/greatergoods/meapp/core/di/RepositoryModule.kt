package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IBodyCompAPI
import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.data.api.IDeviceInfoAPI
import com.greatergoods.meapp.data.api.IGoalAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.api.INotificationAPI
import com.greatergoods.meapp.data.api.ISupportAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.api.IUserSettingsAPI
import com.greatergoods.meapp.data.repository.AccountRepository
import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.BodyCompositionRepository
import com.greatergoods.meapp.data.repository.DashboardRepository
import com.greatergoods.meapp.data.repository.DeviceInfoRepository
import com.greatergoods.meapp.data.repository.DeviceRepository
import com.greatergoods.meapp.data.repository.EntryRepository
import com.greatergoods.meapp.data.repository.GoalRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.repository.IntegrationRepository
import com.greatergoods.meapp.data.repository.LogRepository
import com.greatergoods.meapp.data.repository.NotificationRepository
import com.greatergoods.meapp.data.repository.UserSettingsRepository
import com.greatergoods.meapp.data.storage.datastore.DashboardKeysDatastore
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.repository.IDashboardRepository
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.repository.IDeviceRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IGoalRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.domain.repository.INotificationRepository
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import com.greatergoods.meapp.domain.services.IAccountService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideHealthConnectRepository(healthConnectDataStore: HealthConnectDataStore): IHealthConnectRepository =
        HealthConnectRepository(healthConnectDataStore)

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao,
        userDataStore: UserDataStore,
        dashboardKeysDatastore: DashboardKeysDatastore,
        tokenManager: ITokenManager,
        authAPI: IAuthAPI,
        userAPI: IUserAPI,
    ): IAccountRepository =
        AccountRepository(accountDao, userDataStore, dashboardKeysDatastore, tokenManager, authAPI, userAPI)

    @Provides
    @Singleton
    fun provideIntegrationRepository(
        integrationAPI: IIntegrationAPI,
        accountDao: AccountDao,
        accountRepository: IAccountRepository,
        userAPI: IAuthAPI
    ): IIntegrationRepository = IntegrationRepository(accountRepository,userAPI,integrationAPI, accountDao)

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
    ): ILogRepository = LogRepository(logDao, supportAPI, accountService)

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
    ): IUserSettingsRepository = UserSettingsRepository(userSettingsAPI, accountDao)

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
        dashboardKeysDatastore: DashboardKeysDatastore
    ): IDashboardRepository =
        DashboardRepository(dashboardKeysDatastore)

    @Provides
    @Singleton
    fun provideDeviceRepository(
        deviceAPI: IDeviceAPI,
        deviceDao: DeviceDao,
    ): IDeviceRepository = DeviceRepository(deviceAPI, deviceDao)
}
