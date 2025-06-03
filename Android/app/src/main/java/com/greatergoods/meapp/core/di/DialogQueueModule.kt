package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.features.common.service.DialogQueueService
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing dialog queue service and viewmodel as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DialogQueueModule {
    @Provides
    @Singleton
    fun provideDialogQueueService(): DialogQueueService = DialogQueueService()

}
