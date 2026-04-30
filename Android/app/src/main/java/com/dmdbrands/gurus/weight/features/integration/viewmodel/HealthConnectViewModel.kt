package com.dmdbrands.gurus.weight.features.integration.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectAction
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectIntent
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectReducer
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectSetup
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectUiState
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Intent

/**
 * ViewModel for managing Health Connect integration state and handling user intents.
 */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val healthConnectService: IHealthConnectService,
) : BaseIntentViewModel<HealthConnectUiState, HealthConnectIntent>(
  reducer = HealthConnectReducer(),
), DefaultLifecycleObserver {

    private val _navigationEvent = MutableStateFlow<Intent?>(null)
    val navigationEvent: StateFlow<Intent?> = _navigationEvent.asStateFlow()

    private val tag = "HealthConnectViewModel"

    init {
        // Initialize the Health Connect state
        initializeHealthConnectState()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        handleAppResume()
    }

    /**
     * Handles app resume to check if returning from Health Connect.
     */
    private fun handleAppResume() {
        viewModelScope.launch {
            if (_state.value.isHealthConnectOpened) {
                handleIntent(HealthConnectIntent.AppResumed)
                resumeSetup()
            }
        }
    }

    /**
     * Resumes setup after returning from Health Connect app.
     */
    private suspend fun resumeSetup() {
        try {
            val permissionStatus = healthConnectService.checkPermissionStatus()
            when (permissionStatus) {
                HealthConnectPermissionStatus.ALL -> {
                    _state.update { it.copy(
                        healthConnectSetupState = HealthConnectSetup.FINISH_CONNECT,
                        isLoading = false
                    )}
                }
                HealthConnectPermissionStatus.PARTIAL -> {
                    _state.update { it.copy(
                        healthConnectSetupState = HealthConnectSetup.FINISH_CONNECT,
                        isLoading = false
                    )}
                }
                HealthConnectPermissionStatus.NONE -> {
                    // User cancelled, potentially dismiss modal
                    _state.update { it.copy(
                        healthConnectSetupState = HealthConnectSetup.START_CONNECT,
                        isLoading = false
                    )}
                }
            }
        } catch (e: Exception) {
            _state.update { currentState ->
                currentState.copy(
                    errorMessage = "Failed to resume setup: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

  override fun provideInitialState(): HealthConnectUiState = HealthConnectUiState()

  /**
     * Handles user intents and updates state using the reducer.
     */
  override fun handleIntent(intent: HealthConnectIntent) {
    super.handleIntent(intent)
        viewModelScope.launch {
            when (intent) {
                is HealthConnectIntent.PrimaryAction -> {
                    handlePrimaryAction(intent.label)
                }
                is HealthConnectIntent.SecondaryAction -> {
                    handleSecondaryAction(intent.label)
                }
                is HealthConnectIntent.ConfirmExitSetup -> {
                    exitSetup()
                }
                else -> {}
            }
        }
    }

    /**
     * Handles primary action based on the action label.
     */
    private suspend fun handlePrimaryAction(label: HealthConnectAction) {
        when (label) {
            HealthConnectAction.CONNECT -> {
              dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
                handleConnect()
              dialogQueueService.dismissLoader()
            }
            HealthConnectAction.FINISH -> {
                handleFinish()
            }
            HealthConnectAction.OPEN_HEALTH_CONNECT -> {
                openHealthConnect()
            }
            HealthConnectAction.UPDATE_PERMISSIONS -> {
                handleConnect(fromIncomplete = true)
            }
            HealthConnectAction.EXIT -> {
                handleExitSetup()
            }
            else -> {}
        }
    }

    /**
     * Handles secondary action based on the action label.
     */
    private fun handleSecondaryAction(label: HealthConnectAction) {
        when (label) {
            HealthConnectAction.SKIP -> {
                _state.update { it.copy(
                    healthConnectSetupState = HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION,
                    isLoading = false
                ) }
            }
            HealthConnectAction.EXIT -> {
                handleExitSetup()
            }
            HealthConnectAction.OPEN_HEALTH_CONNECT -> {
                openHealthConnect()
            }
            else -> {}
        }
    }

    /**
     * Handles exit setup logic with different behaviors based on current state.
     */
    private fun handleExitSetup() {
        navigateBack()
        return
    }

    private fun exitAlert(){
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = HealthConnectStrings.ExitAlert.title,
                message = HealthConnectStrings.ExitAlert.description,
                confirmText = HealthConnectStrings.ActionButtons.exitSetup,
                cancelText = HealthConnectStrings.ActionButtons.cancel,
                onConfirm = {
                    navigateBack()
                    dialogQueueService.dismissCurrent()
                },
                onCancel = {
                    dialogQueueService.dismissCurrent()
                },
            ),
        )
    }

    /**
     * Determines the setup state based on Health Connect status, user conflict, and permissions.
     * This matches the Angular connectHealthConnectIntegration logic.
     * Note: INSTALL_REQUIRED and UNAVAILABLE alerts are handled in IntegrationViewModel.connectHealthConnectIntegration().
     * This method only handles INSTALLED/UPDATE_REQUIRED cases for modal setup states.
     */
    private suspend fun determineSetupState(
        status: com.greatergoods.libs.healthconnect.enums.HealthConnectStatus,
        permissionStatus: HealthConnectPermissionStatus
    ): HealthConnectSetup {
        return when (status) {
            com.greatergoods.libs.healthconnect.enums.HealthConnectStatus.INSTALLED,
            com.greatergoods.libs.healthconnect.enums.HealthConnectStatus.UPDATE_REQUIRED -> {
                // Check for user conflict
                val isAlreadyUsed = try {
                    healthConnectService.checkIfAlreadyUsed()
                } catch (e: Exception) {
                    AppLog.e("HealthConnectViewModel", "Failed to check if Health Connect is already used", e)
                    false
                }
                if (!isAlreadyUsed) {
                    return HealthConnectSetup.USER_CONFLICT
                }
                // Check permission status
                when (permissionStatus) {
                    HealthConnectPermissionStatus.ALL -> HealthConnectSetup.COMPLETE_RECONNECTION
                    HealthConnectPermissionStatus.PARTIAL -> HealthConnectSetup.INCOMPLETE_RECONNECTION
                    HealthConnectPermissionStatus.NONE -> HealthConnectSetup.START_CONNECT
                }
            }
            com.greatergoods.libs.healthconnect.enums.HealthConnectStatus.INSTALL_REQUIRED -> {
                // Alert is handled in IntegrationViewModel.connectHealthConnectIntegration()
                // Return NONE as this case is handled before navigation
                HealthConnectSetup.NONE
            }
            com.greatergoods.libs.healthconnect.enums.HealthConnectStatus.UNAVAILABLE -> {
                // Alert is handled in IntegrationViewModel.connectHealthConnectIntegration()
                // Return NONE as this case is handled before navigation
                HealthConnectSetup.NONE
            }
        }
    }

    /**
     * Initializes the Health Connect state by checking availability and status.
     */
    private fun initializeHealthConnectState() {
        viewModelScope.launch {
            try {
                val isAvailable = healthConnectService.checkAvailability()
                val status = healthConnectService.healthConnectStatus()
                val permissionStatus: HealthConnectPermissionStatus = healthConnectService.checkPermissionStatus()
                val setupState = determineSetupState(status, permissionStatus)
                _state.update { currentState ->
                    currentState.copy(
                        isHealthConnectAvailable = isAvailable,
                        permissionStatus = permissionStatus,
                        healthConnectSetupState = setupState
                    )
                }
            } catch (e: Exception) {
                AppLog.e("HealthConnectViewModel", "Failed to initialize Health Connect", e)
                _state.update { currentState ->
                    currentState.copy(
                        errorMessage = "Failed to initialize Health Connect: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Handles the connect action.
     */
    private suspend fun handleConnect(fromIncomplete: Boolean = false) {
        try {
            healthConnectService.requestAuthorization { requestStatus ->
                viewModelScope.launch {
                    when (requestStatus) {
                        HealthConnectRequestStatus.CONNECTED -> {
                            if (!fromIncomplete) {
                                handleIntent(HealthConnectIntent.ConnectSuccess)
                              _state.update { it.copy(healthConnectSetupState = HealthConnectSetup.FINISH_CONNECT) }
                              } else {
                                _state.update { it.copy(healthConnectSetupState = HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION) }
                            }
                        }
                        HealthConnectRequestStatus.PARTIAL -> {
                            _state.update { it.copy(healthConnectSetupState = HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION) }
                        }
                        HealthConnectRequestStatus.PRIVACY_POLICY -> {
                            openInAppBrowser(AppConfig.AppUrls.PrivacyPolicy)
                        }
                        HealthConnectRequestStatus.CANCELLED -> {
                            _state.update { it.copy(healthConnectSetupState = HealthConnectSetup.PERMISSION_LIMIT) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("HealthConnectViewModel", "Failed to handle Health Connect connection", e)
        }
        finally {
        }
    }

    /**
     * Handles the finish action.
     */
    private  fun handleFinish() {
      viewModelScope.launch {
          dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
        try {
          healthConnectService.turnOnIntegration()
          navigationService.navigateBack()
        } catch (e: Exception) {
          AppLog.e("HealthConnectViewModel", "Failed to finish Health Connect integration", e)
          _state.update { currentState ->
            currentState.copy(errorMessage = "Failed to finish integration: ${e.message}")
          }
        }
          finally {
              dialogQueueService.dismissLoader()
          }
      }
    }

    /**
     * Opens Health Connect app.
     */
    fun openHealthConnect() {
        viewModelScope.launch {
          dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
            try {
                healthConnectService.openHealthConnect(true)
            } catch (e: Exception) {
                AppLog.e("HealthConnectViewModel", "Failed to open Health Connect", e)
                _state.update { currentState ->
                    currentState.copy(errorMessage = "Failed to open Health Connect: ${e.message}")
                }
            }
          finally {
            dialogQueueService.dismissLoader()
          }
        }
    }


    /**
     * Handles navigation back from change password screen.
     * Call this when user wants to exit the change password flow.
     */
    private fun navigateBack() {
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
                AppLog.d("ChangePasswordViewModel", "Successfully navigated back from change password")
            } catch (e: Exception) {
                AppLog.e("ChangePasswordViewModel", "Failed to navigate back from change password", e)
            }
        }
    }

    /**
     * Handles the exit setup logic, showing an alert or finishing as appropriate.
     */
    fun exitSetup() {
        val setupState = _state.value.healthConnectSetupState
        when (setupState) {
            HealthConnectSetup.FINISH_CONNECT,
            HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION,
            HealthConnectSetup.COMPLETE_RECONNECTION -> {
                viewModelScope.launch {
                    handleFinish()
                }
                return
            }
            HealthConnectSetup.PERMISSION_LIMIT,
            HealthConnectSetup.USER_CONFLICT -> {
               navigateBack()
                return
            }
            else -> {
                exitAlert()
            }
        }
    }
}

