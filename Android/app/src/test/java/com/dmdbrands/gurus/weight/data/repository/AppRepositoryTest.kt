package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.FcmToken
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppRepositoryTest {

    companion object {
        private const val ACCOUNT_ID_1 = "account1"
        private const val ACCOUNT_ID_2 = "account2"
        private const val FCM_TOKEN = "test-fcm-token"
        private const val FCM_TOKEN_NEW = "new-token"
        private const val FCM_TOKEN_123 = "token-123"
    }

    @MockK(relaxed = true)
    private lateinit var userDataStore: UserDataStore

    @MockK(relaxed = true)
    private lateinit var fcmDataStore: FcmDataStore

    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = AppRepository(userDataStore, fcmDataStore)
    }

    // ── themeModeFlow ──────────────────────────────────────────────────────────

    @Test
    fun `themeModeFlow delegates to userDataStore currentThemeModeFlow`() = runTest {
        every { userDataStore.currentThemeModeFlow } returns flowOf(ThemeMode.DARK)

        val flow = repository.themeModeFlow

        assertThat(flow).isNotNull()
    }

    // ── fcmTokenFlow ───────────────────────────────────────────────────────────

    @Test
    fun `fcmTokenFlow delegates to fcmDataStore tokenFlow`() = runTest {
        every { fcmDataStore.tokenFlow } returns flowOf(FCM_TOKEN)

        val flow = repository.fcmTokenFlow

        assertThat(flow).isNotNull()
    }

    // ── getThemeMode ───────────────────────────────────────────────────────────

    @Test
    fun `getThemeMode returns theme mode from data store`() = runTest {
        coEvery { userDataStore.getCurrentThemeMode() } returns ThemeMode.DARK

        val result = repository.getThemeMode()

        assertThat(result).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `getThemeMode returns SYSTEM when data store returns SYSTEM`() = runTest {
        coEvery { userDataStore.getCurrentThemeMode() } returns ThemeMode.SYSTEM

        val result = repository.getThemeMode()

        assertThat(result).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `getThemeMode calls user data store exactly once`() = runTest {
        coEvery { userDataStore.getCurrentThemeMode() } returns ThemeMode.LIGHT

        repository.getThemeMode()

        coVerify(exactly = 1) { userDataStore.getCurrentThemeMode() }
    }

    // ── setThemeMode ───────────────────────────────────────────────────────────

    @Test
    fun `setThemeMode delegates to user data store with correct params`() = runTest {
        repository.setThemeMode(ACCOUNT_ID_1, ThemeMode.DARK)

        coVerify { userDataStore.setThemeMode(ACCOUNT_ID_1, ThemeMode.DARK) }
    }

    @Test
    fun `setThemeMode passes light mode correctly`() = runTest {
        repository.setThemeMode(ACCOUNT_ID_2, ThemeMode.LIGHT)

        coVerify { userDataStore.setThemeMode(ACCOUNT_ID_2, ThemeMode.LIGHT) }
    }

    // ── clearThemeMode ─────────────────────────────────────────────────────────

    @Test
    fun `clearThemeMode delegates to user data store clearData`() = runTest {
        repository.clearThemeMode()

        coVerify { userDataStore.clearData() }
    }

    // ── getFcmToken ────────────────────────────────────────────────────────────

    @Test
    fun `getFcmToken returns token from data store`() = runTest {
        val fcmToken = FcmToken.newBuilder().setToken(FCM_TOKEN).build()
        coEvery { fcmDataStore.getData() } returns fcmToken

        val result = repository.getFcmToken()

        assertThat(result).isEqualTo(FCM_TOKEN)
    }

    @Test
    fun `getFcmToken returns empty string when token is empty`() = runTest {
        val fcmToken = FcmToken.newBuilder().setToken("").build()
        coEvery { fcmDataStore.getData() } returns fcmToken

        val result = repository.getFcmToken()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getFcmToken calls fcm data store getData`() = runTest {
        val fcmToken = FcmToken.newBuilder().setToken(FCM_TOKEN_123).build()
        coEvery { fcmDataStore.getData() } returns fcmToken

        repository.getFcmToken()

        coVerify(exactly = 1) { fcmDataStore.getData() }
    }

    // ── setFcmToken ────────────────────────────────────────────────────────────

    @Test
    fun `setFcmToken delegates to fcm data store setToken`() = runTest {
        repository.setFcmToken(FCM_TOKEN_NEW)

        coVerify { fcmDataStore.setToken(FCM_TOKEN_NEW) }
    }

    // ── clearFcmToken ──────────────────────────────────────────────────────────

    @Test
    fun `clearFcmToken delegates to fcm data store clearData`() = runTest {
        repository.clearFcmToken()

        coVerify { fcmDataStore.clearData() }
    }

    // ── exception propagation ──────────────────────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `getThemeMode propagates exception from data store`() = runTest {
        coEvery { userDataStore.getCurrentThemeMode() } throws RuntimeException("DataStore error")

        repository.getThemeMode()
    }

    @Test(expected = RuntimeException::class)
    fun `getFcmToken propagates exception from data store`() = runTest {
        coEvery { fcmDataStore.getData() } throws RuntimeException("DataStore error")

        repository.getFcmToken()
    }
}
