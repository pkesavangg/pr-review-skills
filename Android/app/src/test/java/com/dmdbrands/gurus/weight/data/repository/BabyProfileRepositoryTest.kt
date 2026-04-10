package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BabyProfileRepositoryTest {

    companion object {
        private const val PROFILE_ID = "baby-1"
        private const val PROFILE_NAME = "Luna"
        private const val ACCOUNT_ID = "account-123"
        private const val BIRTHDATE = "2023-01-15"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var babyProfileDao: BabyProfileDao

    private lateinit var repository: BabyProfileRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = BabyProfileRepository(babyProfileDao)
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
        assertThat(result[0].birthdate).isEqualTo(BIRTHDATE)
        assertThat(result[0].accountId).isEqualTo(ACCOUNT_ID)
    }

    @Test
    fun `observeAll returns empty list when no profiles`() = runTest {
        coEvery { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.observeAll(ACCOUNT_ID).first()

        assertThat(result).isEmpty()
    }

    // ── save ────────────────────────────────────────────────────────────────────

    @Test
    fun `save delegates to dao insert`() = runTest {
        val profile = BabyProfile(id = PROFILE_ID, name = PROFILE_NAME, birthdate = BIRTHDATE, accountId = ACCOUNT_ID)

        repository.save(profile)

        coVerify {
            babyProfileDao.insert(
                BabyProfileEntity(babyId = PROFILE_ID, name = PROFILE_NAME, birthdate = BIRTHDATE, accountId = ACCOUNT_ID),
            )
        }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    fun `update delegates to dao update`() = runTest {
        val profile = BabyProfile(id = PROFILE_ID, name = "Updated", birthdate = BIRTHDATE, accountId = ACCOUNT_ID)

        repository.update(profile)

        coVerify {
            babyProfileDao.update(
                BabyProfileEntity(babyId = PROFILE_ID, name = "Updated", birthdate = BIRTHDATE, accountId = ACCOUNT_ID),
            )
        }
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    fun `delete delegates to dao delete`() = runTest {
        repository.delete(PROFILE_ID)

        coVerify { babyProfileDao.delete(PROFILE_ID) }
    }

    // ── getById ─────────────────────────────────────────────────────────────────

    @Test
    fun `getById returns mapped profile when found`() = runTest {
        val entity = BabyProfileEntity(babyId = PROFILE_ID, name = PROFILE_NAME, birthdate = BIRTHDATE, accountId = ACCOUNT_ID)
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns entity

        val result = repository.getById(PROFILE_ID)

        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(PROFILE_ID)
        assertThat(result?.name).isEqualTo(PROFILE_NAME)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { babyProfileDao.getById(PROFILE_ID) } returns null

        val result = repository.getById(PROFILE_ID)

        assertThat(result).isNull()
    }
}
