package com.dmdbrands.gurus.weight.features.integration.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus

data class HealthConnectUiState(
  val healthConnectSetupState: HealthConnectSetup = HealthConnectSetup.NONE,
  val currentSlide: Int = 0,
  val isHealthConnectAvailable: Boolean = false,
  val isLoading: Boolean = false,
  val permissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NONE,
  val isOutOfSync: Boolean = false,
  val errorMessage: String? = null,
  val actionButtons: ActionButtons = ActionButtons(),
  val alertPresented: Boolean = false,
  val isHealthConnectOpened: Boolean = false,
  val pageLoadFrom: PageLoadFrom = PageLoadFrom()
): IReducer.State


/**
 * Sealed class representing user intents for Health Connect integration.
 */
sealed class HealthConnectIntent: IReducer.Intent  {
  data object ConfirmExitSetup : HealthConnectIntent()
  data object ConnectSuccess : HealthConnectIntent()
  data object ConnectError : HealthConnectIntent()
  data object AppResumed : HealthConnectIntent()
  data object SetAlertPresented : HealthConnectIntent()
  data object ClearAlertPresented : HealthConnectIntent()
  data object SetHealthConnectOpened : HealthConnectIntent()
  data object ClearHealthConnectOpened : HealthConnectIntent()
  data class UpdateSlide(val slide: Int) : HealthConnectIntent()
  data class PrimaryAction(val label: HealthConnectAction) : HealthConnectIntent()
  data class SecondaryAction(val label: HealthConnectAction) : HealthConnectIntent()
}

/**
 * Reducer for handling Health Connect state updates.
 */
class HealthConnectReducer : IReducer<HealthConnectUiState, HealthConnectIntent> {
    override fun reduce(state: HealthConnectUiState, intent: HealthConnectIntent): HealthConnectUiState {
        return when (intent) {

            HealthConnectIntent.ConnectSuccess -> {
                state.copy(
                    isLoading = false,
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
