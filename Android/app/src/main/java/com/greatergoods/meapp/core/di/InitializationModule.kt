package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.initialization.AppInitializer
import com.greatergoods.meapp.core.shared.utilities.logging.ILogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InitializationModule {
    @Provides
    @Singleton
    fun provideAppInitializer(
        logRepository: ILogger,
        // Add other dependencies here
    ): AppInitializer =
        AppInitializer(
            logRepository,
        )
}
