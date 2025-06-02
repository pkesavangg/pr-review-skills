package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.service.AppEventService
import com.greatergoods.meapp.core.service.IAppEventService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAppEventService(): IAppEventService {
        return AppEventService()
    }
}