package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.service.AppEventService
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.core.service.pushNotification.NotificationManager as GGNotificationManager
import com.greatergoods.meapp.core.service.pushNotification.NotificationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Dagger Hilt module for providing core service dependencies such as event and notification managers.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    /**
     * Provides a singleton instance of [IAppEventService].
     * @return [AppEventService] instance.
     */
    @Provides
    @Singleton
    fun provideAppEventService(): IAppEventService = AppEventService()

    /**
     * Provides a singleton instance of [NotificationManager] for notification operations.
     * @param context The application context.
     * @param notificationService The notification service dependency.
     * @return [NotificationManager] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        notificationService: NotificationService,
    ): GGNotificationManager = GGNotificationManager(context, notificationService)
}
