package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.services.AuthState
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenManagerTest {

    @MockK(relaxed = true)
    lateinit var userDataStore: UserDataStore

    @MockK(relaxed = true)
    lateinit var secureTokenStore: ISecureTokenStore

    @MockK(relaxed = true)
    lateinit var appNavigationService: IAppNavigationService

    private lateinit var tokenManager: TokenManager

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        tokenManager = TokenManager(userDataStore, secureTokenStore, appNavigationService)
    }

    // -------------------------------------------------------------------------
    // Encryption-failure loop guard (MOB-1537 / MOB-1526)
    // -------------------------------------------------------------------------

    @Test
    fun `repeated encryption failures emit forced-logout only once per process`() = runTest {
        // Every secure-store read fails — as on a device where the MasterKey is unavailable.
        every { secureTokenStore.getToken(any()) } throws
            EncryptionUnavailableException("MasterKey unavailable")

        // Simulate the storm: many concurrent token reads all hitting the failing store.
        repeat(5) { tokenManager.getAccessToken("account-1") }

        // Without the guard each read would emit its own logout event → blink/loop. It must emit once.
        coVerify(exactly = 1) {
            appNavigationService.emitAuthEvent(match { it is AuthState.EncryptionFailure })
        }
    }

    @Test
    fun `encryption failure increments the persisted failure counter`() = runTest {
        every { secureTokenStore.getToken(any()) } throws
            EncryptionUnavailableException("MasterKey unavailable")

        tokenManager.getAccessToken("account-1")

        coVerify(exactly = 1) { secureTokenStore.incrementEncryptionFailureCount() }
    }

    @Test
    fun `successful setTokens resets the encryption failure guard and counter`() = runTest {
        // First, trip the guard.
        every { secureTokenStore.getToken(any()) } throws
            EncryptionUnavailableException("MasterKey unavailable")
        tokenManager.getAccessToken("account-1")

        // Encryption recovers: a save now succeeds (relaxed mock does not throw).
        val token = Token(
            accountId = "account-1",
            isActive = true,
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = "2999-01-01T00:00:00.000Z",
        )
        tokenManager.setTokens(token)

        coVerify(exactly = 1) { secureTokenStore.resetEncryptionFailureCount() }

        // After recovery, a fresh failure emits again (guard was reset).
        tokenManager.getAccessToken("account-2")
        coVerify(exactly = 2) {
            appNavigationService.emitAuthEvent(match { it is AuthState.EncryptionFailure })
        }
    }
}
