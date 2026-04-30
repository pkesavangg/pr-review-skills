package com.greatergoods.ggInAppMessaging.core.di

import com.greatergoods.ggInAppMessaging.core.service.LinkService
import com.greatergoods.ggInAppMessaging.domain.services.ILinkService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object IamProviderModule {
  /**
   * Provides the Link service implementation.
   * Handles opening external links, custom tabs, and other link-related functionality.
   */
  @Provides
  @Singleton
  fun provideLinkService(
    @ApplicationContext context: Context,
  ): ILinkService = LinkService(context)
}
