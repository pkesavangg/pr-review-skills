package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.coroutines.flow.Flow

interface IBabyProfileService {

    fun observeAll(): Flow<List<BabyProfile>>

    /** Creates the baby (server + local mirror) and returns the persisted profile,
     * which carries the server-assigned id. */
    suspend fun save(profile: BabyProfile): BabyProfile

    suspend fun update(profile: BabyProfile)

    suspend fun delete(profileId: String)

    /** Fetches the latest baby list from the server and mirrors it into the local cache. */
    suspend fun refresh()
}
