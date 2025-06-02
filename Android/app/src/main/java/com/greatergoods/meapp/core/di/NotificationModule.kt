package com.greatergoods.meapp.core.di

import com.example.notification.NotificationHandler
import com.greatergoods.notification.NotificationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Dagger Hilt module for providing notification-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    /**
     * Provides a singleton instance of [NotificationService].
     * @param notificationHandler The handler for notification operations.
     * @return [NotificationService] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationService(
        notificationHandler: NotificationHandler,
    ): NotificationService {
        return NotificationService(notificationHandler)
    }

    /**
     * Provides a singleton instance of [NotificationHandler].
     * @param context The application context.
     * @return [NotificationHandler] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationHandler(
        @ApplicationContext context: Context,
    ): NotificationHandler {
        return NotificationHandler(context)
    }
}
