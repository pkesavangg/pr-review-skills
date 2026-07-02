package com.dmdbrands.gurus.weight.core.di

import android.content.Context
import com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver
import com.dmdbrands.gurus.weight.core.power.utility.PowerSaveModeObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing power-related observers.
 */
@Module
@InstallIn(SingletonComponent::class)
object PowerModule {

  @Provides
  @Singleton
  fun providePowerSaveModeObserver(
    @ApplicationContext context: Context,
  ): IPowerSaveModeObserver = PowerSaveModeObserver(context)
}
