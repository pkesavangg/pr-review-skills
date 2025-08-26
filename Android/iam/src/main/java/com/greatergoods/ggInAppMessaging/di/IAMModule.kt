package com.greatergoods.ggInAppMessaging.di

import com.greatergoods.ggInAppMessaging.core.service.FeedStorageService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.GGInAppMessagingConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for GG In-App Messaging
 * Provides all necessary services and configurations
 */
@Module
@InstallIn(SingletonComponent::class)
object IAMModule {

    /**
     * Provide GG In-App Messaging configuration
     */
    @Provides
    @Singleton
    fun provideGGInAppMessagingConfig(): GGInAppMessagingConfig {
        return GGInAppMessagingConfig(
            baseNavigationPath = "" // Configure based on your app's navigation structure
        )
    }

    /**
     * Provide Feed Storage Service
     */
    @Provides
    @Singleton
    fun provideFeedStorageService(): FeedStorageService {
        return FeedStorageService()
    }

    /**
     * Provide GG In-App Messaging Service
     */
    @Provides
    @Singleton
    fun provideGGInAppMessagingService(
        feedStorageService: FeedStorageService
    ): GGInAppMessagingService {
        return GGInAppMessagingService(feedStorageService)
    }
}
