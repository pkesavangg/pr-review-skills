package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.HealthConnectRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
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
}
