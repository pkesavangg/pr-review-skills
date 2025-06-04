package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.repository.UserRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
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
    fun provideAppRepository(userDataStore: UserDataStore, fcmDataStore: FcmDataStore): IAppRepository =
        AppRepository(userDataStore, fcmDataStore)

    @Provides
    @Singleton
    fun provideUserRepository(userDataStore: UserDataStore): IUserRepository =
        UserRepository(userDataStore)
}
