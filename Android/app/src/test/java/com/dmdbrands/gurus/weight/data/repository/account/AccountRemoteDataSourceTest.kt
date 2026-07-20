package com.dmdbrands.gurus.weight.data.repository.account

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.google.common.truth.Truth.assertThat
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRemoteDataSourceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val authAPI: IAuthAPI = mockk()
    private val userAPI: IUserAPI = mockk()
    private val remote = AccountRemoteDataSource(authAPI, userAPI)

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `login delegates to authAPI`() = runTest {
        val response = mockk<LoginResponse>()
        coEvery { authAPI.login(any()) } returns response

        assertThat(remote.login("john@example.com", "pwd")).isEqualTo(response)
        coVerify { authAPI.login(any()) }
    }

    @Test
    fun `getAccountFromAPI returns account info and rethrows on failure`() = runTest {
        val info = mockk<AccountInfo>()
        coEvery { authAPI.getAccountWithToken("acc-1") } returns info
        assertThat(remote.getAccountFromAPI("acc-1")).isEqualTo(info)

        coEvery { authAPI.getAccountWithToken("acc-2") } throws IllegalStateException("boom")
        assertFailsWith<IllegalStateException> { remote.getAccountFromAPI("acc-2") }
    }

    @Test
    fun `emailCheck returns availability from response`() = runTest {
        coEvery { authAPI.emailCheck(any()) } returns mockk { every { isAvailable } returns true }
        assertThat(remote.emailCheck("john@example.com")).isTrue()
    }

    @Test
    fun `refreshToken maps response into a Token preserving account id`() = runTest {
        coEvery { authAPI.refreshToken(any()) } returns mockk {
            every { accessToken } returns "access"
            every { refreshToken } returns "refresh"
            every { expiresAt } returns "expires"
        }

        val token = remote.refreshToken("old-refresh", "acc-1")

        assertThat(token.accountId).isEqualTo("acc-1")
        assertThat(token.accessToken).isEqualTo("access")
        assertThat(token.refreshToken).isEqualTo("refresh")
        assertThat(token.isActive).isTrue()
    }

    @Test
    fun `updateProfile returns the account from the response`() = runTest {
        val info = mockk<AccountInfo>()
        coEvery { userAPI.updateProfile(any()) } returns mockk { every { account } returns info }

        assertThat(remote.updateProfile(mockk())).isEqualTo(info)
    }

    @Test
    fun `updateMeasurementUnits returns the account from the response`() = runTest {
        val info = mockk<AccountInfo>()
        coEvery { userAPI.updateMeasurementUnits(any()) } returns mockk { every { account } returns info }

        assertThat(remote.updateMeasurementUnits(MeasurementUnits.METRIC)).isEqualTo(info)
        coVerify { userAPI.updateMeasurementUnits(any()) }
    }

    @Test
    fun `deleteAccountFromServer swallows API errors`() = runTest {
        coEvery { userAPI.deleteAccount() } throws IllegalStateException("server down")

        // Should not throw — errors are logged and swallowed.
        remote.deleteAccountFromServer()

        coVerify { userAPI.deleteAccount() }
    }

    @Test
    fun `logout delegates to authAPI with empty token when null`() = runTest {
        coEvery { authAPI.logoutWithToken(any(), any()) } returns Unit

        remote.logout(null, "acc-1")

        coVerify { authAPI.logoutWithToken(any(), "acc-1") }
    }
}
