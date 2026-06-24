package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.GoalAlertDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
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
    @ApplicationContext context: Context,
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
  ): FcmDataStore = FcmDataStore(context)

  /**
   * Provides a singleton instance of [HealthConnectDataStore].
   */
  @Provides
  @Singleton
  fun provideHealthConnectDataStore(
    @ApplicationContext context: Context,
  ): HealthConnectDataStore = HealthConnectDataStore(context)

  @Provides
  @Singleton
  fun provideGoalAlertDataStore(
    @ApplicationContext context: Context,
  ): GoalAlertDataStore = GoalAlertDataStore(context)

}
