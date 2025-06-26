package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.repository.AccountRepository
import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.DeviceInfoRepository
import com.greatergoods.meapp.data.repository.EntryRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.repository.IntegrationRepository
import com.greatergoods.meapp.data.repository.LogRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
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
        tokenManager: ITokenManager,
        authAPI: IAuthAPI,
        userAPI: IUserAPI,
    ): IAccountRepository =
        AccountRepository(accountDao, userDataStore, tokenManager, authAPI, userAPI)

    @Provides
    @Singleton
    fun provideIntegrationRepository(
        integrationAPI: IIntegrationAPI,
        accountDao: AccountDao,
    ): IIntegrationRepository = IntegrationRepository(integrationAPI, accountDao)

    @Provides
    @Singleton
    fun provideDeviceInfoRepository(
        deviceAPI: IDeviceAPI,
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
    fun provideLogRepository(logDao: LogDao): ILogRepository = LogRepository(logDao)
}
