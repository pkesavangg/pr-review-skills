package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.api.integration.UserAccount

interface IIntegrationRepository {
    suspend fun getActiveAccountId(): String
    suspend fun fetchAccount(): UserAccount
    suspend fun removeIntegration(provider: String)
    suspend fun updateLocalAccount()
}
