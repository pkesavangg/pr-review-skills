package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.LogoutRequest
import com.greatergoods.meapp.domain.model.api.auth.PasswordResetRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.DELETE

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
    suspend fun logout(
        @Body request: LogoutRequest,
    )

    @POST(ACCOUNT + PASSWORD_RESET)
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest,
    ): Map<String, Any>

    @POST(ACCOUNT)
    suspend fun createAccount(
        @Body request: Map<String, String>
    ): Map<String, Any>

    @PUT(ACCOUNT + PASSWORD_UPDATE)
    suspend fun updatePassword(
        @Body request: Map<String, String>
    ): Map<String, Any>

    @DELETE(ACCOUNT)
    suspend fun deleteAccount(): Map<String, Any>

    //TODO: FOR TESTING PURPOSE LATER IT WILL BE REMOVED.
    @GET(ACCOUNT)
    suspend fun getProfile(): ProfileUpdateRequest
}
