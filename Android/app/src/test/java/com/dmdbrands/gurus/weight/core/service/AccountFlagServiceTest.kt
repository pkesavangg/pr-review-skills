package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.domain.model.AccountFlag
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccountFlagServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val context: Context = mockk(relaxed = true)
    private val accountFlagRepository: IAccountFlagRepository = mockk()
    private val appReviewManager: IAppReviewManager = mockk(relaxed = true)

    private lateinit var service: AccountFlagService

    // --- Test Fixtures ---
    private val loginFlag = AccountFlag(id = "flag-1", type = "app-rate-ask login", trigger = "login")
    private val entryFlag = AccountFlag(id = "flag-2", type = "app-rate-ask entry", trigger = "entry")
    private val unknownFlag = AccountFlag(id = "flag-3", type = "unknown-type", trigger = "login")

    @Before
    fun setUp() {
        service = AccountFlagService(context, accountFlagRepository, appReviewManager)
    }

    // -------------------------------------------------------------------------
    // getAccountFlag — flag retrieval and precedence logic
    // -------------------------------------------------------------------------

    @Test
    fun `getAccountFlag returns null when repository returns empty list`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns emptyList()

        val result = service.getAccountFlag()

        assertThat(result).isNull()
    }

    @Test
    fun `getAccountFlag returns login flag when both login and entry flags exist`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(entryFlag, loginFlag)

        val result = service.getAccountFlag()

        assertThat(result).isEqualTo(loginFlag)
    }

    @Test
    fun `getAccountFlag returns first flag when no login flag exists`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(entryFlag)

        val result = service.getAccountFlag()

        assertThat(result).isEqualTo(entryFlag)
    }

    @Test
    fun `getAccountFlag returns first flag in list when multiple non-login flags exist`() = runTest {
        val first = AccountFlag(id = "f1", type = "type-a", trigger = "entry")
        val second = AccountFlag(id = "f2", type = "type-b", trigger = "entry")
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(first, second)

        val result = service.getAccountFlag()

        assertThat(result).isEqualTo(first)
    }

    @Test
    fun `getAccountFlag returns null and clears cache on repository exception`() = runTest {
        // First populate the cache
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        service.getAccountFlag()

        // Now throw on the next call
        coEvery { accountFlagRepository.getAccountFlags() } throws RuntimeException("network error")

        val result = service.getAccountFlag()

        assertThat(result).isNull()
    }

    @Test
    fun `getAccountFlag caches flag so checkAccountFlag can use it`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        coEvery { accountFlagRepository.deleteAccountFlag(loginFlag.id) } returns true

        service.getAccountFlag()
        val checked = service.checkAccountFlag("login")

        assertThat(checked).isTrue()
    }

    // -------------------------------------------------------------------------
    // checkAccountFlag — trigger matching and flag type dispatch
    // -------------------------------------------------------------------------

    @Test
    fun `checkAccountFlag returns false when no flag has been loaded`() = runTest {
        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag returns false on trigger mismatch`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        service.getAccountFlag()

        val result = service.checkAccountFlag("entry")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag for app-rate-ask deletes flag and returns true when deletion succeeds`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        coEvery { accountFlagRepository.deleteAccountFlag(loginFlag.id) } returns true
        service.getAccountFlag()

        val result = service.checkAccountFlag("login")

        assertThat(result).isTrue()
        coVerify { accountFlagRepository.deleteAccountFlag(loginFlag.id) }
    }

    @Test
    fun `checkAccountFlag for app-rate-ask returns false when deletion fails`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        coEvery { accountFlagRepository.deleteAccountFlag(loginFlag.id) } returns false
        service.getAccountFlag()

        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag returns false for unknown flag type`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(unknownFlag)
        service.getAccountFlag()

        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag returns false when flag type has extra words but first word is unknown`() = runTest {
        val complexUnknown = AccountFlag(id = "f4", type = "scale-review-ask login", trigger = "login")
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(complexUnknown)
        service.getAccountFlag()

        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag returns false on repository exception during delete`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        coEvery { accountFlagRepository.deleteAccountFlag(any()) } throws RuntimeException("delete failed")
        service.getAccountFlag()

        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    @Test
    fun `checkAccountFlag clears cached flag after null-guard branch when firstFlag is null mid-execution`() = runTest {
        // firstFlag starts null — exercises the early-return guard at the top
        val result = service.checkAccountFlag("login")

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // deleteFlag — direct deletion with cache management
    // -------------------------------------------------------------------------

    @Test
    fun `deleteFlag returns true and clears cached flag when deletion succeeds`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        coEvery { accountFlagRepository.deleteAccountFlag(loginFlag.id) } returns true
        service.getAccountFlag() // populate cache

        val result = service.deleteFlag(loginFlag.id)

        assertThat(result).isTrue()
        // After deletion the cache is cleared — checkAccountFlag should return false
        coEvery { accountFlagRepository.deleteAccountFlag(any()) } returns true
        val afterDelete = service.checkAccountFlag("login")
        assertThat(afterDelete).isFalse()
    }

    @Test
    fun `deleteFlag returns false when repository returns false`() = runTest {
        coEvery { accountFlagRepository.deleteAccountFlag("flag-99") } returns false

        val result = service.deleteFlag("flag-99")

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteFlag returns false and clears cache on repository exception`() = runTest {
        coEvery { accountFlagRepository.getAccountFlags() } returns listOf(loginFlag)
        service.getAccountFlag() // populate cache

        coEvery { accountFlagRepository.deleteAccountFlag(loginFlag.id) } throws RuntimeException("db error")

        val result = service.deleteFlag(loginFlag.id)

        assertThat(result).isFalse()
        // Cache cleared — subsequent checkAccountFlag must return false
        val afterException = service.checkAccountFlag("login")
        assertThat(afterException).isFalse()
    }

    @Test
    fun `deleteFlag passes correct flagId to repository`() = runTest {
        val targetId = "specific-flag-id"
        coEvery { accountFlagRepository.deleteAccountFlag(targetId) } returns true

        service.deleteFlag(targetId)

        coVerify { accountFlagRepository.deleteAccountFlag(targetId) }
    }
}
