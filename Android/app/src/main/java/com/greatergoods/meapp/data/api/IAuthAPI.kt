package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.LogoutRequest
import com.greatergoods.meapp.domain.model.api.auth.PasswordResetRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * API interface for authentication and account management.
 * Supports token-based authentication for different accounts using X-Account-ID header.
 */
interface IAuthAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val LOGIN = "login"
        private const val LOGOUT = "logout"
        private const val PASSWORD_RESET = "password-reset/request"
        private const val REFRESH_TOKEN = "refresh-token"
        private const val PASSWORD_UPDATE = "password"
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
    suspend fun logoutWithToken(
        @Body request: LogoutRequest,
        @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String
    )

    @POST(ACCOUNT + PASSWORD_RESET)
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest,
    ): Response<Unit>

    @POST(ACCOUNT)
    suspend fun createAccount(
        @Body request: CreateAccountRequest
    ): LoginResponse

    @GET(ACCOUNT)
    suspend fun getAccountWithToken(
        @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String
    ): AccountInfo

    @PUT(ACCOUNT + PASSWORD_UPDATE)
    suspend fun updatePassword(
        @Body request: Map<String, String>
    ): Map<String, Any>

    @DELETE(ACCOUNT)
    suspend fun deleteAccount(): Map<String, Any>
}
