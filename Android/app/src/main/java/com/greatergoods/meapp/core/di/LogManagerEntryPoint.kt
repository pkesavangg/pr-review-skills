package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.initialization.AppInitializer
import com.greatergoods.meapp.domain.repository.ILogRepository
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
