package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IBabyAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.api.baby.toDomain
import com.dmdbrands.gurus.weight.domain.model.api.baby.toRequest
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val productSelectionRepository: IProductSelectionRepository,
) : IBabyProfileRepository {

    /**
     * Cascade-safe id remap plus persisted-selection reconcile. The Room remap moves the baby row,
     * its entries, and the denormalised activeBabyId; this also moves the DataStore-persisted
     * product selection ([IProductSelectionRepository.saveSelectedBabyProfileId]) off the temp id so
     * the dashboard restores to this baby — not the first one — after sync / app reopen (MOB-1476).
     */
    private suspend fun remapBaby(tempId: String, serverId: String, accountId: String) {
        // Move the persisted selection FIRST: remapBabyId changes the baby row, which fires the
        // product-switcher's baby observer and re-derives the selection from the persisted id — so
        // that id must already be the server id when the observer reads it, or it falls back to the
        // first baby (MOB-1476).
        if (productSelectionRepository.observeSelectedBabyProfileId().first() == tempId) {
            productSelectionRepository.saveSelectedBabyProfileId(serverId)
        }
        babyProfileDao.remapBabyId(tempId, serverId, accountId)
    }

    override fun observeAll(accountId: String): Flow<List<BabyProfile>> =
        babyProfileDao.observeByAccountId(accountId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun save(profile: BabyProfile): BabyProfile {
        // Optimistic local-first insert: the baby row must exist immediately (with the client-UUID
        // id, isSynced = false, existsOnServer = false) so My Kids / dashboard update and
        // productTypes gains "baby" even offline (MOB-1476, building on MOB-1217's read side).
        val localEntity = profile.toEntity().copy(isSynced = false, existsOnServer = false)
        babyProfileDao.insert(localEntity)
        return try {
            // The server assigns its own id (the client id is not sent). Swap the client UUID for
            // the server id everywhere (profile + baby entries + activeBabyId) without the
            // baby_entry cascade wiping entries (MOB-598).
            val persisted = babyApi.createBaby(profile.toRequest()).toDomain(profile.accountId)
            remapBaby(profile.id, persisted.id, profile.accountId)
            persisted
        } catch (e: Exception) {
            // Offline / transient failure: keep the local row queued for syncPendingBabies();
            // do NOT throw — the create must succeed offline.
            AppLog.e(TAG, "Baby create deferred to offline sync", e)
            localEntity.toDomain()
        }
    }

    override suspend fun update(profile: BabyProfile) {
        // Preserve server-existence and the local-only activeBabyId pointer; the edit form doesn't
        // carry either, so writing the raw mapped entity would flip existsOnServer and wipe the
        // active baby (MOB-1476). A not-yet-synced offline create that gets edited stays a pending
        // CREATE (must not PUT a babyId the server never saw).
        val existing = babyProfileDao.getById(profile.id)
        val existsOnServer = existing?.existsOnServer ?: profile.existsOnServer
        val entity = profile.toEntity().copy(existsOnServer = existsOnServer, activeBabyId = existing?.activeBabyId)
        babyProfileDao.update(entity.copy(isSynced = false))
        if (!existsOnServer) return // syncPendingBabies will POST the edited values as a create
        try {
            babyApi.updateBaby(profile.id, profile.toRequest())
            babyProfileDao.update(entity.copy(isSynced = true))
        } catch (e: Exception) {
            AppLog.e(TAG, "Baby update deferred to offline sync", e)
        }
    }

    override suspend fun delete(profileId: String) {
        val existing = babyProfileDao.getById(profileId) ?: return
        if (!existing.existsOnServer) {
            // Never reached the server — just purge locally, nothing to sync.
            babyProfileDao.purgeBabyAndEntries(profileId)
            return
        }
        // Soft-delete + mark pending so it disappears from UI now; syncPendingBabies issues the
        // server DELETE and then hard-purges.
        babyProfileDao.update(existing.copy(isDeleted = true, isSynced = false))
        try {
            babyApi.deleteBaby(profileId)
            babyProfileDao.purgeBabyAndEntries(profileId)
        } catch (e: Exception) {
            AppLog.e(TAG, "Baby delete deferred to offline sync", e)
        }
    }

    override suspend fun getById(profileId: String): BabyProfile? =
        babyProfileDao.getById(profileId)?.toDomain()

    override suspend fun refresh(accountId: String) {
        try {
            val activeBabyId = babyProfileDao.getActiveBabyId(accountId)
            val remote = babyApi.getBabies().toDomain(accountId)
            babyProfileDao.deleteByAccountIdNotIn(accountId, remote.map { it.id })
            // Update existing profiles in place rather than INSERT-with-REPLACE. REPLACE deletes
            // and re-inserts the baby row, and baby_entry.babyId -> baby ON DELETE CASCADE would
            // wipe that baby's saved entries on every refresh, making them vanish from History
            // (MOB-598).
            remote.forEach { profile ->
                val entity = profile.toEntity()
                val local = babyProfileDao.getById(entity.babyId)
                when {
                    // Never clobber a locally-pending change (offline edit / soft-delete) with server
                    // data — the pending push (syncPendingBabies) is the source of truth until it lands.
                    local != null && !local.isSynced -> Unit
                    // Preserve the local-only activeBabyId pointer: the server never sends it, so
                    // writing the mapped entity (activeBabyId = null) would wipe the active baby and
                    // the dashboard/History would show no entries (MOB-1476).
                    local != null -> babyProfileDao.update(entity.copy(activeBabyId = local.activeBabyId))
                    else -> babyProfileDao.insert(entity)
                }
            }
            // update/REPLACE wipes the denormalised activeBabyId column; restore it if still valid.
            if (activeBabyId != null && remote.any { it.id == activeBabyId }) {
                babyProfileDao.setActiveBabyId(accountId, activeBabyId)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to refresh baby profiles", e)
            throw e
        }
    }

    override suspend fun syncPendingBabies(accountId: String) {
        val pending = babyProfileDao.getUnsynced(accountId)
        if (pending.isEmpty()) return
        // Pull the server list first so a create whose POST succeeded but whose remap never ran
        // (lost-reply) can be adopted by name+birthdate instead of POSTing a duplicate baby.
        runCatching { refresh(accountId) }
            .onFailure { AppLog.e(TAG, "Baby refresh before sync failed; proceeding with local state", it) }

        pending.forEach { baby ->
            try {
                when {
                    baby.isDeleted && baby.existsOnServer -> {
                        babyApi.deleteBaby(baby.babyId)
                        babyProfileDao.purgeBabyAndEntries(baby.babyId)
                    }
                    baby.isDeleted -> babyProfileDao.purgeBabyAndEntries(baby.babyId) // never synced → local purge
                    baby.existsOnServer -> {
                        babyApi.updateBaby(baby.babyId, baby.toDomain().toRequest())
                        babyProfileDao.update(baby.copy(isSynced = true))
                    }
                    else -> {
                        // CREATE: adopt an existing server twin (lost-reply) or POST a fresh one,
                        // then remap the client UUID to the server id.
                        val twin = babyProfileDao.findServerBabyByNameAndBirthdate(accountId, baby.name, baby.birthdate)
                        val serverId = twin?.babyId
                            ?: babyApi.createBaby(baby.toDomain().toRequest()).toDomain(accountId).id
                        remapBaby(baby.babyId, serverId, accountId)
                    }
                }
            } catch (e: Exception) {
                // Leave this baby pending (isSynced = false) for the next sync; don't block the rest.
                AppLog.e(TAG, "Failed to sync baby ${baby.babyId}; will retry", e)
            }
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
        existsOnServer = existsOnServer,
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
        existsOnServer = existsOnServer,
        activeBabyId = activeBabyId,
    )

    companion object {
        private const val TAG = "BabyProfileRepository"
    }
}
