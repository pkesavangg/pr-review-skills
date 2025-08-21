package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.HealthConnectService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
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
    entryService: IEntryService,
    connectivityObserver: IConnectivityObserver,
  ): IHealthConnectService = HealthConnectService(
    context,
    healthConnectRepository,
    accountRepository,
    connectivityObserver,
    dialogQueueService,
    appNavigationService,
    entryRepository,
    integrationRepository,
    entryService,
  )
}
