package com.dmdbrands.gurus.weight.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.settings.manager.IDataSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.INotificationSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IProfileSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IScaleSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IUnitSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val profileSettingsManager: IProfileSettingsManager,
  private val unitSettingsManager: IUnitSettingsManager,
  private val notificationSettingsManager: INotificationSettingsManager,
  private val scaleSettingsManager: IScaleSettingsManager,
  private val dataSettingsManager: IDataSettingsManager,
) : BaseIntentViewModel<SettingsState, SettingsIntent>(
  SettingsReducer(),
) {
  companion object {
    private const val TAG = "SettingsViewModel"
  }

  override fun provideInitialState(): SettingsState = SettingsState()

  init {
    profileSettingsManager.observeUserProfile(viewModelScope, ::dispatchIntent)
    profileSettingsManager.showAccountSwitchInfoModal(viewModelScope)
    dataSettingsManager.loadCurrentThemeMode(viewModelScope, ::dispatchIntent)
    scaleSettingsManager.loadMacAddressSettings(viewModelScope, ::dispatchIntent)
    notificationSettingsManager.initFeedNotificationListener(viewModelScope, ::dispatchIntent)
    dataSettingsManager.observeExportEnabled(viewModelScope, ::dispatchIntent)
  }

  override fun handleIntent(intent: SettingsIntent) {
    super.handleIntent(intent)

    when (intent) {
      SettingsIntent.OpenAddScales -> {
        navigateTo(AppRoute.AccountSettings.AddEditScales)
      }

      SettingsIntent.OpenHelp -> {
        navigateTo(AppRoute.AccountSettings.HelpScreen)
      }

      SettingsIntent.ExportData -> {
        dataSettingsManager.onExportDataClick(viewModelScope)
      }

      SettingsIntent.Logout -> {
        dataSettingsManager.onLogOutClick(
          scope = viewModelScope,
          stateProvider = ::currentState,
          isLogoutAll = false,
        )
      }

      SettingsIntent.LogoutAllAccounts -> {
        dataSettingsManager.onLogOutClick(
          scope = viewModelScope,
          stateProvider = ::currentState,
          isLogoutAll = true,
        )
      }

      SettingsIntent.SwitchAccount -> {
        onSwitchAccountClick()
      }

      SettingsIntent.ShowBiologicalSexModal -> {
        profileSettingsManager.onBiologicalSexClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowActivityLevelModal -> {
        profileSettingsManager.onActivityLevelClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowUnitTypeModal -> {
        unitSettingsManager.onUnitTypeClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowNotificationsModal -> {
        notificationSettingsManager.onNotificationsClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowHeightModal -> {
        profileSettingsManager.onHeightClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowWeightlessModal -> {
        profileSettingsManager.onShowWeightlessModal(viewModelScope, ::currentState)
      }

      SettingsIntent.goalSettingModal -> {
        profileSettingsManager.onGoalSettingClick(viewModelScope)
      }

      SettingsIntent.ShowAppearanceModal -> {
        dataSettingsManager.onAppearanceClick(viewModelScope, ::currentState, ::dispatchIntent)
      }

      is SettingsIntent.ToggleStreak -> {
        profileSettingsManager.onStreakUpdate(viewModelScope, ::currentState, intent.checked)
      }

      SettingsIntent.ConfirmDeleteAccount -> {
        dataSettingsManager.onConfirmDeleteAccount(::dispatchIntent)
      }

      SettingsIntent.DeleteAccount -> {
        dataSettingsManager.onDeleteAccount(viewModelScope, ::currentState)
      }

      SettingsIntent.OpenPrivacyPolicy -> {
        openInAppBrowser(AppConfig.AppUrls.PrivacyPolicy)
      }

      SettingsIntent.OpenTermsOfService -> {
        openInAppBrowser(AppConfig.AppUrls.TermsOfService)
      }

      SettingsIntent.OpenGreaterGoodsWebsite -> {
        openInAppBrowser(AppConfig.AppUrls.GreaterGoodsWebsite)
      }

      SettingsIntent.ShowMacAddressFilterModal -> {
        scaleSettingsManager.onMacAddressFilterClick(viewModelScope, ::currentState, ::dispatchIntent)
      }

      else -> {}
    }
  }

  fun onSwitchAccountClick() {
    navigateTo(AppRoute.AccountSettings.MyAccounts)
    AppLog.d(TAG, "Navigating to My Accounts")
  }

  fun onAccountSwitchInfoDismiss() {
    profileSettingsManager.dismissAccountSwitchInfoModal()
  }

  fun getWeightlessDisplayText(): String = profileSettingsManager.getWeightlessDisplayText(state.value)

  private fun currentState(): SettingsState = state.value

  private fun dispatchIntent(intent: SettingsIntent) {
    handleIntent(intent)
  }

  private fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }
}
