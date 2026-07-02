package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.coroutines.flow.Flow

interface IBabyProfileRepository {

    fun observeAll(accountId: String): Flow<List<BabyProfile>>

    /** Creates the baby on the server and mirrors it locally. Returns the persisted profile
     * (carrying the server-assigned id, which differs from any client-provided id). */
    suspend fun save(profile: BabyProfile): BabyProfile

    suspend fun update(profile: BabyProfile)

    suspend fun delete(profileId: String)

    suspend fun getById(profileId: String): BabyProfile?

    /**
     * Fetches the baby list from the server for [accountId] and mirrors it into
     * the local cache (reconcile + replace), preserving the current active baby.
     */
    suspend fun refresh(accountId: String)
}
