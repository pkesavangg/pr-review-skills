package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.integration.UserAccount
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface IIntegrationAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val INTEGRATIONS = "integrations/"
    }

    // Get account info, which includes integration status
    @GET(ACCOUNT)
    suspend fun getAccount(): UserAccount

    // Remove integration (Fitbit, MyFitnessPal, Under Armour, etc.)
    @DELETE("$INTEGRATIONS{provider}")
    suspend fun removeIntegration(@Path("provider") provider: String)
}
