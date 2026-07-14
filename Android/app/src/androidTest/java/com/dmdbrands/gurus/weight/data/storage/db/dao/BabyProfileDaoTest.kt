package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the MOB-1476 offline-baby id-remap. These prove the risky data-integrity
 * bits against a real (bundled) SQLite with foreign keys enabled: the temp→server PK remap must NOT
 * cascade-delete a baby's entries (MOB-598), and refresh()'s reconcile-delete must spare not-yet-
 * synced offline babies.
 */
@RunWith(AndroidJUnit4::class)
class BabyProfileDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var babyProfileDao: BabyProfileDao
    private lateinit var babyEntryDao: BabyEntryDao
    private lateinit var entryDao: EntryDao
    private lateinit var accountDao: AccountDao

    private val accountId = "acc-1"

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        babyProfileDao = db.babyProfileDao()
        babyEntryDao = db.babyEntryDao()
        entryDao = db.entryDao()
        accountDao = db.accountDao()
        accountDao.insertAccount(DaoTestFixtures.account(id = accountId))
    }

    @After
    fun tearDown() = db.close()

    private fun baby(
        id: String,
        name: String = "Mia",
        birthdate: String? = "2026-03-15",
        isSynced: Boolean = false,
        isDeleted: Boolean = false,
        existsOnServer: Boolean = false,
        activeBabyId: String? = null,
    ) = BabyProfileEntity(
        babyId = id,
        accountId = accountId,
        name = name,
        birthdate = birthdate,
        isSynced = isSynced,
        isDeleted = isDeleted,
        existsOnServer = existsOnServer,
        activeBabyId = activeBabyId,
    )

    /** Inserts a parent entry row + a baby_entry referencing [babyId] (baby_entry FKs to both). */
    private suspend fun insertBabyEntry(entryId: Long, babyId: String, weightDg: Int) {
        entryDao.insertEntryEntity(DaoTestFixtures.entryEntity(id = entryId, deviceType = "baby"))
        entryDao.insertBabyEntry(BabyEntryEntity(id = entryId, babyId = babyId, babyWeightDecigrams = weightDg))
    }

    @Test
    fun remapBabyId_keeps_entries_attached_and_does_not_cascade_delete() = runTest {
        babyProfileDao.insert(baby(id = "temp-1"))
        insertBabyEntry(entryId = 1L, babyId = "temp-1", weightDg = 34000)
        insertBabyEntry(entryId = 2L, babyId = "temp-1", weightDg = 35000)

        babyProfileDao.remapBabyId(tempId = "temp-1", serverId = "srv-1", accountId = accountId)

        // Baby row now carries the server id and is marked synced.
        assertThat(babyProfileDao.getById("temp-1")).isNull()
        val server = babyProfileDao.getById("srv-1")
        assertThat(server).isNotNull()
        assertThat(server!!.isSynced).isTrue()
        assertThat(server.existsOnServer).isTrue()

        // Both entries survived (cascade deleted 0) and now point at the server id.
        val underTemp = babyEntryDao.observeByBabyId("temp-1").first()
        val underServer = babyEntryDao.observeByBabyId("srv-1").first()
        assertThat(underTemp).isEmpty()
        assertThat(underServer).hasSize(2)
        assertThat(underServer.map { it.babyWeightDecigrams }).containsExactly(34000, 35000)
    }

    @Test
    fun remapBabyId_moves_active_baby_pointer() = runTest {
        babyProfileDao.insert(baby(id = "temp-1", activeBabyId = "temp-1"))

        babyProfileDao.remapBabyId("temp-1", "srv-1", accountId)

        assertThat(babyProfileDao.getActiveBabyId(accountId)).isEqualTo("srv-1")
    }

    @Test
    fun remapBabyId_is_idempotent_when_server_row_already_exists() = runTest {
        babyProfileDao.insert(baby(id = "temp-1"))
        insertBabyEntry(entryId = 1L, babyId = "temp-1", weightDg = 34000)
        // Server row already present (e.g. pulled by a refresh before the remap ran).
        babyProfileDao.insert(baby(id = "srv-1", isSynced = true, existsOnServer = true))

        babyProfileDao.remapBabyId("temp-1", "srv-1", accountId)

        assertThat(babyProfileDao.getById("temp-1")).isNull()
        assertThat(babyProfileDao.getById("srv-1")).isNotNull()
        assertThat(babyEntryDao.observeByBabyId("srv-1").first()).hasSize(1)
    }

    @Test
    fun reconcileDelete_spares_offline_baby_but_removes_stale_server_baby() = runTest {
        babyProfileDao.insert(baby(id = "temp-1", existsOnServer = false)) // offline, not yet synced
        babyProfileDao.insert(baby(id = "srv-stale", existsOnServer = true)) // server dropped it
        insertBabyEntry(entryId = 1L, babyId = "temp-1", weightDg = 34000)

        // Server returned only "srv-live"; keepIds excludes both local rows.
        babyProfileDao.deleteByAccountIdNotIn(accountId, listOf("srv-live"))

        // Offline baby survives (existsOnServer = 0) along with its entry; stale server baby is gone.
        assertThat(babyProfileDao.getById("temp-1")).isNotNull()
        assertThat(babyEntryDao.observeByBabyId("temp-1").first()).hasSize(1)
        assertThat(babyProfileDao.getById("srv-stale")).isNull()
    }

    @Test
    fun observeByAccountId_excludes_soft_deleted() = runTest {
        babyProfileDao.insert(baby(id = "srv-1", existsOnServer = true))
        babyProfileDao.insert(baby(id = "srv-2", existsOnServer = true, isDeleted = true))

        val visible = babyProfileDao.observeByAccountId(accountId).first()

        assertThat(visible.map { it.babyId }).containsExactly("srv-1")
    }
}
