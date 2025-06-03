package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
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
}
