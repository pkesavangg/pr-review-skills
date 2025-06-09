package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.repository.UserRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.data.repository.LogRepository
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.domain.repository.ILogRepository
import dagger.Binds
import com.greatergoods.meapp.domain.repository.IUserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.greatergoods.meapp.data.repository.AccountRepository
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.core.network.TokenManager


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
    fun provideAccountRepository(
        accountDao: AccountDao,
        userDataStore: UserDataStore,
        tokenManager: TokenManager,
        authAPI: IAuthAPI,
        userAPI: IUserAPI,
        accountEntityMapper: AccountEntityMapper
    ): IAccountRepository =
        AccountRepository(accountDao, userDataStore, tokenManager, authAPI, userAPI, accountEntityMapper)
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
