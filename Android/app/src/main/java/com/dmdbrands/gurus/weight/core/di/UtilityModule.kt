package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.shared.utilities.AppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.CustomTabManager
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
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
        @ApplicationContext context: Context,
    ): IAppReviewManager = AppReviewManager(context)

    @Provides
    fun provideCustomTabManager(
        @ApplicationContext context: Context,
    ): ICustomTabManager = CustomTabManager(context)
}
