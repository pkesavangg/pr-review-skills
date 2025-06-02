package com.greatergoods.meapp.di

import android.content.Context
import com.greatergoods.meapp.data.repository.ThemeRepository
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    @Provides
    @Singleton
    fun provideThemeDataStore(@ApplicationContext context: Context): ThemeDataStore =
        ThemeDataStore(context)

    @Provides
    @Singleton
    fun provideThemeRepository(themeDataStore: ThemeDataStore): ThemeRepository =
        ThemeRepository(themeDataStore)
}
