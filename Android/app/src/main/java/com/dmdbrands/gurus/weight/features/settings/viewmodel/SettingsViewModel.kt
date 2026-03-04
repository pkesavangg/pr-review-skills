package com.dmdbrands.gurus.weight.features.settings.viewmodel

import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.enums.ActivityLevel
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.Gender
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.INotificationService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.ScaleProfileConstants
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.strings.SettingsScreenStrings
import com.dmdbrands.gurus.weight.features.weightless.helper.WeightlessHelper
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import javax.inject.Inject
/**
 * ViewModel for the settings feature, managing state and handling settings intents.
 *
 * (Add service dependencies as needed.)
 */
// TODO: MyAccountsViewModel will be implemented in a new file under 'viewmodel' if needed.
// MyAccountsScreen will use AccountService.loggedInAccountsFlow for account data.
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val exportService: IExportService,
  private val bodyCompositionService: IBodyCompositionService,
  private val userDataStore: UserDataStore,
  private val notificationService: INotificationService,
  private val userSettingsService: IUserSettingsService,
  private val healthConnectService: IHealthConnectService,
  private val bluetoothPreferencesService: BluetoothPreferencesService,
  private val feedService: IFeedService,
  private val ggDeviceService: GGDeviceService
) : BaseIntentViewModel<SettingsState, SettingsIntent>(
  SettingsReducer(),
) {
  override fun provideInitialState(): SettingsState = SettingsState()

  companion object {
    private const val TAG = "SettingsViewModel"
  }

  init {
    getUserProfile()
    showAccountSwitchInfoModal()
    loadCurrentThemeMode()
    loadMacAddressSettings()
    initFeedNotificationListener()
    checkExportEnabled()
  }

  fun getUserProfile() {
    viewModelScope.launch {
      accountService.loggedInAccountsFlow.collect {
        val account = accountService.getCurrentAccount()
        val hasMultipleAccounts = it.size > 1
        handleIntent(SettingsIntent.UpdateAccount(account, hasMultipleAccounts))
      }
    }
  }

  /**
   * Loads the current theme mode from the data store and updates the state.
   */
  private fun loadCurrentThemeMode() {
    viewModelScope.launch {
      userDataStore.currentThemeModeFlow.collect { themeMode ->
        val displayString = when (themeMode) {
          com.dmdbrands.gurus.weight.proto.ThemeMode.LIGHT -> RadioGroupModalStrings.Appearance.Light
          com.dmdbrands.gurus.weight.proto.ThemeMode.DARK -> RadioGroupModalStrings.Appearance.Dark
          else -> RadioGroupModalStrings.Appearance.System
        }
        handleIntent(SettingsIntent.UpdateThemeMode(displayString))
      }
    }
  }

  /**
   * Initializes the feed notification listener for settings screen
   * Listens to feed notification changes and updates unread count and indicator visibility
   * Observes both feedsChanged and notificationBadgeUpdated flows for real-time updates
   */
  private fun initFeedNotificationListener() {
    // Observe feed items changes to update unread count
    viewModelScope.launch {
      try {
        feedService.feedsChanged.collect {
          AppLog.d(TAG, "Feed items changed, updating unread count")
          updateUnreadFeedCount()
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing feedsChanged flow", e.toString())
      }
    }

    // Observe notification badge updates for immediate indicator visibility updates
    viewModelScope.launch {
      try {
        feedService.notificationBadgeUpdated.collect { shouldShow ->
          AppLog.d(TAG, "Notification badge updated: $shouldShow")
          val count = feedService.getUnreadFeedCount()
          handleIntent(SettingsIntent.SetUnreadFeedCount(count))
          handleIntent(SettingsIntent.SetShowUnreadFeedIndication(shouldShow))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing notificationBadgeUpdated flow", e.toString())
      }
    }

    // Initial update on init
    viewModelScope.launch {
      try {
        updateUnreadFeedCount()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error in initial feed notification update", e.toString())
      }
    }
  }

  /**
   * Updates the unread feed count and indicator visibility for settings screen
   */
  private suspend fun updateUnreadFeedCount() {
    try {
      val count = feedService.getUnreadFeedCount()
      val feedSettings = feedService.getFeedSettings()
      val shouldShow = count > 0 && (feedSettings?.showNotificationBadge ?: true)
      AppLog.d(TAG, "Updating unread feed count: $count, show indicator: $shouldShow")
      handleIntent(SettingsIntent.SetUnreadFeedCount(count))
      handleIntent(SettingsIntent.SetShowUnreadFeedIndication(shouldShow))
      AppLog.d(TAG, "Updated unread feed count: $count, show indicator: $shouldShow")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update unread feed count", e.toString())
    }
  }

  override fun handleIntent(intent: SettingsIntent) {
    super.handleIntent(intent)
    when (intent) {
      is SettingsIntent.UpdateAccount -> {
        val account = intent.account
        _state.value = _state.value.copy(account = account)
      }

      is SettingsIntent.OpenAddScales -> {
        navigateTo(AppRoute.AccountSettings.AddEditScales)
      }

      is SettingsIntent.OpenHelp -> {
        navigateTo(AppRoute.AccountSettings.HelpScreen)
      }

      is SettingsIntent.ExportData -> {
        onExportDataClick()
      }

      is SettingsIntent.Logout -> {
        onLogOutClick()
      }

      is SettingsIntent.LogoutAllAccounts -> {
        onLogOutClick(true)
      }

      is SettingsIntent.SwitchAccount -> {
        onSwitchAccountClick()
      }

      is SettingsIntent.ShowBiologicalSexModal -> {
        onBiologicalSexClick()
      }

      is SettingsIntent.ShowActivityLevelModal -> {
        onActivityLevelClick()
      }

      is SettingsIntent.ShowUnitTypeModal -> {
        onUnitTypeClick()
      }

      is SettingsIntent.ShowNotificationsModal -> {
        onNotificationsClick()
      }

      is SettingsIntent.ShowHeightModal -> {
        onHeightClick()
      }

      is SettingsIntent.ShowWeightlessModal -> {
        onWeightlessClick()
      }

      is SettingsIntent.goalSettingModal -> {
        onGoalSettingClick()
      }

      is SettingsIntent.ShowAppearanceModal -> {
        onAppearanceClick()
      }

      is SettingsIntent.ToggleStreak -> {
        onStreakUpdate(intent.checked)
      }

      is SettingsIntent.ConfirmDeleteAccount -> onConfirmDeleteAccount()

      is SettingsIntent.DeleteAccount -> {
        onDeleteAccount()
      }

      is SettingsIntent.OpenPrivacyPolicy -> {
        openInAppBrowser(AppConfig.AppUrls.PrivacyPolicy)
      }

      is SettingsIntent.OpenTermsOfService -> {
        openInAppBrowser(AppConfig.AppUrls.TermsOfService)
      }

      is SettingsIntent.OpenGreaterGoodsWebsite -> {
        openInAppBrowser(AppConfig.AppUrls.GreaterGoodsWebsite)
      }

      is SettingsIntent.ShowMacAddressFilterModal -> {
        onMacAddressFilterClick()
      }

      else -> {}
    }
  }

  /**
   * Handles the delete account action: shows loader, calls repo, handles error, navigates on success.
   */
  private fun onDeleteAccount() {
    dialogQueueService.showLoader(SettingsScreenStrings.DeletingAccount)
    viewModelScope.launch {
      try {

        val account = state.value.account
        if (account != null) {
          accountService.deleteAccount(account.id, account.isActiveAccount)
          healthConnectService.clearHealthConnect()
          navigationService.emitAuthEvent(AuthState.AccountDeleted(account.isActiveAccount))
        }
        dialogQueueService.dismissLoader()
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
      } finally {
        dialogQueueService.clear()
      }
    }
  }

  private fun onConfirmDeleteAccount() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = SettingsScreenStrings.DeleteAccountDialog.Title,
        message = SettingsScreenStrings.DeleteAccountDialog.Body,
        primaryActionType = ButtonType.ErrorText,
        confirmText = SettingsScreenStrings.DeleteAccountDialog.Confirm,
        cancelText = SettingsScreenStrings.DeleteAccountDialog.Cancel,
        onConfirm = { handleIntent(SettingsIntent.DeleteAccount) },
        onCancel = {},
      ),
    )
  }

  fun onExportDataClick() {
    AppLog.d(TAG, "Export data clicked")

    // Show confirmation dialog
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ExportStrings.ExportDialogTitle,
        message = ExportStrings.ExportDialogMessage,
        confirmText = ExportStrings.SendButton,
        cancelText = ExportStrings.CancelButton,
        onConfirm = {
          performExport()
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          AppLog.d(TAG, "User cancelled export")
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Performs the actual export operation with loading and error handling.
   */
  private fun performExport() {
    AppLog.i("TAG", ExportStrings.ExportStarted)

    // Show loading spinner
    dialogQueueService.showLoader(
      message = ExportStrings.LoaderMessage,
    )

    viewModelScope.launch {
      try {
        exportService.exportCsvWithPrompt()
        AppLog.i(TAG, ExportStrings.ExportCompleted)
      } catch (e: HttpException) {
        AppLog.e(TAG, ExportStrings.ExportFailed, e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Shows the biological sex selection modal.
   */
  private fun showBiologicalSexModal() {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.BiologicalSex,
      options =
        listOf(
          RadioButtonOption(Gender.MALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Male),
          RadioButtonOption(Gender.FEMALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Female),
        ),
      selectedItem = state.value.account?.gender,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedSex ->
        AppLog.d(TAG, "Biological sex modal onConfirm called with: $selectedSex")
        selectedSex?.let { gender ->
          onBiologicalSexUpdate(gender)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Biological sex selection cancelled")
      },
    )
  }

  /**
   * Updates the biological sex via the offline handler service.
   * Follows the same pattern as Angular onSexSelectionChange method.
   */
  private fun onBiologicalSexUpdate(gender: String) {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for biological sex update")
      return
    }
    if (currentAccount.gender == gender) {
      AppLog.d(TAG, "Gender is already set to $gender, no update needed")
      return
    }

    // Show loading dialog
    dialogQueueService.showLoader("Loading...")
    viewModelScope.launch {
      try {
        val updatedCurrentProfile =
          ProfileUpdateRequest(
            id = currentAccount.id,
            firstName = currentAccount.firstName,
            lastName = currentAccount.lastName,
            email = currentAccount.email,
            dob = currentAccount.dob,
            gender = gender,
            zipcode = currentAccount.zipcode,
          )
        // IMPORTANT: Update scale first, then API. If we call the API first, activeAccountFlow
        // emits and AppViewModel.startScan() runs ggDeviceService.updateProfile(), which races
        // with our updateR4Profile callback so the loader never dismisses (see AppViewModel.startScan).
        val updatedProfile = currentAccount.toGGBTUserProfile().copy(sex = gender)
        val scaleResult = updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        when (scaleResult) {
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            dialogQueueService.enqueue(
              DialogModel.Alert(
                title = AppPopupStrings.R4ProfileUpdatePending.Title,
                message = AppPopupStrings.R4ProfileUpdatePending.Message,
                onDismiss = { dialogQueueService.dismissCurrent() },
              ),
            )
          }

          GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }

          else -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }
        }
        accountService.updateProfile(updatedCurrentProfile, isFromProfile = false, showToast = false)

        AppLog.i(TAG, "Successfully updated biological sex")
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
        AppLog.e(TAG, "Error updating biological sex", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private suspend fun updateR4Profile(profile: GGBTUserProfile): GGUserActionResponseType {
    val result = CompletableDeferred<GGUserActionResponseType>()
    try {
      ggDeviceService.updateProfile(
        profile,
      ) { responseType ->
        result.complete(responseType)
      }
    } catch (e: Exception) {
      AppLog.d(TAG, "updateR4Profile - Error updating profile to scale: ${e.message}")
      result.complete(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
    }
    return withTimeoutOrNull(ScaleProfileConstants.SCALE_PROFILE_UPDATE_TIMEOUT_MS) { result.await() }
      ?: run {
        AppLog.d(TAG, "updateR4Profile - Timeout or no callback from scale; dismissing loader")
        GGUserActionResponseType.EXCEPTION_ENCOUNTERED
      }
  }

  /**
   * Shows the activity level selection modal.
   */
  private fun showActivityLevelModal() {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.ActivityLevel,
      options =
        listOf(
          RadioButtonOption(
            ActivityLevel.NORMAL.name.lowercase(),
            RadioGroupModalStrings.ActivityLevel.Normal,
          ),
          RadioButtonOption(
            ActivityLevel.ATHLETE.name.lowercase(),
            RadioGroupModalStrings.ActivityLevel.Athlete,
          ),
        ),
      selectedItem = state.value.account?.activityLevel,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedActivityLevel ->
        selectedActivityLevel?.let { activityLevel ->
          onActivityLevelUpdate(activityLevel)
        }
      },
      onCancel = {
      },
    )
  }

  /**
   * Updates the activity level via the body composition service.
   * Follows the same pattern as Angular onActivitySelectionChange method.
   */
  private fun onActivityLevelUpdate(activityLevel: String) {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for activity level update")
      return
    }
    if (currentAccount.activityLevel == activityLevel) {
      AppLog.d(TAG, "Activity level is already set to $activityLevel, no update needed")
      return
    }

    dialogQueueService.showLoader("Loading...")
    viewModelScope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = currentAccount.height ?: 1700,
            activityLevel = activityLevel,
            weightUnit = currentAccount.weightUnit.value,
          )
        // IMPORTANT: Update scale first, then API (bodyCompositionService). API-first causes
        // activeAccountFlow to emit and AppViewModel.startScan() to call updateProfile(), racing
        // with our updateR4Profile callback so the loader never dismisses.
        val updatedProfile =
          currentAccount.toGGBTUserProfile().copy(isAthlete = (activityLevel == ActivityLevel.ATHLETE.name.lowercase()))
        val scaleResult = updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        when (scaleResult) {
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            dialogQueueService.enqueue(
              DialogModel.Alert(
                title = AppPopupStrings.R4ProfileUpdatePending.Title,
                message = AppPopupStrings.R4ProfileUpdatePending.Message,
                onDismiss = { dialogQueueService.dismissCurrent() },
              ),
            )
          }

          GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }

          else -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }
        }
        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, bodyComposition)
        AppLog.i(TAG, "Successfully updated activity level")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating activity level", e)
        // Error toast is shown by the service
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Shows the unit type selection modal.
   */
  private fun showUnitTypeModal() {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.UnitType,
      options =
        listOf(
          RadioButtonOption(WeightUnit.LB.value, RadioGroupModalStrings.UnitType.Imperial),
          RadioButtonOption(WeightUnit.KG.value, RadioGroupModalStrings.UnitType.Metric),
        ),
      selectedItem =
        state.value.account
          ?.weightUnit
          ?.value,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedUnitType ->
        selectedUnitType?.let { unitType ->
          onUnitTypeUpdate(unitType)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Unit type selection cancelled")
      },
    )
  }

  /**
   * Updates the unit type via the body composition service.
   * Follows the same pattern as Angular onUnitSelectionChange method.
   */
  private fun onUnitTypeUpdate(unitTypeValue: String) {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for unit type update")
      return
    }
    val newWeightUnit =
      when (unitTypeValue) {
        WeightUnit.KG.value -> WeightUnit.KG
        WeightUnit.LB.value -> WeightUnit.LB
        else -> {
          return
        }
      }
    if (currentAccount.weightUnit == newWeightUnit) {
      return
    }
    dialogQueueService.showLoader("Updating unit type...")
    viewModelScope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = currentAccount.height ?: 1700,
            activityLevel = currentAccount.activityLevel ?: "normal",
            weightUnit = newWeightUnit.value,
          )
        // IMPORTANT: Update scale first, then API. API-first causes activeAccountFlow to emit
        // and AppViewModel.startScan() to call updateProfile(), racing with our callback.
        val updatedProfile = currentAccount.toGGBTUserProfile().copy(unit = newWeightUnit.value)
        val scaleResult = updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        when (scaleResult) {
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            dialogQueueService.enqueue(
              DialogModel.Alert(
                title = AppPopupStrings.R4ProfileUpdatePending.Title,
                message = AppPopupStrings.R4ProfileUpdatePending.Message,
                onDismiss = { dialogQueueService.dismissCurrent() },
              ),
            )
          }

          GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }

          else -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }
        }
        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, bodyComposition)
        AppLog.i(TAG, "Successfully updated unit type")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating unit type", e)
        // Error toast is shown by the service
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun onBiologicalSexClick() {
    AppLog.d(TAG, "Biological sex clicked")
    showBiologicalSexModal()
  }

  fun onActivityLevelClick() {
    AppLog.d(TAG, "Activity level clicked")
    showActivityLevelModal()
  }

  fun onUnitTypeClick() {
    AppLog.d(TAG, "Unit type clicked")
    showUnitTypeModal()
  }

  fun onHeightClick() {
    AppLog.d(TAG, "Height clicked")
    showHeightModal()
  }

  /**
   * Shows the height picker modal.
   * Uses metric (cm) or imperial (ft/in) based on user's weight unit preference.
   */
  private fun showHeightModal() {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for height update")
      return
    }
    val currentHeightInput =
      HeightInput.fromStoredHeight(
        storedHeight = currentAccount.height ?: 1700, // Default to 170cm (1700 in stored format)
        isMetric = currentAccount.weightUnit.value == "kg",
      )

    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HeightPicker,
        params = mapOf("value" to currentHeightInput, "confirmText" to RadioGroupModalStrings.Button.Save),
        onConfirm = { selectedHeight ->
          if (selectedHeight is HeightInput) {
            onHeightUpdate(selectedHeight)
          }
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
        dismissOnBackPress = true,
      ),
    )
  }

  /**
   * Updates the height via the body composition service.
   * Follows the same pattern as Angular height update method.
   */
  private fun onHeightUpdate(heightInput: HeightInput) {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for height update")
      return
    }

    // Convert HeightInput to stored format
    val newStoredHeight = heightInput.toStoredHeight()

    // Check if height actually changed
    if (currentAccount.height == newStoredHeight) {
      AppLog.d(TAG, "Height is already set to $newStoredHeight, no update needed")
      return
    }

    // Show loading dialog
    dialogQueueService.showLoader("Updating height...")
    viewModelScope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = newStoredHeight,
            activityLevel = currentAccount.activityLevel ?: "normal",
            weightUnit = currentAccount.weightUnit.value,
          )
        val updatedProfile = currentAccount.toGGBTUserProfile().copy(
          height = ConversionTools.convertStoredHeightToCm(newStoredHeight).toDouble(),
        )
        val scaleResult = updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        when (scaleResult) {
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            dialogQueueService.enqueue(
              DialogModel.Alert(
                title = AppPopupStrings.R4ProfileUpdatePending.Title,
                message = AppPopupStrings.R4ProfileUpdatePending.Message,
                onDismiss = { dialogQueueService.dismissCurrent() },
              ),
            )
          }

          GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED, GGUserActionResponseType.CREATION_FAILED -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }

          else -> {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(
              Toast(
                ToastStrings.Success.UpdateProfileSuccess.Message,
                ToastStrings.Success.UpdateProfileSuccess.Header,
              ),
            )
          }
        }
        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.HEIGHT, bodyComposition)
        AppLog.i(TAG, "Successfully updated height to ${heightInput.getString()}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating height", e)
        // Error toast is shown by the service
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun onGoalSettingClick() {
    AppLog.d(TAG, "Goal setting clicked")
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.Goal)
    }
  }

  fun onWeightlessClick() {
    AppLog.d(TAG, "Weightless clicked")
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.Weightless)
    }
  }

  fun onStreakClick() {
    AppLog.d(TAG, "Streak clicked")
    showStreakModal()
  }

  /**
   * Shows the weightless mode selection modal.
   */
  private fun showWeightlessModal() {
    val currentAccount = state.value.account
    currentAccount?.isWeightlessOn ?: false

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = "Weightless Mode",
      options =
        listOf(
          RadioButtonOption("true", "On"),
          RadioButtonOption("false", "Off"),
        ),
      selectedItem = state.value.account?.isWeightlessOn ?: false,
      onConfirm = { selectedWeightless ->
        selectedWeightless?.let { weightlessValue ->
          val isWeightlessOn = weightlessValue.toString().toBoolean()
          onWeightlessUpdate(isWeightlessOn)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Weightless mode selection cancelled")
      },
    )
  }

  /**
   * Updates the weightless mode via the user settings service.
   */
  private fun onWeightlessUpdate(isWeightlessOn: Boolean) {
    val currentAccount = state.value.account
    if (currentAccount?.isWeightlessOn == isWeightlessOn) {
      AppLog.d(TAG, "Weightless mode is already set to $isWeightlessOn, no update needed")
      return
    }

    // Show loading dialog
    dialogQueueService.showLoader("Updating weightless mode...")
    viewModelScope.launch {
      try {
        userSettingsService.toggleWeightlessSetting(
          isWeightlessOn = isWeightlessOn,
          weightlessWeight = if (isWeightlessOn) currentAccount?.weightlessWeight?.toDouble() else null,
        )
        AppLog.i(TAG, "Successfully updated weightless mode")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating weightless mode", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun onAppearanceClick() {
    AppLog.d(TAG, "Appearance clicked")
    showAppearanceModal()
  }

  /**
   * Shows the appearance/theme mode selection modal.
   */
  private fun showAppearanceModal() {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.Appearance,
      options = listOf(
        RadioButtonOption("LIGHT", RadioGroupModalStrings.Appearance.Light),
        RadioButtonOption("DARK", RadioGroupModalStrings.Appearance.Dark),
        RadioButtonOption("SYSTEM", RadioGroupModalStrings.Appearance.System),
      ),
      selectedItem = getCurrentThemeModeString(),
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedTheme ->
        selectedTheme?.let { themeValue ->
          onAppearanceUpdate(themeValue.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "Appearance selection cancelled")
      },
    )
  }

  /**
   * Gets the current theme mode as a string for modal selection.
   */
  private fun getCurrentThemeModeString(): String {
    return when (state.value.currentThemeMode) {
      RadioGroupModalStrings.Appearance.Light -> "LIGHT"
      RadioGroupModalStrings.Appearance.Dark -> "DARK"
      else -> "SYSTEM"
    }
  }

  /**
   * Updates the appearance/theme mode via the user data store.
   */
  private fun onAppearanceUpdate(themeModeString: String) {
    val currentAccount = state.value.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for appearance update")
      return
    }

    // Convert string to ThemeMode enum
    val themeMode = when (themeModeString) {
      "LIGHT" -> com.dmdbrands.gurus.weight.proto.ThemeMode.LIGHT
      "DARK" -> com.dmdbrands.gurus.weight.proto.ThemeMode.DARK
      else -> com.dmdbrands.gurus.weight.proto.ThemeMode.SYSTEM
    }

    // Convert to display string
    val displayString = when (themeModeString) {
      "LIGHT" -> RadioGroupModalStrings.Appearance.Light
      "DARK" -> RadioGroupModalStrings.Appearance.Dark
      else -> RadioGroupModalStrings.Appearance.System
    }

    // Show loading dialog
    dialogQueueService.showLoader("Updating appearance...")
    viewModelScope.launch {
      try {
        userDataStore.setThemeMode(currentAccount.id, themeMode)
        handleIntent(SettingsIntent.UpdateThemeMode(displayString))
        AppLog.i(TAG, "Successfully updated appearance to $displayString")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating appearance", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Shows the streak mode selection modal.
   */
  private fun showStreakModal() {
    val currentAccount = state.value.account
    currentAccount?.isStreakOn ?: false

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = "Streak Mode",
      options =
        listOf(
          RadioButtonOption(true, "On"),
          RadioButtonOption(false, "Off"),
        ),
      selectedItem = state.value.account?.isStreakOn ?: false,
      onConfirm = { selectedStreak ->
        selectedStreak?.let { streakValue ->
          val isStreakOn = streakValue.toString().toBoolean()
          onStreakUpdate(isStreakOn)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Streak mode selection cancelled")
      },
    )
  }

  /**
   * Updates the streak mode via the user settings service.
   */
  private fun onStreakUpdate(isStreakOn: Boolean) {
    val currentAccount = state.value.account
    if (currentAccount?.isStreakOn == isStreakOn) {
      AppLog.d(TAG, "Streak mode is already set to $isStreakOn, no update needed")
      return
    }

    // Show loading dialog
    dialogQueueService.showLoader("Updating streak mode...")
    viewModelScope.launch {
      try {
        userSettingsService.toggleStreakSetting(isStreakOn = isStreakOn)
        AppLog.i(TAG, "Successfully updated streak mode")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating streak mode", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun onNotificationsClick() {
    AppLog.d(TAG, "Notifications clicked")
    showNotificationsModal()
  }

  /**
   * Shows the notifications selection modal.
   * Follows the same pattern as Angular onNotifSelectionChange method.
   * Uses reactive state property for selectedItem.
   */
  private fun showNotificationsModal() {
    // Get current notification status from reactive state property
    val currentNotificationStatus = state.value.currentNotificationStatus
    AppLog.d(TAG, "Showing notifications modal with reactive status: $currentNotificationStatus")
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.Notifications,
      options =
        listOf(
          RadioButtonOption("On", RadioGroupModalStrings.Notifications.On),
          RadioButtonOption("w/ weight", RadioGroupModalStrings.Notifications.WithWeight),
          RadioButtonOption("Off", RadioGroupModalStrings.Notifications.Off),
        ),
      selectedItem =
        when {
          state.value.account?.shouldSendEntryNotifications == true &&
            state.value.account?.shouldSendWeightInEntryNotifications == true -> "w/ weight"

          state.value.account?.shouldSendEntryNotifications == true -> "On"
          else -> "Off"
        },
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedNotification ->
        selectedNotification?.let { notificationOption ->
          onNotificationUpdate(notificationOption)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Notification selection cancelled")
      },
    )
  }

  /**
   * Converts notification option string to NotificationSettingsRequest following Angular pattern.
   */
  private fun getNotificationSettingsFromOption(option: String): NotificationSettingsRequest =
    when (option) {
      "On" ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = true,
          shouldSendWeightInEntryNotifications = false,
        )

      "w/ weight" ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = true,
          shouldSendWeightInEntryNotifications = true,
        )

      else ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = false,
          shouldSendWeightInEntryNotifications = false,
        )
    }

  /**
   * Updates the notification settings via the notification service.
   * Follows the same pattern as Angular onNotifSelectionChange method.
   * Relies on activeAccountFlow to automatically update the UI state.
   */
  private fun onNotificationUpdate(notificationOption: String) {
    dialogQueueService.showLoader("Loading...")
    viewModelScope.launch {
      try {
        val notificationSettings = getNotificationSettingsFromOption(notificationOption)
        val updatedAccount = notificationService.updateNotificationSettings(notificationSettings)
        if (updatedAccount != null) {
          dialogQueueService.dismissLoader()
          dialogQueueService.showToast(Toast("Notification settings updated", "Success!"))
          AppLog.i(TAG, "Successfully updated notification settings - flow will update UI")
          // The activeAccountFlow will automatically emit the updated account and update the UI
        } else {
          AppLog.e(TAG, "Notification settings update returned null account")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating notification settings", e)
        // Error toast is shown by the service
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /*
   * Show a confirmation dialog before logging out.
   */
  private fun onLogOutClick(isLogoutAll: Boolean = false) {
    val logoutModalString = SettingsScreenStrings.LogoutDialog
    val title =
      if (isLogoutAll) SettingsScreenStrings.LogoutDialog.LogoutAll.Title else SettingsScreenStrings.LogoutDialog.Logout.Title
    val body =
      if (isLogoutAll) SettingsScreenStrings.LogoutDialog.LogoutAll.Body else SettingsScreenStrings.LogoutDialog.Logout.Body

    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title,
        body,
        primaryActionType = ButtonType.ErrorText,
        logoutModalString.Confirm,
        logoutModalString.Cancel,
        onDismiss = {},
        onConfirm = {
          if (isLogoutAll) {
            onLogoutAllAccounts()
          } else {
            logout()
          }
        },
      ),
    )
  }

  private fun logout() {
    dialogQueueService.showLoader(SettingsScreenStrings.LoggingOut)
    viewModelScope.launch {
      try {
        val account = state.value.account
        if (account != null) {
          accountService.logout(account.id, account.fcmToken)
          navigationService.reInitialize()
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to log out", e)
      } finally {
        dialogQueueService.dismissLoader()
        dialogQueueService.clear()
      }
    }
  }

  private fun onLogoutAllAccounts() {
    dialogQueueService.showLoader(SettingsScreenStrings.LoggingOutAll)
    viewModelScope.launch {
      try {
        accountService.logoutAll()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to log out all accounts", e)
      } finally {
        dialogQueueService.dismissLoader()
        dialogQueueService.clear()
      }
    }
  }

  fun onSwitchAccountClick() {
    navigateTo(AppRoute.AccountSettings.MyAccounts)
    AppLog.d(TAG, "Navigating to My Accounts")
  }

  private fun showAccountSwitchInfoModal() {
    viewModelScope.launch {
      val hasShown = userDataStore.hasShownAccountSwitchInfoModalForDevice()
      if (hasShown) return@launch
      val activeAccount = accountService.getCurrentAccount()

      // Set the flag to true when modal is shown, so it persists even if app closes
      userDataStore.setAccountSwitchInfoModalShownForDevice(true)

      dialogQueueService.enqueue(
        DialogModel.Custom(
          contentKey = DialogType.AccountSwitchInfoPopup,
          params =
            mapOf(
              "userInitial" to (activeAccount?.firstName?.firstOrNull()?.toString() ?: "U"),
              "onAddAccount" to {
                onAccountSwitchInfoDismiss()
                navigateTo(AppRoute.AccountSettings.MyAccounts)
              },
            ),
          onDismiss = {
            onAccountSwitchInfoDismiss()
          },
        ),
      )
    }
  }

  fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }

  fun onAccountSwitchInfoDismiss() {
    // Flag is already set when modal is shown, just dismiss
    dialogQueueService.dismissCurrent()
  }

  /**
   * Formats the weightless display text for the settings screen.
   * Converts stored weight to display format with proper unit.
   * @return Formatted weightless display text
   */
  fun getWeightlessDisplayText(): String {
    val account = state.value.account
    return if (account?.isWeightlessOn == true) {
      val weightlessWeight = account.weightlessWeight
      if (weightlessWeight != null) {
        val displayWeight =
          WeightlessHelper.processStoredWeightToDisplay(weightlessWeight.toDouble(), account.weightUnit)
            "On - ${displayWeight / 10}"
      } else {
        "On"
      }
    } else {
      "Off"
    }
  }

  /**
   * Checks if export should be enabled based on latest entry.
   * Export is enabled if there is at least one entry (latestEntry != null).
   */
  private fun checkExportEnabled() {
    viewModelScope.launch {
      try {
        entryService.latestEntry.collect { latestEntry ->
          val isEnabled = latestEntry != null
          handleIntent(SettingsIntent.SetExportEnabled(isEnabled))
          AppLog.d(TAG, "Export enabled: $isEnabled (latestEntry: ${latestEntry != null})")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error checking export enabled state", e.toString())
        // Default to disabled on error
        handleIntent(SettingsIntent.SetExportEnabled(false))
      }
    }
  }

  /**
   * Loads MAC address settings from BluetoothPreferencesService.
   * Initializes the selected MAC address and testing features state.
   */
  private fun loadMacAddressSettings() {
    viewModelScope.launch {
      try {
        // Load selected MAC address
        bluetoothPreferencesService.selectedMacAddress.collect { selectedMac ->
          handleIntent(SettingsIntent.UpdateSelectedMacAddress(selectedMac))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error loading MAC address settings", e.toString())
      }
    }

    viewModelScope.launch {
      try {
        // Load testing features state
        val testingEnabled = bluetoothPreferencesService.enableTestingFeatures
        handleIntent(SettingsIntent.UpdateTestingFeatures(testingEnabled))
      } catch (e: Exception) {
        AppLog.e(TAG, "Error loading testing features state", e.toString())
      }
    }
  }

  /**
   * Handles MAC address filter modal click.
   * Similar to Angular's onMacAddressSelectionChange method.
   */
  private fun onMacAddressFilterClick() {
    AppLog.d(TAG, "MAC address filter clicked")

    // Only show modal if testing features are enabled
    if (!state.value.enableTestingFeatures) {
      AppLog.d(TAG, "Testing features disabled, MAC address filter not available")
      return
    }

    showMacAddressFilterModal()
  }

  /**
   * Shows the MAC address filter selection modal.
   * Displays known MAC addresses for 0412 scale filtering.
   * Similar to Angular's MAC address selection functionality.
   */
  private fun showMacAddressFilterModal() {
    val knownMacAddresses = bluetoothPreferencesService.knownMacAddresses

    val macAddressOptions = knownMacAddresses.map { macAddress ->
      RadioButtonOption(macAddress, macAddress)
    }

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = "MAC Address Filter (0412 Scales)",
      options = macAddressOptions,
      selectedItem = state.value.selectedMacAddress,
      maxHeight = 400.dp, // Enable scrolling for large lists
      onConfirm = { selectedMacAddress ->
        selectedMacAddress?.let { macAddress ->
          onMacAddressSelectionChange(macAddress)
        }
      },
      onCancel = {
        AppLog.d(TAG, "MAC address filter selection cancelled")
      },
    )
  }

  /**
   * Handles MAC address selection change.
   * Similar to Angular's onMacAddressSelectionChange method.
   * Updates the selected MAC address locally via BluetoothPreferencesService.
   */
  private fun onMacAddressSelectionChange(selectedMacAddress: String) {
    AppLog.d(TAG, "MAC address selection changed to: $selectedMacAddress")

    // Only process if testing features are enabled
    if (!state.value.enableTestingFeatures) {
      AppLog.w(TAG, "Testing features disabled, ignoring MAC address selection")
      return
    }

    // Show loading dialog
    dialogQueueService.showLoader("Updating MAC address filter...")

    viewModelScope.launch {
      try {
        // Update selected MAC address locally (similar to Angular implementation)
        bluetoothPreferencesService.setSelectedMacAddressLocally(selectedMacAddress)

        // Update UI state
        handleIntent(SettingsIntent.UpdateSelectedMacAddress(selectedMacAddress))

        AppLog.i(TAG, "Successfully updated MAC address filter to: $selectedMacAddress")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating MAC address filter", e.toString())
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }
}
