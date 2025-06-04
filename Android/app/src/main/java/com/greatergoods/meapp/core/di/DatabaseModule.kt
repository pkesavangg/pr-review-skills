package com.greatergoods.meapp.core.di

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
import android.content.Context

/**
 * Dagger Hilt module for providing database DAO dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Provides a singleton instance of [AccountDao].
     * @param database The app's Room database.
     * @return [AccountDao] instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    /**
     * Provides a singleton instance of [DeviceDao].
     * @param database The app's Room database.
     * @return [DeviceDao] instance.
     */
    @Provides
    @Singleton
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    /**
     * Provides a singleton instance of [EntryDao].
     * @param database The app's Room database.
     * @return [EntryDao] instance.
     */
    @Provides
    @Singleton
    fun provideEntryDao(database: AppDatabase): EntryDao = database.entryDao()
}
