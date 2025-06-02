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

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    @Provides
    @Singleton
    fun provideThemeDataStore(
        @ApplicationContext context: Context,
    ): ThemeDataStore = ThemeDataStore(context)

    @Provides
    @Singleton
    fun provideThemeRepository(themeDataStore: ThemeDataStore): ThemeRepository = ThemeRepository(themeDataStore)
}
