package com.greatergoods.meapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authAPI: IAuthAPI,
    ) : ViewModel() {
        private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
        val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

        private val _refreshTokenState = MutableStateFlow<RefreshTokenState>(RefreshTokenState.Initial)
        val refreshTokenState: StateFlow<RefreshTokenState> = _refreshTokenState.asStateFlow()

        fun login(
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _loginState.value = LoginState.Loading
                try {
                    val response = authAPI.login(LoginRequest(email, password))
                    _loginState.value = LoginState.Success(response)
                } catch (e: Exception) {
                    _loginState.value = LoginState.Error(e.message ?: "Login failed")
                }
            }
        }

        fun refreshToken(refreshToken: String) {
            viewModelScope.launch {
                _refreshTokenState.value = RefreshTokenState.Loading
                try {
                    val response = authAPI.refreshToken(RefreshTokenRequest(refreshToken))
                    _refreshTokenState.value = RefreshTokenState.Success(response)
                } catch (e: Exception) {
                    _refreshTokenState.value = RefreshTokenState.Error(e.message ?: "Token refresh failed")
                }
            }
        }

        sealed class LoginState {
            object Initial : LoginState()

            object Loading : LoginState()

            data class Success(
                val response: LoginResponse,
            ) : LoginState()

            data class Error(
                val message: String,
            ) : LoginState()
        }

        sealed class RefreshTokenState {
            object Initial : RefreshTokenState()

            object Loading : RefreshTokenState()

            data class Success(
                val response: RefreshTokenResponse,
            ) : RefreshTokenState()

            data class Error(
                val message: String,
            ) : RefreshTokenState()
        }
    }
