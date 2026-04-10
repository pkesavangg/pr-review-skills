package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BabyProfileServiceTest {

    companion object {
        private const val ACCOUNT_ID = "account-123"
        private const val PROFILE_ID = "baby-1"
        private const val PROFILE_NAME = "Luna"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var babyProfileRepository: IBabyProfileRepository

    @MockK
    private lateinit var accountRepository: IAccountRepository

    private lateinit var service: BabyProfileService

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        val account = mockk<Account>(relaxed = true) {
            every { id } returns ACCOUNT_ID
        }
        every { accountRepository.getActiveAccount() } returns flowOf(account)
        service = BabyProfileService(babyProfileRepository, accountRepository)
    }

    // ── observeAll ──────────────────────────────────────────────────────────────

    @Test
    fun `observeAll returns profiles for active account`() = runTest {
        val profiles = listOf(
            BabyProfile(id = PROFILE_ID, name = PROFILE_NAME, birthdate = null, accountId = ACCOUNT_ID),
        )
        every { babyProfileRepository.observeAll(ACCOUNT_ID) } returns flowOf(profiles)

        val result = service.observeAll().first()

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo(PROFILE_NAME)
    }

    @Test
    fun `observeAll returns empty when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)
        service = BabyProfileService(babyProfileRepository, accountRepository)

        val result = service.observeAll().first()

        assertThat(result).isEmpty()
    }

    // ── save ────────────────────────────────────────────────────────────────────

    @Test
    fun `save delegates to repository`() = runTest {
        val profile = BabyProfile(id = PROFILE_ID, name = PROFILE_NAME, birthdate = null, accountId = ACCOUNT_ID)

        service.save(profile)

        coVerify { babyProfileRepository.save(profile) }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    fun `update delegates to repository`() = runTest {
        val profile = BabyProfile(id = PROFILE_ID, name = "Updated", birthdate = null, accountId = ACCOUNT_ID)

        service.update(profile)

        coVerify { babyProfileRepository.update(profile) }
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    fun `delete delegates to repository`() = runTest {
        service.delete(PROFILE_ID)

        coVerify { babyProfileRepository.delete(PROFILE_ID) }
    }
}
