package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.EntryRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.repository.LogRepository
import com.greatergoods.meapp.data.repository.UserRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
import dagger.Binds
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
        fcmDataStore: FcmDataStore
    ): IAppRepository {
        return AppRepository(userDataStore, fcmDataStore)
    }

    @Provides
    @Singleton
    fun provideHealthConnectRepository(
        healthConnectDataStore: HealthConnectDataStore
    ): IHealthConnectRepository {
        return HealthConnectRepository(healthConnectDataStore)
    }

    @Provides
    fun provideCurrentAccountId(): String {
        return "current_account_id"
    }

    @Provides
    @Singleton
    fun provideUserRepository(userDataStore: UserDataStore): IUserRepository =
        UserRepository(userDataStore)

    @Provides
    @Singleton
    fun provideEntryRepository(
        entryDao: EntryDao,
        entryApi: EntryApi,
    ): IEntryRepository {
        return EntryRepository(entryDao, entryApi)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindsModule {
    /**
     * Provides a singleton instance of [ILogRepository].
     * @param repository The implementation of [ILogRepository].
     * @return [ILogRepository] instance.
     */
    @Binds
    @Singleton
    abstract fun bindLogRepository(logRepository: LogRepository): ILogRepository
}
