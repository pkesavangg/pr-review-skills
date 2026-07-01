package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IBabyAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.api.baby.BabyResponse
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
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

    private lateinit var repository: BabyProfileRepository

    private fun profile(id: String = PROFILE_ID, name: String = PROFILE_NAME) =
        BabyProfile(id = id, name = name, birthdate = BIRTHDATE, accountId = ACCOUNT_ID)

    private fun response(id: String = PROFILE_ID, name: String = PROFILE_NAME) =
        BabyResponse(id = id, name = name, birthdate = BIRTHDATE)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = BabyProfileRepository(babyProfileDao, babyApi)
    }

    // ── observeAll ──────────────────────────────────────────────────────────────

    @Test
    fun `observeAll returns mapped domain models`() = runTest {
        val entities = listOf(
            BabyProfileEntity(babyId = PROFILE_ID, name = PROFILE_NAME, birthdate = BIRTHDATE, accountId = ACCOUNT_ID),
        )
        coEvery { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(entities)

        val result = repository.observeAll(ACCOUNT_ID).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(PROFILE_ID)
        assertThat(result[0].name).isEqualTo(PROFILE_NAME)
    }

    @Test
    fun `observeAll returns empty list when no profiles`() = runTest {
        coEvery { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.observeAll(ACCOUNT_ID).first()

        assertThat(result).isEmpty()
    }

    // ── save ────────────────────────────────────────────────────────────────────

    @Test
    fun `save posts to api then mirrors response into dao`() = runTest {
        coEvery { babyApi.createBaby(any()) } returns response()

        repository.save(profile())

        coVerifyOrder {
            babyApi.createBaby(any())
            babyProfileDao.insert(any())
        }
    }

    @Test
    fun `save mirrors server response with synced flag`() = runTest {
        coEvery { babyApi.createBaby(any()) } returns response(id = "server-id")

        repository.save(profile(id = "local-temp-id"))

        coVerify {
            babyProfileDao.insert(
                match<BabyProfileEntity> { it.babyId == "server-id" && it.isSynced },
            )
        }
    }

    @Test
    fun `save returns the persisted profile carrying the server-assigned id`() = runTest {
        coEvery { babyApi.createBaby(any()) } returns response(id = "server-id")

        val result = repository.save(profile(id = "local-temp-id"))

        // Callers rely on this returned id (not the client id) to set the active baby.
        assertThat(result.id).isEqualTo("server-id")
    }

    @Test
    fun `save rethrows and skips local write when api fails`() {
        coEvery { babyApi.createBaby(any()) } throws IOException("network")

        assertThrows(IOException::class.java) {
            runTest { repository.save(profile()) }
        }
        coVerify(exactly = 0) { babyProfileDao.insert(any()) }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    fun `update puts to api then mirrors response into dao`() = runTest {
        coEvery { babyApi.updateBaby(PROFILE_ID, any()) } returns response(name = "Updated")

        repository.update(profile(name = "Updated"))

        coVerifyOrder {
            babyApi.updateBaby(PROFILE_ID, any())
            babyProfileDao.update(any())
        }
    }

    @Test
    fun `update rethrows and skips local write when api fails`() {
        coEvery { babyApi.updateBaby(PROFILE_ID, any()) } throws IOException("network")

        assertThrows(IOException::class.java) {
            runTest { repository.update(profile()) }
        }
        coVerify(exactly = 0) { babyProfileDao.update(any()) }
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    fun `delete calls api then dao`() = runTest {
        coEvery { babyApi.deleteBaby(PROFILE_ID) } returns Unit

        repository.delete(PROFILE_ID)

        coVerifyOrder {
            babyApi.deleteBaby(PROFILE_ID)
            babyProfileDao.delete(PROFILE_ID)
        }
    }

    @Test
    fun `delete rethrows and skips local delete when api fails`() {
        coEvery { babyApi.deleteBaby(PROFILE_ID) } throws IOException("network")

        assertThrows(IOException::class.java) {
            runTest { repository.delete(PROFILE_ID) }
        }
        coVerify(exactly = 0) { babyProfileDao.delete(PROFILE_ID) }
    }

    // ── refresh ───────────────────────────────────────────────────────────────────

    @Test
    fun `refresh replaces local cache with server list`() = runTest {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns null
        coEvery { babyApi.getBabies() } returns listOf(response(id = "a"), response(id = "b"))

        repository.refresh(ACCOUNT_ID)

        coVerify { babyProfileDao.deleteByAccountIdNotIn(ACCOUNT_ID, listOf("a", "b")) }
        coVerify { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "a" }) }
        coVerify { babyProfileDao.insert(match<BabyProfileEntity> { it.babyId == "b" }) }
    }

    @Test
    fun `refresh restores active baby id when still present`() = runTest {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns "a"
        coEvery { babyApi.getBabies() } returns listOf(response(id = "a"))

        repository.refresh(ACCOUNT_ID)

        coVerify { babyProfileDao.setActiveBabyId(ACCOUNT_ID, "a") }
    }

    @Test
    fun `refresh does not restore active baby id when no longer present`() = runTest {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns "gone"
        coEvery { babyApi.getBabies() } returns listOf(response(id = "a"))

        repository.refresh(ACCOUNT_ID)

        coVerify(exactly = 0) { babyProfileDao.setActiveBabyId(ACCOUNT_ID, any()) }
    }

    @Test
    fun `refresh rethrows when api fails`() {
        coEvery { babyProfileDao.getActiveBabyId(ACCOUNT_ID) } returns null
        coEvery { babyApi.getBabies() } throws IOException("network")

        assertThrows(IOException::class.java) {
            runTest { repository.refresh(ACCOUNT_ID) }
        }
    }

    // ── getById ─────────────────────────────────────────────────────────────────

    @Test
    fun `getById returns mapped profile when found`() = runTest {
        val entity = BabyProfileEntity(babyId = PROFILE_ID, name = PROFILE_NAME, birthdate = BIRTHDATE, accountId = ACCOUNT_ID)
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity

        val result = repository.getById(PROFILE_ID)

        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(PROFILE_ID)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns null

        val result = repository.getById(PROFILE_ID)

        assertThat(result).isNull()
    }
}
