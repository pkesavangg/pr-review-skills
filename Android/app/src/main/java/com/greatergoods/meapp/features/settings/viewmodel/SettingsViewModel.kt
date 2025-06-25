package com.greatergoods.meapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.enum.AccountSettingsAction
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.domain.services.IExportService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.export.strings.ExportStrings
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
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val authService: IAccountAuthService,
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
            authService.activeAccountFlow.collect {
                handleIntent(SettingsIntent.updateAccount(it))
            }
        }
    }

        override fun handleIntent(intent: SettingsIntent) {
            super.handleIntent(intent)
            when (intent) {
                is SettingsIntent.updateAccount -> {
                    _state.value = _state.value.copy(account = intent.account)
                }

                is SettingsIntent.ExportData -> {
                    onExportDataClick()
                }

                is SettingsIntent.Logout -> {
                    onLogOutClick()
                }

                else -> {}
            }

        fun onEditProfileClick() {
            AppLog.d("TAG", "Edit profile clicked")
            // TODO: Navigate to edit profile screen
        }

        fun onAddEditScalesClick() {
            AppLog.d("TAG", "Add/Edit scales clicked")
            // TODO: Navigate to scales screen
        }

        fun onIntegrationsClick() {
            AppLog.d("TAG", "Integrations clicked")
            // TODO: Navigate to integrations screen
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

    fun onIntegrationsClick() {
        AppLog.d("SettingsViewModel", "Integrations clicked")
        // TODO: Navigate to integrations screen
    }

    fun onExportDataClick() {
        AppLog.d("SettingsViewModel", "Export data clicked")
        // TODO: Show export data dialog
    }

    fun onChangePasswordClick() {
        AppLog.d("SettingsViewModel", "Change password clicked")
        // TODO: Navigate to change password screen
    }

    fun onGoalSettingClick() {
        AppLog.d("SettingsViewModel", "Goal setting clicked")
        // TODO: Navigate to goal setting screen
    }

    fun onBiologicalSexClick() {
        AppLog.d("SettingsViewModel", "Biological sex clicked")
        // TODO: Show biological sex dialog
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

    fun onPrivacyPolicyClick() {
        AppLog.d("SettingsViewModel", "Privacy policy clicked")
        // TODO: Open privacy policy in browser
    }

    fun onTermsOfServiceClick() {
        AppLog.d("SettingsViewModel", "Terms of service clicked")
        // TODO: Open terms of service in browser
    }

    fun onGreaterGoodsClick() {
        AppLog.d("SettingsViewModel", "GreaterGoods.com clicked")
        // TODO: Open GreaterGoods.com in browser
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
                    authService.logout(account.id)
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
}
