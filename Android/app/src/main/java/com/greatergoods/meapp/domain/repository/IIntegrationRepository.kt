package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.api.user.AccountInfo

interface IIntegrationRepository {
    suspend fun getAccount(accountId: String): AccountInfo
    suspend fun removeIntegration(provider: String)
    suspend fun updateLocalAccount()
}
