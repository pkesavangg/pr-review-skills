package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account as EntityAccount
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountToken
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for [AccountRepository].
 *
 * All five dependencies are mocked; AccountDao and UserDataStore use relaxUnitFun = true
 * to avoid manually stubbing every Unit-returning DAO/DataStore method.
 *
 * UserDataStore.currentThemeModeFlow must be stubbed before constructing the repository
 * because AccountRepository initialises `currentThemeModeFlow` from it at construction time.
 */
class AccountRepositoryTest {

    @MockK(relaxUnitFun = true)
    lateinit var accountDao: AccountDao

    @MockK(relaxUnitFun = true)
    lateinit var userDataStore: UserDataStore

    @MockK(relaxUnitFun = true)
    lateinit var tokenManager: ITokenManager

    @MockK(relaxUnitFun = true)
    lateinit var authAPI: IAuthAPI

    @MockK(relaxUnitFun = true)
    lateinit var userAPI: IUserAPI

    private lateinit var repository: AccountRepository

    // ---- Shared test fixtures ----

    private val accountInfo = AccountInfo(
        id = "acc1",
        email = "test@example.com",
        firstName = "John",
        lastName = "Doe",
        gender = "male",
        zipcode = "12345",
        weightUnit = "lbs",
        isWeightlessOn = false,
        height = 1780,
        activityLevel = "normal",
        dob = "1990-01-01",
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = "4_metrics",
        dashboardMetrics = emptyList(),
        progressMetrics = emptyList(),
        goalType = null,
        goalWeight = null,
        initialWeight = null,
        shouldSendEntryNotifications = false,
        shouldSendWeightInEntryNotifications = false,
    )

    private val loginResponse = LoginResponse(
        id = "acc1",
        email = "test@example.com",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = "9999999999",
        account = accountInfo,
    )

    private val accountEntity = AccountEntity(
        id = "acc1",
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "test@example.com",
        gender = "male",
        zipcode = "12345",
        isActiveAccount = true,
        isLoggedIn = true,
    )

    // Entity Account with relations (all settings null — mapper defaults apply)
    private val entityAccountWithRelations = EntityAccount(
        account = accountEntity,
        weightCompSettings = null,
        goalSettings = null,
        streaksSettings = null,
        weightlessSettings = null,
        notificationSettings = null,
        dashboardSettings = null,
        integrationsSettings = null,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        // AccountRepository reads userDataStore.currentThemeModeFlow at construction time.
        every { userDataStore.currentThemeModeFlow } returns flowOf(ThemeMode.SYSTEM)
        repository = AccountRepository(accountDao, userDataStore, tokenManager, authAPI, userAPI)
    }

    // -------------------------------------------------------------------------
    // Auth: login
    // -------------------------------------------------------------------------

    @Test
    fun `login calls authAPI and returns mapped Account`() = runTest {
        coEvery { authAPI.login(any()) } returns loginResponse
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val result = repository.login("test@example.com", "password")

        assertThat(result.id).isEqualTo("acc1")
        assertThat(result.email).isEqualTo("test@example.com")
        coVerify { authAPI.login(any()) }
    }

    @Test
    fun `login when API throws rethrows exception`() = runTest {
        coEvery { authAPI.login(any()) } throws RuntimeException("Network error")

        try {
            repository.login("test@example.com", "password")
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Network error")
        }
    }

    // -------------------------------------------------------------------------
    // Auth: signup
    // -------------------------------------------------------------------------

