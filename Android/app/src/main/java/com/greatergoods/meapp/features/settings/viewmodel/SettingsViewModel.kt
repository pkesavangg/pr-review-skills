package com.greatergoods.meapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.enums.ActivityLevel
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.common.Gender
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.services.BodyCompUpdateType
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IBodyCompositionService
import com.greatergoods.meapp.domain.services.IExportService
import com.greatergoods.meapp.domain.services.INotificationService
import com.greatergoods.meapp.domain.services.IUserSettingsService
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.components.RadioButtonOption
import com.greatergoods.meapp.features.common.components.showRadioGroupModal
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.export.strings.ExportStrings
import com.greatergoods.meapp.features.settings.strings.RadioGroupModalStrings
import com.greatergoods.meapp.features.settings.strings.SettingsScreenStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
        private val accountService: IAccountService,
        private val exportService: IExportService,
        private val bodyCompositionService: IBodyCompositionService,
        private val userDataStore: UserDataStore,
        private val notificationService: INotificationService,
        private val userSettingsService: IUserSettingsService,
    ) : BaseIntentViewModel<SettingsState, SettingsIntent>(
            SettingsReducer(),
        ) {
        override fun provideInitialState(): SettingsState = SettingsState()

        private val TAG = "SettingsViewModel"

        init {
            getUserProfile()
            showAccountSwitchInfoModal()
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

                is SettingsIntent.ShowStreakModal -> {
                    onStreakClick()
                }

                is SettingsIntent.goalSettingModal -> {
                    onGoalSettingClick()
                }

                else -> {}
            }
        }

        fun onExportDataClick() {
            AppLog.d("TAG", "Export data clicked")

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
                        AppLog.d("TAG", "User cancelled export")
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
                    AppLog.e(TAG, ExportStrings.ExportFailed, e.toString())
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
                        RadioButtonOption(Gender.MALE.name, RadioGroupModalStrings.BiologicalSex.Male),
                        RadioButtonOption(Gender.FEMALE.name, RadioGroupModalStrings.BiologicalSex.Female),
                    ),
                selectedItem = state.value.account?.gender,
                onConfirm = { selectedSex ->
                    AppLog.d("SettingsViewModel", "Biological sex modal onConfirm called with: $selectedSex")
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
            dialogQueueService.showLoader("Updating biological sex...")
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
                    // Use offline handler service similar to Angular implementation
                    accountService.updateProfile(updatedCurrentProfile)
                    AppLog.i(TAG, "Successfully updated biological sex")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error updating biological sex", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
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
            // Show loading dialog
            val currentAccount = state.value.account

            dialogQueueService.showLoader("Updating activity level...")
            viewModelScope.launch {
                try {
                    val bodyComposition =
                        BodyCompUpdateRequest(
                            height = currentAccount?.height ?: 1700,
                            activityLevel = activityLevel,
                            weightUnit = currentAccount?.weightUnit?.value ?: "lb",
                        )
                    bodyCompositionService.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, bodyComposition)
                    AppLog.i(TAG, "Successfully updated activity level")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error updating activity level", e.toString())
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
                    bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, bodyComposition)
                    AppLog.i(TAG, "Successfully updated unit type")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error updating unit type", e.toString())
                    // Error toast is shown by the service
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        fun onBiologicalSexClick() {
            AppLog.d("SettingsViewModel", "Biological sex clicked")
            showBiologicalSexModal()
        }

        fun onActivityLevelClick() {
            AppLog.d("SettingsViewModel", "Activity level clicked")
            showActivityLevelModal()
        }

        fun onUnitTypeClick() {
            AppLog.d("SettingsViewModel", "Unit type clicked")
            showUnitTypeModal()
        }

        fun onHeightClick() {
            AppLog.d("SettingsViewModel", "Height clicked")
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
                    isMetric = currentAccount.weightUnit?.value == "kg",
                )

            dialogQueueService.enqueue(
                DialogModel.Custom(
                    contentKey = DialogType.HeightPicker,
                    params = mapOf("value" to currentHeightInput),
                    onConfirm = { selectedHeight ->
                        if (selectedHeight is HeightInput) {
                            onHeightUpdate(selectedHeight)
                        }
                    },
                    onDismiss = {
                        AppLog.d(TAG, "Height picker cancelled")
                    },
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
                            weightUnit = currentAccount.weightUnit?.value ?: "lb",
                        )
                    bodyCompositionService.updateBodyComposition(BodyCompUpdateType.HEIGHT, bodyComposition)
                    AppLog.i(TAG, "Successfully updated height to ${heightInput.getString()}")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error updating height", e.toString())
                    // Error toast is shown by the service
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        fun onGoalSettingClick() {
            AppLog.d("SettingsViewModel", "Goal setting clicked")
            viewModelScope.launch {
                navigationService.navigateTo(AppRoute.AccountSettings.Goal)
            }
        }

        fun onWeightlessClick() {
            AppLog.d("SettingsViewModel", "Weightless clicked")
            viewModelScope.launch {
                navigationService.navigateTo(AppRoute.AccountSettings.Weightless)
            }
        }

        fun onStreakClick() {
            AppLog.d("SettingsViewModel", "Streak clicked")
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
                    AppLog.e(TAG, "Error updating weightless mode", e.toString())
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
                    AppLog.e(TAG, "Error updating streak mode", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        fun onNotificationsClick() {
            AppLog.d("SettingsViewModel", "Notifications clicked")
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
            dialogQueueService.showLoader("Updating notification settings...")
            viewModelScope.launch {
                try {
                    val notificationSettings = getNotificationSettingsFromOption(notificationOption)
                    val updatedAccount = notificationService.updateNotificationSettings(notificationSettings)
                    if (updatedAccount != null) {
                        AppLog.i(TAG, "Successfully updated notification settings - flow will update UI")
                        // The activeAccountFlow will automatically emit the updated account and update the UI
                    } else {
                        AppLog.e(TAG, "Notification settings update returned null account")
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error updating notification settings", e.toString())
                    // Error toast is shown by the service
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        fun onMessagesClick() {
            AppLog.d("SettingsViewModel", "Messages clicked")
            // TODO: Navigate to messages settings
        }

        fun onAppPermissionsClick() {
            AppLog.d("SettingsViewModel", "App permissions clicked")
            // TODO: Navigate to app permissions screen
        }

        fun onHelpClick() {
            AppLog.d("SettingsViewModel", "Help clicked")
            // TODO: Navigate to help screen
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
                    }
                } catch (e: Exception) {
                    AppLog.e("SettingsViewModel", "Failed to log out", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        private fun onLogoutAllAccounts() {
            dialogQueueService.showLoader(SettingsScreenStrings.LoggingOutAll)
            viewModelScope.launch {
                try {
                    accountService.logoutAll()
                } catch (e: Exception) {
                    AppLog.e("SettingsViewModel", "Failed to log out all accounts", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        fun onDeleteAccountClick() {
            AppLog.d("SettingsViewModel", "Delete account clicked")
            // TODO: Show delete account confirmation dialog
        }

        fun onSwitchAccountClick() {
            navigateTo(AppRoute.AccountSettings.MyAccounts)
            AppLog.d("onSwitchAccountClick", "Navigating to My Accounts")
        }

        private fun showAccountSwitchInfoModal() {
            viewModelScope.launch {
                val hasShown = userDataStore.hasShownAccountSwitchInfoModalForDevice()
                if (hasShown) return@launch
                val activeAccount = accountService.getCurrentAccount()
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
            viewModelScope.launch {
                userDataStore.setAccountSwitchInfoModalShownForDevice(true)
                dialogQueueService.dismissCurrent()
            }
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
                        ConversionTools.convertStoredToDisplay(weightlessWeight.toDouble(), account.isMetric)
                    val formattedWeight = String.format("%.1f", displayWeight)
                    "On - $formattedWeight"
                } else {
                    "On"
                }
            } else {
                "Off"
            }
        }
    }
