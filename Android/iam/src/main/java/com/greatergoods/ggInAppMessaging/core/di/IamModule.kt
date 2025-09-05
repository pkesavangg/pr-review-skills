package com.greatergoods.ggInAppMessaging.core.di

import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for IAM service bindings
 * Provides interface to implementation bindings
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IamModule {

  /**
   * Binds IInAppMessagingService interface to GGInAppMessagingService implementation
   */
  @Binds
  @Singleton
  abstract fun bindInAppMessagingService(
    impl: GGInAppMessagingService
  ): IInAppMessagingService
}
