package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IBabyAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.api.baby.toDomain
import com.dmdbrands.gurus.weight.domain.model.api.baby.toRequest
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Baby Profile repository following the "direct API + local-cache mirror" model:
 * each mutation hits `/v3/baby/` first and, only on success, mirrors the result
 * into Room. The Room-backed [observeAll] flow remains the single source of truth
 * for the UI.
 */
class BabyProfileRepository @Inject constructor(
    private val babyProfileDao: BabyProfileDao,
    private val babyApi: IBabyAPI,
) : IBabyProfileRepository {

    override fun observeAll(accountId: String): Flow<List<BabyProfile>> =
        babyProfileDao.observeByAccountId(accountId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun save(profile: BabyProfile) {
        try {
            val response = babyApi.createBaby(profile.toRequest())
            babyProfileDao.insert(response.toDomain(profile.accountId).toEntity())
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to create baby profile", e)
            throw e
        }
    }

    override suspend fun update(profile: BabyProfile) {
        try {
            val response = babyApi.updateBaby(profile.id, profile.toRequest())
            babyProfileDao.update(response.toDomain(profile.accountId).toEntity())
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update baby profile", e)
            throw e
        }
    }

    override suspend fun delete(profileId: String) {
        try {
            babyApi.deleteBaby(profileId)
            babyProfileDao.delete(profileId)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete baby profile", e)
            throw e
        }
    }

    override suspend fun getById(profileId: String): BabyProfile? =
        babyProfileDao.getById(profileId)?.toDomain()

    override suspend fun refresh(accountId: String) {
        try {
            val activeBabyId = babyProfileDao.getActiveBabyId(accountId)
            val remote = babyApi.getBabies().toDomain(accountId)
            babyProfileDao.deleteByAccountIdNotIn(accountId, remote.map { it.id })
            remote.forEach { babyProfileDao.insert(it.toEntity()) }
            // REPLACE wipes the denormalised activeBabyId column; restore it if still valid.
            if (activeBabyId != null && remote.any { it.id == activeBabyId }) {
                babyProfileDao.setActiveBabyId(accountId, activeBabyId)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to refresh baby profiles", e)
            throw e
        }
    }

    private fun BabyProfileEntity.toDomain() = BabyProfile(
        id = babyId,
        accountId = accountId,
        name = name,
        birthdate = birthdate,
        sex = sex,
        birthWeightDecigrams = birthWeightDecigrams,
        birthLengthMillimeters = birthLengthMillimeters,
        isBorn = isBorn,
        isOwnedByAccount = isOwnedByAccount,
        permissions = permissions,
        createdAt = createdAt,
        dueDate = dueDate,
        lastUpdated = lastUpdated,
        isSynced = isSynced,
        isDeleted = isDeleted,
        activeBabyId = activeBabyId,
    )

    private fun BabyProfile.toEntity() = BabyProfileEntity(
        babyId = id,
        accountId = accountId,
        name = name,
        birthdate = birthdate,
        sex = sex,
        birthWeightDecigrams = birthWeightDecigrams,
        birthLengthMillimeters = birthLengthMillimeters,
        isBorn = isBorn,
        isOwnedByAccount = isOwnedByAccount,
        permissions = permissions,
        createdAt = createdAt,
        dueDate = dueDate,
        lastUpdated = lastUpdated,
        isSynced = isSynced,
        isDeleted = isDeleted,
        activeBabyId = activeBabyId,
    )

    companion object {
        private const val TAG = "BabyProfileRepository"
    }
}
