package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IUserAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class APIModule {

    @Provides
    @Singleton
    fun provideAuthAPI(httpClient: HttpClient): IAuthAPI {
        return httpClient.createService(IAuthAPI::class.java)
    }

    @Provides
    @Singleton
    fun provideUserAPI(httpClient: HttpClient): IUserAPI {
        return httpClient.createService(IUserAPI::class.java)
    }
}
