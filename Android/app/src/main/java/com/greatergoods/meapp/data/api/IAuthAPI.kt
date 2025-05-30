package com.greatergoods.meapp.data.api

import LoginRequest
import LogoutRequest
import com.greatergoods.meapp.domain.model.api.*
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import retrofit2.http.*

interface IAuthAPI {
    companion object {
        const val ACCOUNT = "account"
        const val LOGIN = "/login"
        const val LOGOUT = "/logout"
        const val PASSWORD_RESET = "/password-reset/request"
        const val REFRESH_TOKEN = "/refresh-token"
    }

    @POST("$ACCOUNT$LOGIN")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    @POST(REFRESH_TOKEN)
    suspend fun refreshToken(@Body request: Map<String, String>): RefreshTokenResponse

    @POST("$ACCOUNT$LOGOUT")
    suspend fun logout(@Body request: LogoutRequest): Unit

    @POST("$ACCOUNT$PASSWORD_RESET")
    suspend fun requestPasswordReset(@Body data: Map<String, String>): Unit
}
