package com.greatergoods.meapp.features.integration.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.features.integration.model.HealthConnectIntent
import com.greatergoods.meapp.features.integration.model.HealthConnectReducer
import com.greatergoods.meapp.features.integration.model.HealthConnectUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing Health Connect integration state and handling user intents.
 */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
) : ViewModel() {
    private val _state = MutableStateFlow(HealthConnectUiState())
    val state: StateFlow<HealthConnectUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableStateFlow<Intent?>(null)
    val navigationEvent: StateFlow<Intent?> = _navigationEvent.asStateFlow()

    /**
     * Handles user intents and updates state using the reducer.
     */
    fun handleIntent(intent: HealthConnectIntent) {
        viewModelScope.launch {
            when (intent) {
                HealthConnectIntent.Connect -> {
                    // Update state to loading
                    updateState(intent)
                    try {
                        // TODO: Implement actual Health Connect integration logic
                        // For now, simulate success
                        updateState(HealthConnectIntent.ConnectSuccess)
                    } catch (e: Exception) {
                        updateState(HealthConnectIntent.ConnectError)
                    }
                }
                else -> updateState(intent)
            }
        }
    }

    /**
     * Updates state using the reducer.
     */
    private fun updateState(intent: HealthConnectIntent) {
        _state.update { currentState ->
            HealthConnectReducer.reduce(currentState, intent)
        }
    }

    /**
     * Clears the navigation event after handling
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }
}

