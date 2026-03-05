package com.dmdbrands.gurus.weight.core.di

import com.dmdbrands.gurus.weight.features.settings.manager.DataSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IDataSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.INotificationSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IProfileSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IScaleSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IUnitSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.NotificationSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.ProfileSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.ScaleSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.UnitSettingsManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsManagerModule {
  @Binds
  abstract fun bindProfileSettingsManager(
    impl: ProfileSettingsManager,
  ): IProfileSettingsManager

  @Binds
  abstract fun bindUnitSettingsManager(
    impl: UnitSettingsManager,
  ): IUnitSettingsManager

  @Binds
  abstract fun bindNotificationSettingsManager(
    impl: NotificationSettingsManager,
  ): INotificationSettingsManager

  @Binds
  abstract fun bindScaleSettingsManager(
    impl: ScaleSettingsManager,
  ): IScaleSettingsManager

  @Binds
  abstract fun bindDataSettingsManager(
    impl: DataSettingsManager,
  ): IDataSettingsManager
}
