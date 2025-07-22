package com.greatergoods.meapp.core.di

import com.greatergoods.meapp.core.service.HealthConnectService
import com.greatergoods.meapp.core.service.IAppNavigationService
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.domain.services.IHealthConnectService
import com.greatergoods.meapp.domain.services.IIntegrationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Hilt module for providing Health Connect service dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {

    /**
     * Binds the Health Connect service implementation to its interface.
     */
    @Provides
    @Singleton
     fun provideHealthConnectService(
      @ApplicationContext context: Context,
      healthConnectRepository: IHealthConnectRepository,
      accountRepository: IAccountRepository,
      integrationRepository: IIntegrationRepository,
      dialogQueueService: IDialogQueueService,
      appNavigationService: IAppNavigationService,
      entryRepository: IEntryRepository,
      integrationService: IIntegrationService,
      entryService: IEntryService
    ): IHealthConnectService = HealthConnectService(
      context,
      healthConnectRepository,
      accountRepository,
      dialogQueueService,
      appNavigationService,
      entryRepository,
      integrationRepository,
      integrationService,
      entryService
    )
}