    @Test
    fun `signup calls createAccount and returns mapped Account`() = runTest {
        coEvery { authAPI.createAccount(any()) } returns loginResponse
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val request = SignupRequest(
            email = "test@example.com",
            firstName = "John",
            lastName = "Doe",
            gender = "male",
            zipcode = "12345",
            password = "pass",
            dob = "1990-01-01",
            height = 1780,
        )
        val result = repository.signup(request)

        assertThat(result.id).isEqualTo("acc1")
        coVerify { authAPI.createAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // Auth: getAccountFromAPI
    // -------------------------------------------------------------------------

    @Test
    fun `getAccountFromAPI returns AccountInfo from API`() = runTest {
        coEvery { authAPI.getAccountWithToken("acc1") } returns accountInfo

        val result = repository.getAccountFromAPI("acc1")

        assertThat(result).isEqualTo(accountInfo)
    }

    @Test
    fun `getAccountFromAPI when API throws rethrows`() = runTest {
        coEvery { authAPI.getAccountWithToken(any()) } throws RuntimeException("Server error")

        try {
            repository.getAccountFromAPI("acc1")
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Server error")
        }
    }

    // -------------------------------------------------------------------------
    // Auth: updatePassword
    // -------------------------------------------------------------------------

    @Test
    fun `updatePassword calls changePassword and sets tokens`() = runTest {
        val response = ChangePasswordResponse("new-access", "new-refresh", "expiry")
        coEvery { userAPI.changePassword(any()) } returns response

        val result = repository.updatePassword("acc1", "old", "new")

        assertThat(result.accessToken).isEqualTo("new-access")
        coVerify { tokenManager.setTokens(any()) }
    }

    // -------------------------------------------------------------------------
    // Auth: refreshToken
    // -------------------------------------------------------------------------

    @Test
    fun `refreshToken returns Token with correct fields`() = runTest {
        coEvery { authAPI.refreshToken(any()) } returns RefreshTokenResponse("new-access", "new-refresh", "expiry")

        val result = repository.refreshToken("old-refresh", "acc1")

        assertThat(result.accountId).isEqualTo("acc1")
        assertThat(result.accessToken).isEqualTo("new-access")
        assertThat(result.refreshToken).isEqualTo("new-refresh")
        assertThat(result.isActive).isTrue()
    }

    @Test
    fun `refreshToken with null accountId uses empty string`() = runTest {
        coEvery { authAPI.refreshToken(any()) } returns RefreshTokenResponse("access", "refresh", "exp")

        val result = repository.refreshToken("old-refresh", null)

        assertThat(result.accountId).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // Auth: resetPassword
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword returns response from requestPasswordReset`() = runTest {
        val response = mockk<Response<Unit>>()
        coEvery { authAPI.requestPasswordReset(any()) } returns response

        val result = repository.resetPassword("test@example.com")

        assertThat(result).isSameInstanceAs(response)
    }

    // -------------------------------------------------------------------------
    // Dashboard API ops
    // -------------------------------------------------------------------------

    @Test
    fun `updateDashboardMetrics delegates to userAPI`() = runTest {
        coEvery { userAPI.updateDashboardMetrics(any()) } returns AccountResponse(null, null, null, accountInfo)

        repository.updateDashboardMetrics(listOf("weight", "bmi"))

        coVerify { userAPI.updateDashboardMetrics(any()) }
    }

    @Test
    fun `updateProgressMetrics delegates to userAPI and swallows exceptions`() = runTest {
        coEvery { userAPI.updateProgressMetrics(any()) } throws RuntimeException("network")

        // Should NOT throw — repository swallows the exception
        repository.updateProgressMetrics(listOf("steps"))

        coVerify { userAPI.updateProgressMetrics(any()) }
    }

    @Test
    fun `updateDashboardType delegates to userAPI`() = runTest {
        coEvery { userAPI.updateDashboardType(any()) } returns AccountResponse(null, null, null, accountInfo)

        repository.updateDashboardType("4_metrics")

        coVerify { userAPI.updateDashboardType(any()) }
    }

    // -------------------------------------------------------------------------
    // DB: addAccount
    // -------------------------------------------------------------------------

    @Test
    fun `addAccount new account inserts account and all settings entities`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null

        repository.addAccount(buildDomainAccount())

        coVerify { accountDao.insertAccount(any()) }
        coVerify { accountDao.insertWeightCompSettings(any()) }
        coVerify { accountDao.insertNotificationSettings(any()) }
        coVerify { accountDao.insertStreaksSettings(any()) }
        coVerify { accountDao.insertWeightlessSettings(any()) }
        coVerify { accountDao.insertGoalSettings(any()) }
        coVerify { accountDao.insertIntegrationsSettings(any()) }
        coVerify { accountDao.insertDashboardSettings(any()) }
    }

    @Test
    fun `addAccount existing account updates instead of inserting`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        repository.addAccount(buildDomainAccount())

        coVerify { accountDao.updateAccount(any()) }
        coVerify(exactly = 0) { accountDao.insertAccount(any()) }
    }

    @Test
    fun `addAccount returns the same account passed in`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null
        val account = buildDomainAccount()

        val result = repository.addAccount(account)

        assertThat(result).isEqualTo(account)
    }

    // -------------------------------------------------------------------------
    // DB: updateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `updateAccount merges partial data and preserves unchanged fields`() = runTest {
        coEvery { accountDao.getAccountEntity("acc1") } returns accountEntity

        repository.updateAccount("acc1", PartialAccount(firstName = "Jane"))

        coVerify { accountDao.updateAccount(match { it.firstName == "Jane" && it.lastName == "Doe" }) }
    }

    @Test
    fun `updateAccount when account not found throws IllegalStateException`() = runTest {
        coEvery { accountDao.getAccountEntity("missing") } returns null

        try {
            repository.updateAccount("missing", PartialAccount(firstName = "X"))
            error("Expected IllegalStateException not thrown")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("missing")
        }
    }

    // -------------------------------------------------------------------------
    // DB: getActiveAccount / getLoggedInAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `getActiveAccount returns Flow mapped to domain Account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(entityAccountWithRelations)

        val account = repository.getActiveAccount().first()

        assertThat(account).isNotNull()
        assertThat(account!!.id).isEqualTo("acc1")
        assertThat(account.email).isEqualTo("test@example.com")
    }

    @Test
    fun `getActiveAccount emits null when DAO emits null`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        val account = repository.getActiveAccount().first()

        assertThat(account).isNull()
    }

    @Test
    fun `getLoggedInAccounts returns Flow of mapped domain accounts`() = runTest {
        every { accountDao.getAllLoggedInAccounts() } returns flowOf(listOf(entityAccountWithRelations))

        val accounts = repository.getLoggedInAccounts().first()

        assertThat(accounts).hasSize(1)
        assertThat(accounts.first().id).isEqualTo("acc1")
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAccount API succeeds performs local logout and returns true`() = runTest {
        val result = repository.logoutAccount("acc1", null, isActiveAccount = true)

        assertThat(result).isTrue()
        coVerify { accountDao.deactivateAllAccounts() }
        coVerify { accountDao.logoutAccount("acc1") }
    }

    @Test
    fun `logoutAccount API throws still performs local logout and returns true`() = runTest {
        coEvery { authAPI.logoutWithToken(any(), any()) } throws RuntimeException("network")

        val result = repository.logoutAccount("acc1", "fcm-token", isActiveAccount = false)

        assertThat(result).isTrue()
        coVerify { accountDao.logoutAccount("acc1") }
    }

    @Test
    fun `logoutAllAccounts clears all accounts and returns true`() = runTest {
        every { accountDao.getAllLoggedInAccounts() } returns flowOf(listOf(entityAccountWithRelations))

        val result = repository.logoutAllAccounts()

        assertThat(result).isTrue()
        coVerify { accountDao.logoutAllAccounts() }
        coVerify { tokenManager.clearTokens() }
    }

    // -------------------------------------------------------------------------
    // Profile update
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile online updates DAO with isSynced true`() = runTest {
        coEvery { userAPI.updateProfile(any()) } returns AccountResponse(null, null, null, accountInfo)
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        repository.updateProfile(buildProfileUpdateRequest())

        coVerify { accountDao.updateAccount(match { it.isSynced }) }
    }

    @Test
    fun `updateProfile network error updates DAO with isSynced false and rethrows`() = runTest {
        val httpException = mockk<HttpException>()
        every { httpException.code() } returns 0
        coEvery { userAPI.updateProfile(any()) } throws httpException
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        try {
            repository.updateProfile(buildProfileUpdateRequest())
            error("Expected HttpException not thrown")
        } catch (e: HttpException) {
            // expected
        }

        coVerify { accountDao.updateAccount(match { !it.isSynced }) }
    }

    @Test
    fun `updateProfile non-network HTTP error skips offline update and rethrows`() = runTest {
        val httpException = mockk<HttpException>()
        every { httpException.code() } returns 500
        coEvery { userAPI.updateProfile(any()) } throws httpException

        try {
            repository.updateProfile(buildProfileUpdateRequest())
            error("Expected HttpException not thrown")
        } catch (e: HttpException) {
            // expected
        }

        coVerify(exactly = 0) { accountDao.updateAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // Tokens / DataStore
    // -------------------------------------------------------------------------

    @Test
    fun `updateTokens calls tokenManager setTokens with correct Token`() = runTest {
        repository.updateTokens(AccountToken("acc1", "access", "refresh", "expiry"))

        coVerify { tokenManager.setTokens(match { it.accountId == "acc1" && it.accessToken == "access" }) }
    }

    @Test
    fun `clearAccountTokens delegates to userDataStore`() = runTest {
        repository.clearAccountTokens("acc1")

        coVerify { userDataStore.clearAccountTokens("acc1") }
    }

    // -------------------------------------------------------------------------
    // switchToAccount
    // -------------------------------------------------------------------------

    @Test
    fun `switchToAccount activates account deactivates others and sets active in DataStore`() = runTest {
        repository.switchToAccount("acc1")

        coVerify { accountDao.activateAccount("acc1") }
        coVerify { accountDao.deactivateOtherAccounts("acc1") }
        coVerify { userDataStore.setActiveAccount("acc1") }
    }

    // -------------------------------------------------------------------------
    // markAccountExpired
    // -------------------------------------------------------------------------

    @Test
    fun `markAccountExpired calls DAO markAccountExpired`() = runTest {
        repository.markAccountExpired("acc1")

        coVerify { accountDao.markAccountExpired("acc1") }
    }

    // -------------------------------------------------------------------------
    // syncAccountSettingsWithServer
    // -------------------------------------------------------------------------

    @Test
    fun `syncAccountSettingsWithServer online updates all settings and marks account synced`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        repository.syncAccountSettingsWithServer(accountInfo, isOnline = true)

        coVerify { accountDao.updateWeightlessSettings(any()) }
        coVerify { accountDao.updateWeightCompSettings(any()) }
        coVerify { accountDao.updateGoalSettings(any()) }
        coVerify { accountDao.updateNotificationSettings(any()) }
        coVerify { accountDao.updateIntegrationsSettings(any()) }
        coVerify { accountDao.markAccountSynced("acc1") }
    }

    @Test
    fun `syncAccountSettingsWithServer offline skips markAccountSynced`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        repository.syncAccountSettingsWithServer(accountInfo, isOnline = false)

        coVerify(exactly = 0) { accountDao.markAccountSynced(any()) }
    }

    // -------------------------------------------------------------------------
    // deleteAccount
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount active account deletes from DB and clears all tokens`() = runTest {
        repository.deleteAccount("acc1", isActiveAccount = true)

        coVerify { accountDao.deleteAccountById("acc1") }
        coVerify { accountDao.deactivateAllAccounts() }
        coVerify { tokenManager.clearTokens() }
        coVerify { userDataStore.clearAccountTokens("acc1") }
    }

    // -------------------------------------------------------------------------
    // Notification alert
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownNotificationAlertForAccount delegates to userDataStore`() = runTest {
        coEvery { userDataStore.hasShownNotificationAlertForAccount("acc1") } returns true

        val result = repository.hasShownNotificationAlertForAccount("acc1")

        assertThat(result).isTrue()
    }

    @Test
    fun `setNotificationAlertShownForAccount delegates to userDataStore`() = runTest {
        repository.setNotificationAlertShownForAccount("acc1", true)

        coVerify { userDataStore.setNotificationAlertShownForAccount("acc1", true) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildDomainAccount() = Account(
        id = "acc1",
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "test@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1780,
        activityLevel = "normal",
        isActiveAccount = true,
        isLoggedIn = true,
    )

    private fun buildProfileUpdateRequest() = ProfileUpdateRequest(
        id = "acc1",
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        gender = "male",
        zipcode = "12345",
        email = "test@example.com",
    )
}
