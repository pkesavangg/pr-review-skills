package com.greatergoods.meapp.core.di.di

import com.greatergoods.meapp.data.storage.db.AppDatabase
import com.greatergoods.meapp.data.storage.db.dao.AccountDao

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
