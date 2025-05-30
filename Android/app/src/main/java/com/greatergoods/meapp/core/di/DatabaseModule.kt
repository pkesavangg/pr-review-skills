package com.greatergoods.meapp.core.di

import android.content.Context
import androidx.room.Room
import com.greatergoods.meapp.data.storage.db.AppDatabase
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAccountDao(
        database: AppDatabase
    ): AccountDao = database.accountDao()

    @Provides
    @Singleton
    fun provideDeviceDao(
        database: AppDatabase
    ): DeviceDao = database.deviceDao()

    @Provides
    @Singleton
    fun provideEntryDao(
        database: AppDatabase
    ): EntryDao = database.entryDao()
} 