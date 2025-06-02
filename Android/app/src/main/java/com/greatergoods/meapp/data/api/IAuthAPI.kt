package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.LogoutRequest
import com.greatergoods.meapp.domain.model.api.auth.PasswordResetRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface IAuthAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val LOGIN = "login"
        private const val LOGOUT = "logout"
        private const val PASSWORD_RESET = "password-reset/request"
        private const val REFRESH_TOKEN = "refresh-token"
    }

    @POST(ACCOUNT + LOGIN)
    suspend fun login(
        @Body credentials: LoginRequest,
    ): LoginResponse

    @POST(REFRESH_TOKEN)
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest,
    ): RefreshTokenResponse

    @POST(ACCOUNT + LOGOUT)
    suspend fun logout(
        @Body request: LogoutRequest,
    )

    @POST(ACCOUNT + PASSWORD_RESET)
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest,
    )
}
