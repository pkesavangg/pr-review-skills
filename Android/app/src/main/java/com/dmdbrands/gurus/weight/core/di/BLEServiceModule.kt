package com.dmdbrands.gurus.weight.core.di

import com.greatergoods.blewrapper.GGBLEService
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class BLEServiceModule {

  @Provides
  @Singleton
  fun provideBLEService(): GGBLEService {
    return GGBLEService()
  }

  @Provides
  @Singleton
  fun providePermissionService(bleService: GGBLEService): GGPermissionService {
    return GGPermissionService(bleService)
  }

  @Provides
  @Singleton
  fun provideDeviceService(bleService: GGBLEService): GGDeviceService {
    return GGDeviceService(bleService)
  }
}
