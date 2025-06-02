package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.data.repository.LogRepository
import com.greatergoods.meapp.domain.repository.ILogRepository
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
    fun provideAppRepository(themeDataStore: ThemeDataStore, fcmDataStore: FcmDataStore): IAppRepository =
        AppRepository(themeDataStore, fcmDataStore)

    @Provides
    @Singleton
    fun provideHealthConnectRepository(
        healthConnectDataStore: HealthConnectDataStore
    ): IHealthConnectRepository =
        HealthConnectRepository(healthConnectDataStore)

    /**
     * Provides a singleton instance of [ILogRepository].
     * @param repository The implementation of [ILogRepository].
     * @return [ILogRepository] instance.
     */
    @Binds
    @Singleton
    abstract fun bindLogRepository(repository: LogRepository): ILogRepository

    companion object {
        /**
         * Provides the current account ID for logging.
         * @return The current account ID as a String.
         */
        @Provides
        @Singleton
        fun provideCurrentAccountId(): String = "default" // TODO: Replace with actual account ID
    }
} 
