package com.greatergoods.meapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.enum.AccountSettingsAction
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.domain.services.IExportService
import com.greatergoods.meapp.features.common.components.RadioButtonOption
import com.greatergoods.meapp.features.common.components.showRadioGroupModal
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.export.strings.ExportStrings
import com.greatergoods.meapp.features.settings.strings.RadioGroupModalStrings
import com.greatergoods.meapp.features.settings.strings.SettingsScreenStrings
import com.greatergoods.meapp.features.signup.model.Gender
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
// MyAccountsScreen will use AccountAuthService.loggedInAccountsFlow for account data.
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val accountService: IAccountAuthService,
    private val exportService: IExportService,
) : BaseIntentViewModel<SettingsState, SettingsIntent>(
    SettingsReducer(),
) {
    override fun provideInitialState(): SettingsState = SettingsState()
    private val TAG = "SettingsViewModel"

    init {
        getUserProfile()
    }

    fun getUserProfile() {
        viewModelScope.launch {
            accountService.activeAccountFlow.collect {
                handleIntent(SettingsIntent.UpdateAccount(it))
            }
        }
    }

    override fun handleIntent(intent: SettingsIntent) {
        super.handleIntent(intent)
        when (intent) {
            is SettingsIntent.UpdateAccount -> {
                _state.value = _state.value.copy(account = intent.account)
            }

            is SettingsIntent.ExportData -> {
                onExportDataClick()
            }

            is SettingsIntent.Logout -> {
                onLogOutClick()
            }

            is SettingsIntent.SwitchAccount -> {
                onSwitchAccountClick()
            }

            is SettingsIntent.ShowBiologicalSexModal -> {
                onBiologicalSexClick()
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
                // Call the export service
                exportService.exportCsvWithPrompt()
                // Show success toast
                showExportSuccessToast()
                AppLog.i("TAG", ExportStrings.ExportCompleted)
            } catch (e: HttpException) {
                // Show error toast
                showErrorToast(AccountSettingsAction.EXPORT_CSV, e)
                AppLog.e("TAG", ExportStrings.ExportFailed, e.toString())
            } finally {
                // Dismiss loading spinner
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Shows success toast for export operation.
     */
    private fun showExportSuccessToast() {
        dialogQueueService.showToast(
            Toast(
                message = ExportStrings.SuccessMessage,
            ),
        )
    }

    fun showErrorToast(action: AccountSettingsAction, error: HttpException?) {
        val (title, message) = when (action) {
            AccountSettingsAction.EXPORT_CSV -> {
                val header = ""
                val message = when (error?.code()) {
                    HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                        ToastStrings.Error.LoginError.MessageNoConn

                    HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                        ToastStrings.Error.LoginError.MessageServError

                    HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                        ToastStrings.Error.LoginError.MessageNotAuth

                    else ->
                        ToastStrings.Error.LoginError.MessageGeneric
                }
                header to message
            }
        }
        val errorToast = Toast(
            title = title,
            message = message,
            action = null,
        )
        dialogQueueService.showToast(errorToast)
    }

    fun onGoalSettingClick() {
        AppLog.d("SettingsViewModel", "Goal setting clicked")
        // TODO: Navigate to goal setting screen
    }

    fun onBiologicalSexClick() {
        AppLog.d("SettingsViewModel", "Biological sex clicked")
        showBiologicalSexModal()
    }

    /**
     * Shows the biological sex selection modal.
     */
    private fun showBiologicalSexModal() {
        AppLog.d("SettingsViewModel", "showBiologicalSexModal called")

        showRadioGroupModal(
            dialogService = dialogQueueService,
            title = RadioGroupModalStrings.Titles.BiologicalSex,
            options = listOf(
                RadioButtonOption(Gender.MALE.value, RadioGroupModalStrings.BiologicalSex.Male),
                RadioButtonOption(Gender.FEMALE.value, RadioGroupModalStrings.BiologicalSex.Female),
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
        AppLog.d("SettingsViewModel", "onBiologicalSexUpdate called with gender: $gender")

        val currentAccount = state.value.account
        if (currentAccount == null) {
            AppLog.e("SettingsViewModel", "No active account found for biological sex update")
            return
        }

        if (currentAccount.gender == gender) {
            AppLog.d("SettingsViewModel", "Gender is already set to $gender, no update needed")
            return
        }

        AppLog.i("SettingsViewModel", "Starting biological sex update from '${currentAccount.gender}' to '$gender'")

        // Show loading dialog
        dialogQueueService.showLoader("Updating biological sex...")
        viewModelScope.launch {
            try {
                val updatedCurrentProfile = ProfileUpdateRequest(
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
                AppLog.i("SettingsViewModel", "Successfully updated biological sex")
            } catch (e: Exception) {
                val accountID = currentAccount.id
                val updatedAccount = PartialAccount(
                    firstName = currentAccount.firstName,
                    lastName = currentAccount.lastName,
                    dob = currentAccount.dob,
                    gender = gender,  // Use the NEW gender value, not the old one
                    zipcode = currentAccount.zipcode,
                    email = currentAccount.email,
                    isActiveAccount = true,
                    isSynced = false,  // Mark as unsynced for later offline sync
                )
                accountService.updateProfileInDB(accountID, updatedAccount)
                AppLog.e("SettingsViewModel", "Error updating biological sex", e.toString())
                // Error toast is shown by the service
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    fun onActivityLevelClick() {
        AppLog.d("SettingsViewModel", "Activity level clicked")
        // TODO: Show activity level dialog
    }

    fun onHeightClick() {
        AppLog.d("SettingsViewModel", "Height clicked")
        // TODO: Show height dialog
    }

    fun onUnitTypeClick() {
        AppLog.d("SettingsViewModel", "Unit type clicked")
        // TODO: Show unit type dialog
    }

    fun onWeightlessClick() {
        AppLog.d("SettingsViewModel", "Weightless clicked")
        // TODO: Toggle weightless mode
    }

    fun onNotificationsClick() {
        AppLog.d("SettingsViewModel", "Notifications clicked")
        // TODO: Navigate to notifications settings
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
    private fun onLogOutClick() {
        val logoutModalString = SettingsScreenStrings.LogoutDialog
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                logoutModalString.Title,
                logoutModalString.Body,
                logoutModalString.Confirm,
                logoutModalString.Cancel,
                onDismiss = {},
                onConfirm = {
                    logout()
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

    fun onDeleteAccountClick() {
        AppLog.d("SettingsViewModel", "Delete account clicked")
        // TODO: Show delete account confirmation dialog
    }

    fun onSwitchAccountClick() {
        viewModelScope.launch {
            navigationService.navigateTo(AppRoute.AccountSettings.MyAccounts)
        }
        AppLog.d("onSwitchAccountClick", "Navigating to My Accounts")
    }
}
