package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.ILogger
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LoggerImpl
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun provideLoggerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideLogger(
        logRepository: ILogRepository,
        loggerScope: CoroutineScope,
    ): ILogger = LoggerImpl(logRepository, loggerScope)
}
