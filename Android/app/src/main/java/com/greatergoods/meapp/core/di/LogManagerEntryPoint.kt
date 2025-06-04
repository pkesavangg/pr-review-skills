package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.logging.LogManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogManagerEntryPoint {
    fun logManager(): LogManager
} 