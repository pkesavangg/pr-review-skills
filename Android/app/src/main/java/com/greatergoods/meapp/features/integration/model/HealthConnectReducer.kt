package com.greatergoods.meapp.features.integration.model

/**
 * Reducer for handling Health Connect state updates.
 */
object HealthConnectReducer {
    /**
     * Reduces the current state based on the given intent.
     *
     * @param state Current UI state
     * @param intent User intent to process
     * @return Updated UI state
     */
    fun reduce(state: HealthConnectUiState, intent: HealthConnectIntent): HealthConnectUiState {
        return when (intent) {

            HealthConnectIntent.ConnectSuccess -> {
                state.copy(
                    isLoading = false,
                    healthConnectSetupState = HealthConnectSetup.FINISH_CONNECT,
                    errorMessage = null
                )
            }
            HealthConnectIntent.ConnectError -> {
                state.copy(
                    isLoading = false,
                    healthConnectSetupState = HealthConnectSetup.START_CONNECT,
                    errorMessage = "Failed to connect to Health Connect"
                )
            }
            HealthConnectIntent.AppResumed -> {
                state.copy(
                    isHealthConnectOpened = false
                )
            }
            HealthConnectIntent.SetAlertPresented -> {
                state.copy(
                    alertPresented = true
                )
            }
            HealthConnectIntent.ClearAlertPresented -> {
                state.copy(
                    alertPresented = false
                )
            }
            HealthConnectIntent.SetHealthConnectOpened -> {
                state.copy(
                    isHealthConnectOpened = true
                )
            }
            HealthConnectIntent.ClearHealthConnectOpened -> {
                state.copy(
                    isHealthConnectOpened = false
                )
            }
            is HealthConnectIntent.UpdateSlide -> {
                state.copy(
                    currentSlide = intent.slide
                )
            }
            is HealthConnectIntent.PrimaryAction -> {
                // Handle primary action based on label
                handlePrimaryAction(state, intent.label)
            }
            is HealthConnectIntent.SecondaryAction -> {
                // Handle secondary action based on label
                handleSecondaryAction(state, intent.label)
            }
          is HealthConnectIntent.ConfirmExitSetup -> {
              state.copy(
                isLoading = false,
                errorMessage = null
              )
          }
        }
    }

    private fun handlePrimaryAction(state: HealthConnectUiState, action: HealthConnectAction): HealthConnectUiState {
        return when (action) {
            HealthConnectAction.CONNECT -> {
                state.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            HealthConnectAction.FINISH -> {
                state.copy(
                    healthConnectSetupState = HealthConnectSetup.COMPLETE_RECONNECTION
                )
            }
            HealthConnectAction.OPEN_HEALTH_CONNECT -> {
                state.copy(
                    isHealthConnectOpened = true
                )
            }
            HealthConnectAction.UPDATE_PERMISSIONS -> {
                state.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            HealthConnectAction.EXIT -> {
                state.copy(
                    alertPresented = true
                )
            }
            else -> state
        }
    }

    private fun handleSecondaryAction(state: HealthConnectUiState, action: HealthConnectAction): HealthConnectUiState {
        return when (action) {
            HealthConnectAction.SKIP -> {
                state.copy(
                    healthConnectSetupState = HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION
                )
            }
            HealthConnectAction.EXIT -> {
                state.copy(
                    alertPresented = true
                )
            }
            HealthConnectAction.OPEN_HEALTH_CONNECT -> {
                state.copy(
                    isHealthConnectOpened = true
                )
            }
            else -> state
        }
    }
}
