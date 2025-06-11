package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API for refreshing tokens (used by TokenAuthenticator).
 */
interface RefreshTokenAPI {
    @POST("refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse
} 