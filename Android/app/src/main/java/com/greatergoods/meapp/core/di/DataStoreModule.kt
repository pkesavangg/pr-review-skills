package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.storage.datastore.DashboardKeysDatastore
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Dagger Hilt module for providing theme-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideUserDataStore(
        @ApplicationContext context: Context
    ): UserDataStore = UserDataStore(context)

    /**
     * Provides a singleton instance of [ThemeRepository].
     * @param themeDataStore The data store for theme preferences.
     * @return [ThemeRepository] instance.
     */
    @Provides
    @Singleton
    fun provideFcmDataStore(
        @ApplicationContext context: Context,
    ): FcmDataStore =
        FcmDataStore(context)

    @Provides
    @Singleton
    fun provideVisileMetricsDataStore(
        @ApplicationContext context: Context,
    ): DashboardKeysDatastore =
        DashboardKeysDatastore(context)

    /**
     * Provides a singleton instance of [HealthConnectDataStore].
     */
    @Provides
    @Singleton
    fun provideHealthConnectDataStore(
        @ApplicationContext context: Context,
    ): HealthConnectDataStore =
        HealthConnectDataStore(context)
}
