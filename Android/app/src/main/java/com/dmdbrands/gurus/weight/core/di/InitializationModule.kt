package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.initialization.AppInitializer
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.ILogger
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
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
        crashReportingService: ICrashReportingService,
    ): AppInitializer =
        AppInitializer(
            logRepository,
            crashReportingService,
        )
}
