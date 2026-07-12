package com.dmdbrands.gurus.weight.features.settings.viewmodel

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

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
  val isBabyProduct: Boolean = false,
  val hasWeightScale: Boolean = false,
  // Local-only My Kids unit preference. Defaults to LB_OZ — the canonical
  // baby scale unit. Backend has no per-baby unit; this is loaded from
  // [UserDataStore.babyWeightUnitForCurrentAccountFlow].
  val babyWeightUnit: WeightUnit = WeightUnit.LB_OZ,
  val hasBabyScaleDevice: Boolean = false,
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

  /**
   * Whether the "My Kids" Settings row is enabled. Always enabled now: any account may open
   * My Kids to add a baby — creating a baby (or pairing a baby scale) auto-adds "baby" to
   * [Account.productTypes] server-side, so a weight- or BP-only user must be able to get in.
   * This is intentionally separate from [isMyKidsUnitEnabled]: reachability of the row is not
   * the same as being allowed to change the baby measurement unit.
   */
  val isMyKidsEnabled: Boolean
    get() = true

  /**
   * Whether the "My Kids" section of the Unit Type modal is editable. Gated on actual baby
   * product ownership — the account has engaged a baby scale ([hasBabyScaleDevice]) or its
   * [Account.productTypes] carries "baby" (additive per MOB-686 Rule A; stays on after the
   * device is removed). Unlike the always-on My Kids row, the baby unit can only be changed
   * once the account actually owns the baby product, so it is not enabled globally.
   */
  val isMyKidsUnitEnabled: Boolean
    get() = hasBabyScaleDevice ||
      account?.productTypes?.contains(ProductType.BABY.apiValue) == true

  /**
   * Whether the "My Weight" Unit Type section is editable — driven by product ownership
   * ([Account.productTypes] carries "weight"), which is auto-added when a weight scale is
   * paired and is additive (stays after the device is removed). Deliberately NOT keyed off
   * the [hasWeightScale] device flag: that reads DeviceService._pairedScales, where a baby
   * scale can surface under a weight-scale device type and falsely enable this section for a
   * baby/BP-only account. When false the section is shown locked at the default unit. (MOB-1175)
   */
  val isMyWeightEnabled: Boolean
    get() = account?.productTypes?.contains(ProductType.MY_WEIGHT.apiValue) == true

  /**
   * Whether the "Unit Type" row shows in the App section. Always shown now (MOB-1175):
   * BP-only accounts previously hid it, but per the finalised visibility rules the row
   * stays visible with both sections locked to the default unit and an unlock message,
   * nudging users to add the relevant weight/baby scale.
   */
  val showUnitType: Boolean
    get() = true

  /**
   * Whether the "Integrations" row shows in the Account section. Hidden for baby-scale
   * only accounts (no third-party health integrations apply to baby data). Shown when
   * the account owns a weight or blood-pressure product. Mirrors the WG/BH-show, SB-hide
   * design.
   */
  val showIntegrations: Boolean
    get() = account?.productTypes?.let {
      it.contains(ProductType.MY_WEIGHT.apiValue) || it.contains(ProductType.BLOOD_PRESSURE.apiValue)
    } ?: true
}

/**
 * Intent for settings actions, such as loading and updating settings.
 */
sealed interface SettingsIntent : IReducer.Intent {
  object LoadSettings : SettingsIntent

  data class SetError(
    val message: String,
  ) : SettingsIntent

  object ClearError : SettingsIntent
  object OpenMyDevices : SettingsIntent
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
  object ShowActivityLevelModal : SettingsIntent
  object ShowUnitTypeModal : SettingsIntent
  object ShowNotificationsModal : SettingsIntent
  object NavigateToWeightless : SettingsIntent
  data class ToggleStreak(val checked: Boolean) : SettingsIntent
  object goalSettingModal : SettingsIntent
  object ShowAppearanceModal : SettingsIntent
  data class UpdateThemeMode(val themeMode: String) : SettingsIntent
  // MAC Address Filter Intents (for 0412 scale testing)
  object ShowMacAddressFilterModal : SettingsIntent
  data class UpdateSelectedMacAddress(val macAddress: String) : SettingsIntent
  data class UpdateTestingFeatures(val enabled: Boolean) : SettingsIntent
  data class SetUnreadFeedCount(val count: Int) : SettingsIntent
  data class SetShowUnreadFeedIndication(val show: Boolean) : SettingsIntent
  data class SetIsBabyProduct(val isBabyProduct: Boolean) : SettingsIntent
  data class SetHasWeightScale(val hasWeightScale: Boolean) : SettingsIntent
  data class SetBabyWeightUnit(val unit: WeightUnit) : SettingsIntent
  data class SetHasBabyScaleDevice(val hasBabyScaleDevice: Boolean) : SettingsIntent
  object DeleteAccount : SettingsIntent
  object ConfirmDeleteAccount : SettingsIntent
  object TriggerTestCrash : SettingsIntent
  object TriggerTestNonFatal : SettingsIntent
  object OpenA3MonitorSetup : SettingsIntent
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
      is SettingsIntent.UpdateSelectedMacAddress -> state.copy(selectedMacAddress = intent.macAddress)
      is SettingsIntent.UpdateTestingFeatures -> state.copy(enableTestingFeatures = intent.enabled)
      is SettingsIntent.SetUnreadFeedCount -> state.copy(unreadFeedCount = intent.count)
      is SettingsIntent.SetShowUnreadFeedIndication -> state.copy(showUnreadFeedIndication = intent.show)
      is SettingsIntent.SetIsBabyProduct -> state.copy(isBabyProduct = intent.isBabyProduct)
      is SettingsIntent.SetHasWeightScale -> state.copy(hasWeightScale = intent.hasWeightScale)
      is SettingsIntent.SetBabyWeightUnit -> state.copy(babyWeightUnit = intent.unit)
      is SettingsIntent.SetHasBabyScaleDevice -> state.copy(hasBabyScaleDevice = intent.hasBabyScaleDevice)
      else -> null
      // Add more intent handling as needed
    }
}
