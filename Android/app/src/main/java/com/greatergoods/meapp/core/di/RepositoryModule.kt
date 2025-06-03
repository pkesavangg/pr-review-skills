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
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAppRepository(
        themeDataStore: ThemeDataStore,
        fcmDataStore: FcmDataStore
    ): IAppRepository {
        return AppRepository(themeDataStore, fcmDataStore)
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
