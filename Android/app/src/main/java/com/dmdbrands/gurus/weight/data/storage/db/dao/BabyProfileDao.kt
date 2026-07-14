package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BabyProfileEntity)

    @Update
    suspend fun update(profile: BabyProfileEntity)

    @Query("DELETE FROM baby WHERE babyId = :profileId")
    suspend fun delete(profileId: String)

    /** Non-deleted babies for [accountId]. Excludes soft-deleted rows (isDeleted = 1) that are
     * awaiting a server DELETE, so an offline delete disappears from UI / productTypes at once. */
    @Query("SELECT * FROM baby WHERE accountId = :accountId AND isDeleted = 0")
    fun observeByAccountId(accountId: String): Flow<List<BabyProfileEntity>>

    @Query("SELECT * FROM baby WHERE babyId = :profileId")
    suspend fun getById(profileId: String): BabyProfileEntity?

    /**
     * Deletes any local babies for [accountId] whose id is not in [keepIds] (refresh reconcile).
     * Guarded with `existsOnServer = 1` so a not-yet-synced offline baby (whose client-UUID id is
     * never in the server's list) is never reconcile-deleted before it syncs — which would also
     * cascade-wipe its baby entries (MOB-1476 / MOB-598).
     */
    @Query("DELETE FROM baby WHERE accountId = :accountId AND babyId NOT IN (:keepIds) AND existsOnServer = 1")
    suspend fun deleteByAccountIdNotIn(accountId: String, keepIds: List<String>)

    /** Pending baby mutations for [accountId] awaiting push to the server (create/edit/delete). */
    @Query("SELECT * FROM baby WHERE accountId = :accountId AND isSynced = 0")
    suspend fun getUnsynced(accountId: String): List<BabyProfileEntity>

    /**
     * Finds an already-synced server baby matching [name] + [birthdate] (idempotency fallback):
     * if a create POST succeeded but the app died before the id-remap, the next sync can adopt the
     * existing server baby instead of POSTing a duplicate (MOB-1476).
     */
    @Query(
        "SELECT * FROM baby WHERE accountId = :accountId AND name = :name AND birthdate IS :birthdate " +
            "AND existsOnServer = 1 AND isDeleted = 0 LIMIT 1",
    )
    suspend fun findServerBabyByNameAndBirthdate(
        accountId: String,
        name: String,
        birthdate: String?,
    ): BabyProfileEntity?

    /** Re-points every baby entry from [tempId] onto [serverId] (id-remap step). */
    @Query("UPDATE baby_entry SET babyId = :serverId WHERE babyId = :tempId")
    suspend fun reassignEntries(tempId: String, serverId: String)

    /**
     * Cascade-safe id remap for an offline-created baby: swaps its client-UUID [tempId] for the
     * server-assigned [serverId] without the `baby_entry → baby ON DELETE CASCADE` wiping its
     * entries (MOB-598). Ordered so the temp row is childless before it is deleted:
     *   1. insert (or reconcile) the server-id baby row  — both rows exist momentarily
     *   2. move all baby entries onto the server id       — children now point at the new row
     *   3. delete the temp row                            — childless, so cascade removes 0 entries
     *   4. re-point the active-baby pointer if it was the temp baby (kept consistent across all
     *      rows via setActiveBabyId, else a later refresh() nulls it and the baby's dashboard/
     *      History shows no entries even though they're intact — MOB-1476).
     * `wasActive` is read from the temp row's own denormalised column (robust vs a LIMIT-1 read
     * across possibly-inconsistent rows). Idempotent: no-ops if [tempId] is already gone.
     */
    @Transaction
    suspend fun remapBabyId(tempId: String, serverId: String, accountId: String) {
        if (tempId == serverId) return
        val temp = getById(tempId) ?: return
        val wasActive = temp.activeBabyId == tempId
        val reconciled = temp.copy(
            babyId = serverId,
            isSynced = true,
            existsOnServer = true,
            activeBabyId = if (wasActive) serverId else temp.activeBabyId,
        )
        if (getById(serverId) == null) insert(reconciled) else update(reconciled)
        reassignEntries(tempId, serverId)
        delete(tempId)
        if (wasActive) setActiveBabyId(accountId, serverId)
    }

    @Query("UPDATE baby SET activeBabyId = :activeBabyId WHERE accountId = :accountId")
    suspend fun setActiveBabyId(accountId: String, activeBabyId: String)

    @Query("SELECT activeBabyId FROM baby WHERE accountId = :accountId LIMIT 1")
    suspend fun getActiveBabyId(accountId: String): String?

    @Query("UPDATE baby SET activeBabyId = NULL WHERE accountId = :accountId")
    suspend fun clearActiveBabyId(accountId: String)
}
