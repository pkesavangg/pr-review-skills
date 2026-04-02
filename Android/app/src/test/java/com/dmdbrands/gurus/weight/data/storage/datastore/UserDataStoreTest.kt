package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.proto.UserAccount
import com.dmdbrands.gurus.weight.proto.UserPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataStoreTest {

    private lateinit var fakeDataStore: FakeDataStore<UserPreferences>
    private lateinit var userDataStore: UserDataStore

    private val testAccountId = "acc-1"
    private val testAccountId2 = "acc-2"

    private val testAccount: UserAccount = UserAccount.newBuilder()
        .setIsActive(true)
        .setSyncTimestamp("2024-01-01T00:00:00Z")
        .setRefreshToken("refresh-token")
        .setAccessToken("access-token")
        .setExpiresAt("2024-12-31T23:59:59Z")
        .setThemeMode(ThemeMode.LIGHT)
        .setNotificationAlertShown(false)
        .build()

    private val testAccount2: UserAccount = UserAccount.newBuilder()
        .setIsActive(false)
        .setSyncTimestamp("2024-02-01T00:00:00Z")
        .setRefreshToken("refresh-token-2")
        .setAccessToken("access-token-2")
        .setExpiresAt("2024-12-31T23:59:59Z")
        .setThemeMode(ThemeMode.DARK)
        .setNotificationAlertShown(true)
        .build()

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Exception>()) } returns Unit

        mockkStatic("com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStoreKt")
        val mockContext = mockk<Context>(relaxed = true)
        fakeDataStore = FakeDataStore(UserPreferences.getDefaultInstance())
        every { mockContext.userDataStore } returns fakeDataStore
        userDataStore = UserDataStore(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private suspend fun seedAccount(
        accountId: String = testAccountId,
        account: UserAccount = testAccount,
    ) {
        fakeDataStore.updateData { current ->
            current.toBuilder().putAccounts(accountId, account).build()
        }
    }

    private suspend fun seedAccounts() {
        fakeDataStore.updateData { current ->
            current.toBuilder()
                .putAccounts(testAccountId, testAccount)
                .putAccounts(testAccountId2, testAccount2)
                .build()
        }
    }

    // -------------------------------------------------------------------------
    // accountsFlow
    // -------------------------------------------------------------------------

    @Test
    fun `accountsFlow emits empty map initially`() = runTest {
        userDataStore.accountsFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accountsFlow emits accounts after adding`() = runTest {
        seedAccount()
        userDataStore.accountsFlow.test {
            val accounts = awaitItem()
            assertThat(accounts).containsKey(testAccountId)
            assertThat(accounts[testAccountId]).isEqualTo(testAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // currentThemeModeFlow
    // -------------------------------------------------------------------------

    @Test
    fun `currentThemeModeFlow emits SYSTEM when no active account`() = runTest {
        userDataStore.currentThemeModeFlow.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.SYSTEM)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentThemeModeFlow emits active account theme mode`() = runTest {
        seedAccount()
        userDataStore.currentThemeModeFlow.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.LIGHT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // currentAccountIdFlow
    // -------------------------------------------------------------------------

    @Test
    fun `currentAccountIdFlow emits null when no active account`() = runTest {
        userDataStore.currentAccountIdFlow.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentAccountIdFlow emits active account id`() = runTest {
        seedAccount()
        userDataStore.currentAccountIdFlow.test {
            assertThat(awaitItem()).isEqualTo(testAccountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // currentAccountFlow
    // -------------------------------------------------------------------------

    @Test
    fun `currentAccountFlow emits null when no active account`() = runTest {
        userDataStore.currentAccountFlow.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentAccountFlow emits active account`() = runTest {
        seedAccount()
        userDataStore.currentAccountFlow.test {
            assertThat(awaitItem()).isEqualTo(testAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // getCurrentThemeMode
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentThemeMode returns SYSTEM when no active account`() = runTest {
        val result = userDataStore.getCurrentThemeMode()
        assertThat(result).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `getCurrentThemeMode returns active account theme mode`() = runTest {
        seedAccount()
        val result = userDataStore.getCurrentThemeMode()
        assertThat(result).isEqualTo(ThemeMode.LIGHT)
    }

    // -------------------------------------------------------------------------
    // setThemeMode
    // -------------------------------------------------------------------------

    @Test
    fun `setThemeMode updates theme for existing account`() = runTest {
        seedAccount()
        userDataStore.setThemeMode(testAccountId, ThemeMode.DARK)

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `setThemeMode does nothing for nonexistent account`() = runTest {
        userDataStore.setThemeMode("nonexistent", ThemeMode.DARK)
        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    // -------------------------------------------------------------------------
    // setActiveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `setActiveAccount activates specified account and deactivates others`() = runTest {
        seedAccounts()

        userDataStore.setActiveAccount(testAccountId2)

        val acc1 = userDataStore.getAccount(testAccountId)
        val acc2 = userDataStore.getAccount(testAccountId2)
        assertThat(acc1?.isActive).isFalse()
        assertThat(acc2?.isActive).isTrue()
    }

    @Test
    fun `setActiveAccount deactivates all when target account not found`() = runTest {
        seedAccount()
        userDataStore.setActiveAccount("nonexistent")

        val acc = userDataStore.getAccount(testAccountId)
        assertThat(acc?.isActive).isFalse()
    }

    // -------------------------------------------------------------------------
    // addAccount
    // -------------------------------------------------------------------------

    @Test
    fun `addAccount creates new account with specified values`() = runTest {
        userDataStore.addAccount(
            accountId = testAccountId,
            isActive = true,
            syncTimestamp = "2024-01-01",
            refreshToken = "rt",
            accessToken = "at",
            expiresAt = "2024-12-31",
            themeMode = ThemeMode.DARK,
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account).isNotNull()
        assertThat(account?.isActive).isTrue()
        assertThat(account?.syncTimestamp).isEqualTo("2024-01-01")
        assertThat(account?.refreshToken).isEqualTo("rt")
        assertThat(account?.accessToken).isEqualTo("at")
        assertThat(account?.expiresAt).isEqualTo("2024-12-31")
        assertThat(account?.themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `addAccount throws when account already exists`() = runTest {
        seedAccount()
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                userDataStore.addAccount(accountId = testAccountId)
            }
        }
    }

    @Test
    fun `addAccount with forceUpdate updates existing account`() = runTest {
        seedAccount()

        userDataStore.addAccount(
            accountId = testAccountId,
            isActive = false,
            syncTimestamp = "updated-ts",
            forceUpdate = true,
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.isActive).isFalse()
        assertThat(account?.syncTimestamp).isEqualTo("updated-ts")
    }

    @Test
    fun `addAccount uses default values when not specified`() = runTest {
        userDataStore.addAccount(accountId = testAccountId)

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.isActive).isFalse()
        assertThat(account?.syncTimestamp).isEmpty()
        assertThat(account?.refreshToken).isEmpty()
        assertThat(account?.accessToken).isEmpty()
        assertThat(account?.expiresAt).isEmpty()
        assertThat(account?.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    // -------------------------------------------------------------------------
    // containsAccount
    // -------------------------------------------------------------------------

    @Test
    fun `containsAccount returns true for existing account`() = runTest {
        seedAccount()
        assertThat(userDataStore.containsAccount(testAccountId)).isTrue()
    }

    @Test
    fun `containsAccount returns false for nonexistent account`() = runTest {
        assertThat(userDataStore.containsAccount("nonexistent")).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `updateAccount updates only provided fields on existing account`() = runTest {
        seedAccount()

        userDataStore.updateAccount(
            accountId = testAccountId,
            syncTimestamp = "new-ts",
            themeMode = ThemeMode.DARK,
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.syncTimestamp).isEqualTo("new-ts")
        assertThat(account?.themeMode).isEqualTo(ThemeMode.DARK)
        // Unchanged fields preserved
        assertThat(account?.isActive).isTrue()
        assertThat(account?.refreshToken).isEqualTo("refresh-token")
    }

    @Test
    fun `updateAccount creates account if it does not exist`() = runTest {
        userDataStore.updateAccount(
            accountId = testAccountId,
            isActive = true,
            syncTimestamp = "ts",
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account).isNotNull()
        assertThat(account?.isActive).isTrue()
        assertThat(account?.syncTimestamp).isEqualTo("ts")
    }

    @Test
    fun `updateAccount preserves all fields when only isActive is changed`() = runTest {
        seedAccount()

        userDataStore.updateAccount(accountId = testAccountId, isActive = false)

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.isActive).isFalse()
        assertThat(account?.syncTimestamp).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(account?.refreshToken).isEqualTo("refresh-token")
        assertThat(account?.accessToken).isEqualTo("access-token")
        assertThat(account?.expiresAt).isEqualTo("2024-12-31T23:59:59Z")
        assertThat(account?.themeMode).isEqualTo(ThemeMode.LIGHT)
    }

    // -------------------------------------------------------------------------
    // updateAccountTokens (deprecated)
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    @Test
    fun `updateAccountTokens updates tokens on existing account`() = runTest {
        seedAccount()

        userDataStore.updateAccountTokens(
            accountId = testAccountId,
            refreshToken = "new-rt",
            accessToken = "new-at",
            expiresAt = "2025-01-01",
            isActive = true,
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.refreshToken).isEqualTo("new-rt")
        assertThat(account?.accessToken).isEqualTo("new-at")
        assertThat(account?.expiresAt).isEqualTo("2025-01-01")
        assertThat(account?.isActive).isTrue()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `updateAccountTokens adds account if it does not exist`() = runTest {
        userDataStore.updateAccountTokens(
            accountId = "new-acc",
            refreshToken = "rt",
            accessToken = "at",
            expiresAt = "2025-01-01",
            isActive = true,
        )

        val account = userDataStore.getAccount("new-acc")
        assertThat(account).isNotNull()
        assertThat(account?.isActive).isTrue()
    }

    // -------------------------------------------------------------------------
    // updateSyncTimestamp
    // -------------------------------------------------------------------------

    @Test
    fun `updateSyncTimestamp updates timestamp on existing account`() = runTest {
        seedAccount()

        userDataStore.updateSyncTimestamp(testAccountId, "2025-06-01T00:00:00Z")

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.syncTimestamp).isEqualTo("2025-06-01T00:00:00Z")
    }

    @Test
    fun `updateSyncTimestamp does nothing for nonexistent account`() = runTest {
        userDataStore.updateSyncTimestamp("nonexistent", "2025-06-01")
        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    // -------------------------------------------------------------------------
    // clearData
    // -------------------------------------------------------------------------

    @Test
    fun `clearData removes all accounts`() = runTest {
        seedAccounts()

        userDataStore.clearData()

        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    // -------------------------------------------------------------------------
    // logoutCurrentAccount
    // -------------------------------------------------------------------------

    @Test
    fun `logoutCurrentAccount sets active account to inactive`() = runTest {
        seedAccount()

        userDataStore.logoutCurrentAccount()

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.isActive).isFalse()
    }

    @Test
    fun `logoutCurrentAccount does nothing when no active account`() = runTest {
        val inactiveAccount = testAccount.toBuilder().setIsActive(false).build()
        seedAccount(account = inactiveAccount)

        userDataStore.logoutCurrentAccount()

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.isActive).isFalse()
    }

    // -------------------------------------------------------------------------
    // removeCurrentAccount
    // -------------------------------------------------------------------------

    @Test
    fun `removeCurrentAccount removes the active account`() = runTest {
        seedAccounts()

        userDataStore.removeCurrentAccount()

        assertThat(userDataStore.containsAccount(testAccountId)).isFalse()
        assertThat(userDataStore.containsAccount(testAccountId2)).isTrue()
    }

    @Test
    fun `removeCurrentAccount does nothing when no active account`() = runTest {
        val inactiveAccount = testAccount.toBuilder().setIsActive(false).build()
        seedAccount(account = inactiveAccount)

        userDataStore.removeCurrentAccount()

        assertThat(userDataStore.containsAccount(testAccountId)).isTrue()
    }

    // -------------------------------------------------------------------------
    // logoutAllAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAllAccounts clears tokens and deactivates all accounts`() = runTest {
        seedAccounts()

        userDataStore.logoutAllAccounts()

        val acc1 = userDataStore.getAccount(testAccountId)
        val acc2 = userDataStore.getAccount(testAccountId2)
        assertThat(acc1?.isActive).isFalse()
        assertThat(acc1?.accessToken).isEmpty()
        assertThat(acc1?.refreshToken).isEmpty()
        assertThat(acc1?.expiresAt).isEmpty()
        assertThat(acc2?.isActive).isFalse()
        assertThat(acc2?.accessToken).isEmpty()
        assertThat(acc2?.refreshToken).isEmpty()
        assertThat(acc2?.expiresAt).isEmpty()
    }

    // -------------------------------------------------------------------------
    // hasAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `hasAccounts returns false when empty`() = runTest {
        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    @Test
    fun `hasAccounts returns true when accounts exist`() = runTest {
        seedAccount()
        assertThat(userDataStore.hasAccounts()).isTrue()
    }

    // -------------------------------------------------------------------------
    // getAccount
    // -------------------------------------------------------------------------

    @Test
    fun `getAccount returns account for valid id`() = runTest {
        seedAccount()
        assertThat(userDataStore.getAccount(testAccountId)).isEqualTo(testAccount)
    }

    @Test
    fun `getAccount returns null for invalid id`() = runTest {
        assertThat(userDataStore.getAccount("nonexistent")).isNull()
    }

    // -------------------------------------------------------------------------
    // getCurrentAccountExpiresAt
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentAccountExpiresAt returns expiresAt for active account`() = runTest {
        seedAccount()
        assertThat(userDataStore.getCurrentAccountExpiresAt()).isEqualTo("2024-12-31T23:59:59Z")
    }

    @Test
    fun `getCurrentAccountExpiresAt returns null when no active account`() = runTest {
        assertThat(userDataStore.getCurrentAccountExpiresAt()).isNull()
    }

    // -------------------------------------------------------------------------
    // removeAccount
    // -------------------------------------------------------------------------

    @Test
    fun `removeAccount removes specified account`() = runTest {
        seedAccounts()

        userDataStore.removeAccount(testAccountId)

        assertThat(userDataStore.containsAccount(testAccountId)).isFalse()
        assertThat(userDataStore.containsAccount(testAccountId2)).isTrue()
    }

    @Test
    fun `removeAccount does nothing for nonexistent id`() = runTest {
        seedAccount()
        userDataStore.removeAccount("nonexistent")
        assertThat(userDataStore.containsAccount(testAccountId)).isTrue()
    }

    // -------------------------------------------------------------------------
    // clearAccountTokens
    // -------------------------------------------------------------------------

    @Test
    fun `clearAccountTokens clears tokens and deactivates account`() = runTest {
        seedAccount()

        userDataStore.clearAccountTokens(testAccountId)

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.accessToken).isEmpty()
        assertThat(account?.refreshToken).isEmpty()
        assertThat(account?.expiresAt).isEmpty()
        assertThat(account?.isActive).isFalse()
    }

    @Test
    fun `clearAccountTokens does nothing for nonexistent account`() = runTest {
        userDataStore.clearAccountTokens("nonexistent")
        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    @Test
    fun `clearAccountTokens preserves non-token fields`() = runTest {
        seedAccount()

        userDataStore.clearAccountTokens(testAccountId)

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account?.syncTimestamp).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(account?.themeMode).isEqualTo(ThemeMode.LIGHT)
    }

    // -------------------------------------------------------------------------
    // hasShownAccountSwitchInfoModalForDevice
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownAccountSwitchInfoModalForDevice returns false initially`() = runTest {
        assertThat(userDataStore.hasShownAccountSwitchInfoModalForDevice()).isFalse()
    }

    @Test
    fun `setAccountSwitchInfoModalShownForDevice persists value`() = runTest {
        userDataStore.setAccountSwitchInfoModalShownForDevice(true)
        assertThat(userDataStore.hasShownAccountSwitchInfoModalForDevice()).isTrue()
    }

    @Test
    fun `setAccountSwitchInfoModalShownForDevice can reset to false`() = runTest {
        userDataStore.setAccountSwitchInfoModalShownForDevice(true)
        userDataStore.setAccountSwitchInfoModalShownForDevice(false)
        assertThat(userDataStore.hasShownAccountSwitchInfoModalForDevice()).isFalse()
    }

    // -------------------------------------------------------------------------
    // hasShownNotificationAlertForAccount
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownNotificationAlertForAccount returns false when account not found`() = runTest {
        assertThat(userDataStore.hasShownNotificationAlertForAccount("nonexistent")).isFalse()
    }

    @Test
    fun `hasShownNotificationAlertForAccount returns stored value`() = runTest {
        val accountWithAlert = testAccount.toBuilder().setNotificationAlertShown(true).build()
        seedAccount(account = accountWithAlert)

        assertThat(userDataStore.hasShownNotificationAlertForAccount(testAccountId)).isTrue()
    }

    @Test
    fun `setNotificationAlertShownForAccount updates the flag`() = runTest {
        seedAccount()

        userDataStore.setNotificationAlertShownForAccount(testAccountId, true)

        assertThat(userDataStore.hasShownNotificationAlertForAccount(testAccountId)).isTrue()
    }

    @Test
    fun `setNotificationAlertShownForAccount does nothing for nonexistent account`() = runTest {
        userDataStore.setNotificationAlertShownForAccount("nonexistent", true)
        assertThat(userDataStore.hasAccounts()).isFalse()
    }

    // -------------------------------------------------------------------------
    // addAccount with forceUpdate on new account
    // -------------------------------------------------------------------------

    @Test
    fun `addAccount with forceUpdate creates account when it does not exist`() = runTest {
        userDataStore.addAccount(
            accountId = testAccountId,
            isActive = true,
            syncTimestamp = "2024-01-01",
            refreshToken = "rt",
            accessToken = "at",
            expiresAt = "2024-12-31",
            forceUpdate = true,
        )

        val account = userDataStore.getAccount(testAccountId)
        assertThat(account).isNotNull()
        assertThat(account?.isActive).isTrue()
        assertThat(account?.syncTimestamp).isEqualTo("2024-01-01")
        assertThat(account?.refreshToken).isEqualTo("rt")
        assertThat(account?.accessToken).isEqualTo("at")
        assertThat(account?.expiresAt).isEqualTo("2024-12-31")
    }
}
