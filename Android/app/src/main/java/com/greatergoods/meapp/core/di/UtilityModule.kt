package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.utils.AppReviewManager
import com.greatergoods.meapp.utils.IAppReviewManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object ReviewManagerModule {

    @Provides
    fun provideReviewManager(
        @ApplicationContext context: Context
    ): IAppReviewManager {
        return AppReviewManager(context)
    }
}

