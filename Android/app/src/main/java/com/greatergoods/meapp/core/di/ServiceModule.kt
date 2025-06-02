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

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideAppEventService(): IAppEventService = AppEventService()

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        notificationService: NotificationService,
    ): GGNotificationManager = GGNotificationManager(context, notificationService)
}
