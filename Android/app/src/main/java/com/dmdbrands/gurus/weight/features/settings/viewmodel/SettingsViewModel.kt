package com.dmdbrands.gurus.weight.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.BuildConfig
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.settings.manager.IDataSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.INotificationSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IProfileSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IScaleSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IUnitSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
  private val crashReportingService: ICrashReportingService,
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

  override fun onDependenciesReady() {
    observeProductSelection()
  }

  override fun handleIntent(intent: SettingsIntent) {
    super.handleIntent(intent)

    when (intent) {
      SettingsIntent.OpenMyDevices -> {
        navigateTo(AppRoute.AccountSettings.MyDevices)
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

      SettingsIntent.ShowActivityLevelModal -> {
        profileSettingsManager.onActivityLevelClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowUnitTypeModal -> {
        unitSettingsManager.onUnitTypeClick(viewModelScope, ::currentState)
      }

      SettingsIntent.ShowNotificationsModal -> {
        notificationSettingsManager.onNotificationsClick(viewModelScope, ::currentState)
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

      SettingsIntent.TriggerTestCrash -> {
        if (BuildConfig.DEBUG) {
          AppLog.w(TAG, "Triggering test crash for Crashlytics verification")
          throw RuntimeException("Crashlytics test crash")
        }
      }

      SettingsIntent.TriggerTestNonFatal -> {
        if (BuildConfig.DEBUG) {
          val exception = RuntimeException("Crashlytics test non-fatal exception")
          crashReportingService.recordException(exception, "test_non_fatal")
          AppLog.d(TAG, "Non-fatal test exception recorded to Crashlytics")
          dialogQueueService.showToast(
            com.dmdbrands.gurus.weight.features.common.model.Toast(
              title = null,
              message = "Non-fatal exception recorded",
              action = null,
            ),
          )
        }
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

  private fun observeProductSelection() {
    viewModelScope.launch {
      productSelectionManager.selectedProduct
        .map { it.productType == ProductType.BABY }
        .distinctUntilChanged()
        .collect { isBaby -> dispatchIntent(SettingsIntent.SetIsBabyProduct(isBaby)) }
    }
  }

  private fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }
}
