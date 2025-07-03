package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.domain.repository.IDashboardRepository
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.proto.DashboardKey
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDashboardService for dashboard visible metrics management.
 */
@Singleton
class DashboardService @Inject constructor(
    private val dashboardRepository: IDashboardRepository
) : IDashboardService {

    private var accountId: String? = null

    /**
     * Sets the current account ID to be used by default in other methods.
     */
    override fun setAccountId(accountId: String) {
        this.accountId = accountId
    }

    /**
     * Gets a Flow of visible metric keys for the given account.
     * If accountId is null, uses the stored accountId.
     */
    override fun getVisibleKeys(accountId: String?): Flow<List<DashboardKey>> =
        dashboardRepository.getVisibleKeys(
            accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
        )

    /**
     * Updates the visible keys for the given account.
     * If accountId is null, uses the stored accountId.
     */
    override suspend fun updateVisibleKeys(accountId: String?, keys: List<DashboardKey>) =
        dashboardRepository.updateVisibleKeys(
            accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"), keys,
        )

    /**
     * Checks if the given accountId has a visible keys entry.
     * If accountId is null, uses the stored accountId.
     */
    override suspend fun hasVisibleKeys(accountId: String?): Boolean =
        dashboardRepository.hasVisibleKeys(
            accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
        )

    /**
     * Resets the visible keys for the given account to the default list.
     * If accountId is null, uses the stored accountId.
     */
    override suspend fun resetVisibleKeys(accountId: String?) =
        dashboardRepository.resetVisibleKeys(
            accountId ?: this.accountId ?: throw IllegalStateException("Account ID must be set"),
        )
}
