package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.coroutines.flow.Flow

interface IBabyProfileRepository {

    fun observeAll(accountId: String): Flow<List<BabyProfile>>

    suspend fun save(profile: BabyProfile)

    suspend fun update(profile: BabyProfile)

    suspend fun delete(profileId: String)

    suspend fun getById(profileId: String): BabyProfile?
}
