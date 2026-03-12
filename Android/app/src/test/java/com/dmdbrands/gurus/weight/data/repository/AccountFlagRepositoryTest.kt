package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IAccountFlagAPI
import com.dmdbrands.gurus.weight.domain.model.api.review.AccountFlagResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AccountFlagRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var accountFlagAPI: IAccountFlagAPI

    private lateinit var repository: AccountFlagRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = AccountFlagRepository(accountFlagAPI)
    }

    // ── getAccountFlags ────────────────────────────────────────────────────────

    @Test
    fun `getAccountFlags returns mapped flags on success`() = runTest {
        val apiFlags = listOf(
            AccountFlagResponse(id = "flag-1", type = "promo", trigger = "login", data = null),
            AccountFlagResponse(id = "flag-2", type = "review", trigger = "entry", data = mapOf("key" to "value")),
        )
        coEvery { accountFlagAPI.getAccountFlags() } returns apiFlags

        val result = repository.getAccountFlags()

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("flag-1")
        assertThat(result[0].type).isEqualTo("promo")
        assertThat(result[0].trigger).isEqualTo("login")
        assertThat(result[0].data).isNull()
        assertThat(result[1].id).isEqualTo("flag-2")
        assertThat(result[1].type).isEqualTo("review")
        assertThat(result[1].trigger).isEqualTo("entry")
    }

    @Test
    fun `getAccountFlags returns empty list when api returns empty`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } returns emptyList()

        val result = repository.getAccountFlags()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getAccountFlags maps id field correctly`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } returns listOf(
            AccountFlagResponse(id = "flag-abc", type = "scale-review-ask", trigger = "login")
        )

        val result = repository.getAccountFlags()

        assertThat(result[0].id).isEqualTo("flag-abc")
    }

    @Test
    fun `getAccountFlags calls api exactly once`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } returns emptyList()

        repository.getAccountFlags()

        coVerify(exactly = 1) { accountFlagAPI.getAccountFlags() }
    }

    @Test
    fun `getAccountFlags returns empty list when IOException thrown`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } throws IOException("No internet")

        val result = repository.getAccountFlags()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getAccountFlags returns empty list when RuntimeException thrown`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } throws RuntimeException("Unexpected error")

        val result = repository.getAccountFlags()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getAccountFlags returns empty list when IllegalStateException thrown`() = runTest {
        coEvery { accountFlagAPI.getAccountFlags() } throws IllegalStateException("Bad state")

        val result = repository.getAccountFlags()

        assertThat(result).isEmpty()
    }

    // ── deleteAccountFlag ──────────────────────────────────────────────────────

    @Test
    fun `deleteAccountFlag returns true on success`() = runTest {
        coEvery { accountFlagAPI.deleteAccountFlag("flag-1") } returns true

        val result = repository.deleteAccountFlag("flag-1")

        assertThat(result).isTrue()
    }

    @Test
    fun `deleteAccountFlag calls api with correct flagId`() = runTest {
        coEvery { accountFlagAPI.deleteAccountFlag(any()) } returns true

        repository.deleteAccountFlag("flag-xyz")

        coVerify { accountFlagAPI.deleteAccountFlag("flag-xyz") }
    }

    @Test
    fun `deleteAccountFlag returns false when IOException thrown`() = runTest {
        coEvery { accountFlagAPI.deleteAccountFlag(any()) } throws IOException("No internet")

        val result = repository.deleteAccountFlag("flag-1")

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteAccountFlag returns false when RuntimeException thrown`() = runTest {
        coEvery { accountFlagAPI.deleteAccountFlag(any()) } throws RuntimeException("Unexpected error")

        val result = repository.deleteAccountFlag("flag-1")

        assertThat(result).isFalse()
    }
}
