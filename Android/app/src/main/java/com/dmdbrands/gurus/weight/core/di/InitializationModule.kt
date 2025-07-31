package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.initialization.AppInitializer
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.ILogger
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
