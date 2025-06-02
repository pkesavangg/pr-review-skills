package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.data.repository.ThemeRepository
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
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
object ThemeModule {
    /**
     * Provides a singleton instance of [ThemeDataStore].
     * @param context The application context.
     * @return [ThemeDataStore] instance.
     */
    @Provides
    @Singleton
    fun provideThemeDataStore(
        @ApplicationContext context: Context,
    ): ThemeDataStore = ThemeDataStore(context)

    /**
     * Provides a singleton instance of [ThemeRepository].
     * @param themeDataStore The data store for theme preferences.
     * @return [ThemeRepository] instance.
     */
    @Provides
    @Singleton
    fun provideThemeRepository(themeDataStore: ThemeDataStore): ThemeRepository = ThemeRepository(themeDataStore)
}
