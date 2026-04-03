package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BabyProfileRepository @Inject constructor(
    private val babyProfileDao: BabyProfileDao,
) : IBabyProfileRepository {

    override fun observeAll(accountId: String): Flow<List<BabyProfile>> =
        babyProfileDao.observeByAccountId(accountId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun save(profile: BabyProfile) {
        babyProfileDao.insert(profile.toEntity())
    }

    override suspend fun update(profile: BabyProfile) {
        babyProfileDao.update(profile.toEntity())
    }

    override suspend fun delete(profileId: String) {
        babyProfileDao.delete(profileId)
    }

    override suspend fun getById(profileId: String): BabyProfile? =
        babyProfileDao.getById(profileId)?.toDomain()

    private fun BabyProfileEntity.toDomain() = BabyProfile(
        id = id,
        accountId = accountId,
        name = name,
        isOwnedByAccount = isOwnedByAccount,
        babyPermissions = babyPermissions,
        birthDate = birthDate,
        dueDate = dueDate,
        isBorn = isBorn,
        biologicalSex = biologicalSex,
        birthWeightDecigrams = birthWeightDecigrams,
        birthLengthMillimeters = birthLengthMillimeters,
        lastUpdated = lastUpdated,
        isSynced = isSynced,
        isDeleted = isDeleted,
        activeBabyId = activeBabyId,
    )

    private fun BabyProfile.toEntity() = BabyProfileEntity(
        id = id,
        accountId = accountId,
        name = name,
        isOwnedByAccount = isOwnedByAccount,
        babyPermissions = babyPermissions,
        birthDate = birthDate,
        dueDate = dueDate,
        isBorn = isBorn,
        biologicalSex = biologicalSex,
        birthWeightDecigrams = birthWeightDecigrams,
        birthLengthMillimeters = birthLengthMillimeters,
        lastUpdated = lastUpdated,
        isSynced = isSynced,
        isDeleted = isDeleted,
        activeBabyId = activeBabyId,
    )
}
