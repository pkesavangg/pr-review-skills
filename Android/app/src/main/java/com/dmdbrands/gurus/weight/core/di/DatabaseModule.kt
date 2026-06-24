package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyEntryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryReadDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
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
     * Provides a singleton instance of [AppDatabase].
     * @param context The application context.
     * @return [AppDatabase] instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabase.getInstance(context)

    /**
     * Provides a singleton instance of [AccountDao].
     * @param database The app's Room database.
     * @return [AccountDao] instance.
     */
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

    /**
     * Provides a singleton instance of [LogDao].
     * @param database The app's Room database.
     * @return [LogDao] instance.
     */
    @Provides
    @Singleton
    fun provideLogDao(database: AppDatabase): LogDao = database.logDao()

    @Provides
    @Singleton
    fun provideBabyProfileDao(database: AppDatabase): BabyProfileDao = database.babyProfileDao()

    @Provides
    @Singleton
    fun provideBabyEntryDao(database: AppDatabase): BabyEntryDao = database.babyEntryDao()

    @Provides
    @Singleton
    fun provideEntryReadDao(database: AppDatabase): EntryReadDao = database.entryReadDao()

}
