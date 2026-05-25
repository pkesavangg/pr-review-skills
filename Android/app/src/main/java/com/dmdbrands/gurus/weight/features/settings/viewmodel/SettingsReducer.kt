package com.dmdbrands.gurus.weight.features.settings.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import androidx.compose.runtime.Stable

/**
 * UI state for the settings feature, holding loading state and errors.
 *
 * @property isLoading Whether data is currently loading.
 * @property errorMessage Error message if any error occurs.
 */
@Stable
data class SettingsState(
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val account: Account? = null,
  val hasMultipleAccounts: Boolean = false,
  val currentThemeMode: String = "System Settings",
  val selectedMacAddress: String = "All",
  val enableTestingFeatures: Boolean = false,
  val unreadFeedCount: Int = 0,
  val showUnreadFeedIndication: Boolean = false,
  val isExportEnabled: Boolean = false,
  val currentDefaultGraphRange: GraphSegment = GraphSegment.DEFAULT,
) : IReducer.State {

  /**
   * Computed property that returns the current notification status as a formatted string.
   * This is reactive and will update whenever the account state changes.
   */
  val currentNotificationStatus: String
    get() = when {
      account?.shouldSendEntryNotifications == true && account.shouldSendWeightInEntryNotifications == true -> "On w/ Weight"
      account?.shouldSendEntryNotifications == true -> "On"
      else -> "Off"
    }
}

/**
 * Intent for settings actions, such as loading and updating settings.
 */
sealed interface SettingsIntent : IReducer.Intent {
  object LoadSettings : SettingsIntent
  object ExportData : SettingsIntent

  data class SetError(
    val message: String,
  ) : SettingsIntent

  object ClearError : SettingsIntent
  object OpenAddScales : SettingsIntent
  object Logout : SettingsIntent
  object LogoutAllAccounts : SettingsIntent
  object SwitchAccount : SettingsIntent

  data class UpdateAccount(
    val account: Account?,
    val hasMultipleAccounts: Boolean = false
  ) : SettingsIntent

  // URL Opening Intents
  object OpenPrivacyPolicy : SettingsIntent
  object OpenTermsOfService : SettingsIntent
  object OpenGreaterGoodsWebsite : SettingsIntent
  object OpenHelp : SettingsIntent

  // Modal Selection Intents
  object ShowBiologicalSexModal : SettingsIntent
  object ShowActivityLevelModal : SettingsIntent
  object ShowUnitTypeModal : SettingsIntent
  object ShowNotificationsModal : SettingsIntent
  object ShowHeightModal : SettingsIntent
  object ShowWeightlessModal : SettingsIntent
  data class ToggleStreak(val checked: Boolean) : SettingsIntent
  object goalSettingModal : SettingsIntent
  object ShowAppearanceModal : SettingsIntent
  object ShowDefaultGraphRangeModal : SettingsIntent
  data class UpdateThemeMode(val themeMode: String) : SettingsIntent
  data class UpdateDefaultGraphRange(val range: GraphSegment) : SettingsIntent
  // MAC Address Filter Intents (for 0412 scale testing)
  object ShowMacAddressFilterModal : SettingsIntent
  data class UpdateSelectedMacAddress(val macAddress: String) : SettingsIntent
  data class UpdateTestingFeatures(val enabled: Boolean) : SettingsIntent
  data class SetUnreadFeedCount(val count: Int) : SettingsIntent
  data class SetShowUnreadFeedIndication(val show: Boolean) : SettingsIntent
  data class SetExportEnabled(val enabled: Boolean) : SettingsIntent
  object DeleteAccount : SettingsIntent
  object ConfirmDeleteAccount : SettingsIntent
  object TriggerTestCrash : SettingsIntent
  object TriggerTestNonFatal : SettingsIntent
}

/**
 * Reducer for the settings state, handling intents to update settings and errors.
 */
class SettingsReducer : IReducer<SettingsState, SettingsIntent> {
  override fun reduce(
    state: SettingsState,
    intent: SettingsIntent,
  ): SettingsState? =
    when (intent) {
      is SettingsIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
      SettingsIntent.ClearError -> state.copy(errorMessage = null)
      SettingsIntent.LoadSettings -> state.copy(isLoading = true)
      is SettingsIntent.UpdateAccount -> state.copy(
        account = intent.account,
        hasMultipleAccounts = intent.hasMultipleAccounts,
      )

      is SettingsIntent.UpdateThemeMode -> state.copy(currentThemeMode = intent.themeMode)
      is SettingsIntent.UpdateDefaultGraphRange -> state.copy(currentDefaultGraphRange = intent.range)
      is SettingsIntent.UpdateSelectedMacAddress -> state.copy(selectedMacAddress = intent.macAddress)
      is SettingsIntent.UpdateTestingFeatures -> state.copy(enableTestingFeatures = intent.enabled)
      is SettingsIntent.SetUnreadFeedCount -> state.copy(unreadFeedCount = intent.count)
      is SettingsIntent.SetShowUnreadFeedIndication -> state.copy(showUnreadFeedIndication = intent.show)
      is SettingsIntent.SetExportEnabled -> state.copy(isExportEnabled = intent.enabled)
      else -> null
      // Add more intent handling as needed
    }
}
