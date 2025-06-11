package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.utils.AppReviewManager
import com.greatergoods.meapp.utils.IAppReviewManager
import com.greatergoods.meapp.utils.browser.CustomTabManager
import com.greatergoods.meapp.utils.browser.ICustomTabManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object UtilityModule {

    @Provides
    fun provideReviewManager(
        @ApplicationContext context: Context
    ): IAppReviewManager {
        return AppReviewManager(context)
    }

    @Provides
    fun provideCustomTabManager(
        @ApplicationContext context: Context
    ): ICustomTabManager {
        return CustomTabManager(context)
    }
}

