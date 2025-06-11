package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.logging.ILogger
import com.greatergoods.meapp.core.logging.LoggerImpl
import com.greatergoods.meapp.domain.repository.ILogRepository
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
    ): ILogger =
        LoggerImpl(logRepository, loggerScope)
}
