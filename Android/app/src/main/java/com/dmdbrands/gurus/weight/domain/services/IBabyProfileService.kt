package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.coroutines.flow.Flow

interface IBabyProfileService {

    fun observeAll(): Flow<List<BabyProfile>>

    suspend fun save(profile: BabyProfile)

    suspend fun update(profile: BabyProfile)

    suspend fun delete(profileId: String)
}
