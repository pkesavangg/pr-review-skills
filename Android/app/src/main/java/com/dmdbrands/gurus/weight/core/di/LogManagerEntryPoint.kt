package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.initialization.AppInitializer
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.features.common.service.DialogQueueService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogManagerEntryPoint {
    fun logRepository(): ILogRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun appInitializer(): AppInitializer
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ViewModelServiceEntryPoint {
    val navigationService: IAppNavigationService
    val dialogQueueService: DialogQueueService
}
