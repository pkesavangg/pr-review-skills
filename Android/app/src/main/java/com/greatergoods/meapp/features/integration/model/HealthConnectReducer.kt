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
            HealthConnectIntent.Connect -> {
                state.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
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
            HealthConnectIntent.Skip -> {
                state.copy(
                    healthConnectSetupState = HealthConnectSetup.CANCEL_CONNECT
                )
            }
            HealthConnectIntent.Finish -> {
                state.copy(
                    healthConnectSetupState = HealthConnectSetup.COMPLETE_RECONNECTION
                )
            }
            HealthConnectIntent.ClearError -> {
                state.copy(
                    errorMessage = null
                )
            }
          else -> {
            state
          }
        }
    }
}
