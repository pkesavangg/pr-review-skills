package com.greatergoods.meapp.features.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
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

        private val _refreshTokenState =
            MutableStateFlow<RefreshTokenState>(RefreshTokenState.Initial)
        val refreshTokenState: StateFlow<RefreshTokenState> = _refreshTokenState.asStateFlow()

        private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Initial)
        val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

        fun login(
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _loginState.value = LoginState.Loading
                try {
                    val response = authAPI.login(LoginRequest(email, password))
                    // Store tokens in TokenManager

                    _loginState.value = LoginState.Success(response)
                    // After successful login, fetch profile to test token refresh
                    fetchProfile()
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
                    // Update tokens in TokenManager

                    _refreshTokenState.value = RefreshTokenState.Success(response)
                    // After token refresh, try fetching profile again
                    fetchProfile()
                } catch (e: Exception) {
                    _refreshTokenState.value = RefreshTokenState.Error(e.message ?: "Token refresh failed")
                }
            }
        }

        fun fetchProfile() {
            viewModelScope.launch {
                _profileState.value = ProfileState.Loading
                try {
                    val profile = authAPI.getProfile()
                    _profileState.value = ProfileState.Success(profile)
                } catch (e: Exception) {
                    _profileState.value = ProfileState.Error(e.message ?: "Failed to fetch profile")
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

        sealed class ProfileState {
            object Initial : ProfileState()

            object Loading : ProfileState()

            data class Success(
                val profile: ProfileUpdateRequest,
            ) : ProfileState()

            data class Error(
                val message: String,
            ) : ProfileState()
        }
    }
