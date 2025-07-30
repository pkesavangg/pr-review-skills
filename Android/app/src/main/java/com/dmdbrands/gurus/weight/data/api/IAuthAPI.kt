package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.LogoutRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.PasswordResetRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
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
        @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String,
    )

    @POST(ACCOUNT + PASSWORD_RESET)
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest,
    ): Response<Unit>

    @POST(ACCOUNT)
    suspend fun createAccount(
        @Body request: SignupRequest,
    ): LoginResponse

    @GET(ACCOUNT)
    suspend fun getAccountWithToken(
        @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String,
    ): AccountInfo

    @PUT(ACCOUNT + PASSWORD_UPDATE)
    suspend fun updatePassword(
        @Body request: Map<String, String>,
    ): Map<String, Any>

    @DELETE(ACCOUNT)
    suspend fun deleteAccount(): Map<String, Any>
}
