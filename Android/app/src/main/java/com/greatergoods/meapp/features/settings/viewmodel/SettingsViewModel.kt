package com.greatergoods.meapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
    ) : BaseIntentViewModel<SettingsState, SettingsIntent>(
            SettingsReducer(),
        ) {
        override fun provideInitialState(): SettingsState = SettingsState()

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

                is SettingsIntent.Logout -> {
                    onLogOutClick()
                }

                else -> {}
            }
        }

        fun onEditProfileClick() {
            AppLog.d("SettingsViewModel", "Edit profile clicked")
            // TODO: Navigate to edit profile screen
        }

        fun onAddEditScalesClick() {
            AppLog.d("SettingsViewModel", "Add/Edit scales clicked")
            // TODO: Navigate to scales screen
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

        fun onLogOutClick() {
            AppLog.d("SettingsViewModel", "Log out clicked")

            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    "Log out",
                    "Are you sure you want to log out?",
                    "Log out",
                    "Cancel",
                    onDismiss = {},
                    onConfirm = {
                        logout()
                    },
                ),
            )
        }

        fun logout() {
            viewModelScope.launch {
                try {
                    val account = state.value.account
                    if (account != null) {
                        authService.logout(account.id)
                    }
                } catch (e: Exception) {
                    AppLog.e("SettingsViewModel", "Failed to log out", e.toString())
                }
            }
        }

        fun onDeleteAccountClick() {
            AppLog.d("SettingsViewModel", "Delete account clicked")
            // TODO: Show delete account confirmation dialog
        }
    }
