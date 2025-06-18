package com.greatergoods.meapp.features.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.model.SignupStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignupUiState(
    val signupData: SignupData = SignupData(),
    val currentStep: SignupStep = SignupStep.NAME,
    val isLoading: Boolean = false,
    val steps: List<SignupStep> = SignupStep.entries
) {
    val currentStepIndex: Int get() = steps.indexOf(currentStep)
    val isFirstStep: Boolean get() = currentStepIndex == 0
    val isLastStep: Boolean get() = currentStepIndex == steps.size - 1
    val showSkipButton: Boolean get() = currentStep == SignupStep.GOAL
    val progress: Float get() = (currentStepIndex + 1f) / steps.size
}

@HiltViewModel
class SignupViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun onDataChange(newSignupData: SignupData) {
        _uiState.update { it.copy(signupData = newSignupData) }
    }

    fun onStepChanged(step: SignupStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun onNext() {
        viewModelScope.launch {
            if (_uiState.value.isLastStep) {
                // Handle final account creation
                _uiState.update { it.copy(isLoading = true) }
                // TODO: Add signup logic
            } else {
                val nextIndex = (_uiState.value.currentStepIndex + 1).coerceAtMost(_uiState.value.steps.lastIndex)
                _uiState.update { it.copy(currentStep = _uiState.value.steps[nextIndex]) }
            }
        }
    }

    fun onBack() {
        viewModelScope.launch {
            val prevIndex = (_uiState.value.currentStepIndex - 1).coerceAtLeast(0)
            _uiState.update { it.copy(currentStep = _uiState.value.steps[prevIndex]) }
        }
    }
}
