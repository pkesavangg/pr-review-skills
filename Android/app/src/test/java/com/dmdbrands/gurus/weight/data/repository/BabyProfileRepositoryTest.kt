package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IBabyAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.api.baby.BabyResponse
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BabyProfileRepositoryTest {

    companion object {
        private const val PROFILE_ID = "baby-1"
        private const val PROFILE_NAME = "Luna"
        private const val ACCOUNT_ID = "account-123"
        private const val BIRTHDATE = "2023-01-15"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var babyProfileDao: BabyProfileDao

    @MockK
    private lateinit var babyApi: IBabyAPI

    @MockK(relaxUnitFun = true)
    private lateinit var productSelectionRepository: IProductSelectionRepository

    private lateinit var repository: BabyProfileRepository

    private fun profile(id: String = PROFILE_ID, name: String = PROFILE_NAME) =
        BabyProfile(id = id, name = name, birthdate = BIRTHDATE, accountId = ACCOUNT_ID)

    private fun response(id: String = PROFILE_ID, name: String = PROFILE_NAME) =
        BabyResponse(id = id, name = name, birthdate = BIRTHDATE)

    private fun entity(
        id: String = PROFILE_ID,
        name: String = PROFILE_NAME,
        isSynced: Boolean = false,
        isDeleted: Boolean = false,
        existsOnServer: Boolean = false,
    ) = BabyProfileEntity(
        babyId = id,
        name = name,
        birthdate = BIRTHDATE,
        accountId = ACCOUNT_ID,
        isSynced = isSynced,
        isDeleted = isDeleted,
        existsOnServer = existsOnServer,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf(null)
        repository = BabyProfileRepository(babyProfileDao, babyApi, productSelectionRepository)
    }

    // ── observeAll ──────────────────────────────────────────────────────────────

    @Test
    fun `observeAll returns mapped domain models`() = runTest {
        coEvery { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(listOf(entity()))

        val result = repository.observeAll(ACCOUNT_ID).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(PROFILE_ID)
    }

    // ── save (local-first) ────────────────────────────────────────────────────────

    @Test
    fun `save inserts locally first then posts and remaps to server id`() = runTest {
        coEvery { babyApi.createBaby(any()) } returns response(id = "server-id")

        repository.save(profile(id = "local-temp-id"))

        coVerifyOrder {
            babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "local-temp-id" && !it.isSynced && !it.existsOnServer })
            babyApi.createBaby(any())
            babyProfileDao.remapBabyId("local-temp-id", "server-id", ACCOUNT_ID)
        }
    }

    @Test
    fun `save returns the persisted profile carrying the server-assigned id`() = runTest {
        coEvery { babyApi.createBaby(any()) } returns response(id = "server-id")

        val result = repository.save(profile(id = "local-temp-id"))

        assertThat(result.id).isEqualTo("server-id")
    }

    @Test
    fun `save keeps local row and does not throw when offline`() = runTest {
        coEvery { babyApi.createBaby(any()) } throws IOException("network")

        val result = repository.save(profile(id = "local-temp-id"))

        // Local insert stays; no remap; returns the local (unsynced) profile.
        coVerify { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "local-temp-id" && !it.isSynced }) }
        coVerify(exactly = 0) { babyProfileDao.remapBabyId(any(), any(), any()) }
        assertThat(result.id).isEqualTo("local-temp-id")
        assertThat(result.isSynced).isFalse()
    }

    // ── update (local-first) ───────────────────────────────────────────────────────

    @Test
    fun `update on server-known baby writes locally then PUTs and marks synced`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(isSynced = true, existsOnServer = true)
        coEvery { babyApi.updateBaby(PROFILE_ID, any()) } returns response(name = "Updated")

        repository.update(profile(name = "Updated"))

        coVerifyOrder {
            babyProfileDao.update(match<BabyProfileEntity> { !it.isSynced && it.existsOnServer })
            babyApi.updateBaby(PROFILE_ID, any())
            babyProfileDao.update(match<BabyProfileEntity> { it.isSynced && it.existsOnServer })
        }
    }

    @Test
    fun `update on not-yet-synced baby skips PUT and stays pending create`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(existsOnServer = false)

        repository.update(profile(name = "Updated"))

        coVerify { babyProfileDao.update(match<BabyProfileEntity> { !it.isSynced && !it.existsOnServer }) }
        coVerify(exactly = 0) { babyApi.updateBaby(any(), any()) }
    }

    @Test
    fun `update does not throw when offline and leaves row pending`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(existsOnServer = true)
        coEvery { babyApi.updateBaby(PROFILE_ID, any()) } throws IOException("network")

        repository.update(profile(name = "Updated"))

        // First write marks pending; no synced write happens.
        coVerify { babyProfileDao.update(match<BabyProfileEntity> { !it.isSynced }) }
        coVerify(exactly = 0) { babyProfileDao.update(match<BabyProfileEntity> { it.isSynced }) }
    }

    // ── delete (local-first) ───────────────────────────────────────────────────────

    @Test
    fun `delete of server-known baby soft-deletes then DELETEs and purges`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(isSynced = true, existsOnServer = true)
        coEvery { babyApi.deleteBaby(PROFILE_ID) } returns Unit

        repository.delete(PROFILE_ID)

        coVerifyOrder {
            babyProfileDao.update(match<BabyProfileEntity> { it.isDeleted && !it.isSynced })
            babyApi.deleteBaby(PROFILE_ID)
            babyProfileDao.purgeBabyAndEntries(PROFILE_ID)
        }
    }

    @Test
    fun `delete of never-synced baby purges locally without server call`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(existsOnServer = false)

        repository.delete(PROFILE_ID)

        coVerify { babyProfileDao.purgeBabyAndEntries(PROFILE_ID) }
        coVerify(exactly = 0) { babyApi.deleteBaby(any()) }
    }

    @Test
    fun `delete does not throw when offline and keeps soft-deleted row for sync`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity(isSynced = true, existsOnServer = true)
        coEvery { babyApi.deleteBaby(PROFILE_ID) } throws IOException("network")

        repository.delete(PROFILE_ID)

        coVerify { babyProfileDao.update(match<BabyProfileEntity> { it.isDeleted && !it.isSynced }) }
        coVerify(exactly = 0) { babyProfileDao.purgeBabyAndEntries(PROFILE_ID) }
    }

    // ── refresh ───────────────────────────────────────────────────────────────────

    @Test
    fun `refresh inserts new server babies and reconcile-deletes`() = runTest {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns null
        coEvery { babyProfileDao.getById(any()) } returns null
        coEvery { babyApi.getBabies() } returns listOf(response(id = "a"), response(id = "b"))

        repository.refresh(ACCOUNT_ID)

        coVerify { babyProfileDao.deleteByAccountIdNotIn(ACCOUNT_ID, listOf("a", "b")) }
        coVerify { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "a" && it.existsOnServer }) }
        coVerify { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "b" }) }
    }

    @Test
    fun `refresh does not clobber a locally-pending baby`() = runTest {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns null
        coEvery { babyProfileDao.getById("a") } returns entity(id = "a", isSynced = false, existsOnServer = true)
        coEvery { babyApi.getBabies() } returns listOf(response(id = "a"))

        repository.refresh(ACCOUNT_ID)

        // Pending local change must not be overwritten by server data.
        coVerify(exactly = 0) { babyProfileDao.update(match<BabyProfileEntity> { it.babyId == "a" }) }
        coVerify(exactly = 0) { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "a" }) }
    }

    // ── syncPendingBabies ───────────────────────────────────────────────────────────

    private fun stubEmptyRefresh() {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns null
        coEvery { babyProfileDao.getById(any()) } returns null
        coEvery { babyApi.getBabies() } returns emptyList()
    }

    @Test
    fun `syncPendingBabies is a no-op when nothing pending`() = runTest {
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns emptyList()

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify(exactly = 0) { babyApi.getBabies() }
        coVerify(exactly = 0) { babyApi.createBaby(any()) }
    }

    @Test
    fun `syncPendingBabies posts a pending create then remaps`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns listOf(entity(id = "temp", existsOnServer = false))
        coEvery { babyProfileDao.findServerBabyByNameAndBirthdate(ACCOUNT_ID, PROFILE_NAME, BIRTHDATE) } returns null
        coEvery { babyApi.createBaby(any()) } returns response(id = "srv")

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify { babyApi.createBaby(any()) }
        coVerify { babyProfileDao.remapBabyId("temp", "srv", ACCOUNT_ID) }
    }

    @Test
    fun `syncPendingBabies remaps the persisted product selection off the temp id`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns listOf(entity(id = "temp", existsOnServer = false))
        coEvery { babyProfileDao.findServerBabyByNameAndBirthdate(ACCOUNT_ID, PROFILE_NAME, BIRTHDATE) } returns null
        coEvery { babyApi.createBaby(any()) } returns response(id = "srv")
        // The dashboard's persisted selection still points at the temp id.
        coEvery { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf("temp")

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify { productSelectionRepository.saveSelectedBabyProfileId("srv") }
    }

    @Test
    fun `syncPendingBabies adopts an existing server twin instead of re-posting`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns listOf(entity(id = "temp", existsOnServer = false))
        coEvery { babyProfileDao.findServerBabyByNameAndBirthdate(ACCOUNT_ID, PROFILE_NAME, BIRTHDATE) } returns
            entity(id = "srv-existing", isSynced = true, existsOnServer = true)

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify(exactly = 0) { babyApi.createBaby(any()) }
        coVerify { babyProfileDao.remapBabyId("temp", "srv-existing", ACCOUNT_ID) }
    }

    @Test
    fun `syncPendingBabies PUTs a pending edit and marks synced`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns listOf(entity(isSynced = false, existsOnServer = true))
        coEvery { babyApi.updateBaby(PROFILE_ID, any()) } returns response()

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify { babyApi.updateBaby(PROFILE_ID, any()) }
        coVerify { babyProfileDao.update(match<BabyProfileEntity> { it.isSynced }) }
    }

    @Test
    fun `syncPendingBabies DELETEs a synced soft-delete then purges`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns
            listOf(entity(isDeleted = true, isSynced = false, existsOnServer = true))
        coEvery { babyApi.deleteBaby(PROFILE_ID) } returns Unit

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify { babyApi.deleteBaby(PROFILE_ID) }
        coVerify { babyProfileDao.purgeBabyAndEntries(PROFILE_ID) }
    }

    @Test
    fun `syncPendingBabies purges a never-synced soft-delete without server call`() = runTest {
        stubEmptyRefresh()
        coEvery { babyProfileDao.getUnsynced(ACCOUNT_ID) } returns
            listOf(entity(isDeleted = true, isSynced = false, existsOnServer = false))

        repository.syncPendingBabies(ACCOUNT_ID)

        coVerify { babyProfileDao.purgeBabyAndEntries(PROFILE_ID) }
        coVerify(exactly = 0) { babyApi.deleteBaby(any()) }
    }

    // ── getById ─────────────────────────────────────────────────────────────────

    @Test
    fun `getById returns mapped profile when found`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity()

        val result = repository.getById(PROFILE_ID)

        assertThat(result?.id).isEqualTo(PROFILE_ID)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns null

        assertThat(repository.getById(PROFILE_ID)).isNull()
    }
}
