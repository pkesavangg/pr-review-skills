package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account as EntityAccount
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
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
import com.dmdbrands.gurus.weight.proto.UserAccount
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
import kotlin.test.assertFailsWith

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

    companion object {
        private const val TEST_ACCOUNT_ID = "acc1"
        private const val TEST_EMAIL = "test@example.com"
        private const val TEST_FIRST_NAME = "John"
        private const val TEST_LAST_NAME = "Doe"
        private const val TEST_DOB = "1990-01-01"
        private const val TEST_GENDER = "male"
        private const val TEST_ZIPCODE = "12345"
        private const val TEST_PASSWORD = "password"
        private const val TEST_WEIGHT_UNIT = "lbs"
        private const val TEST_ACTIVITY_LEVEL = "normal"
        private const val TEST_DASHBOARD_TYPE = "4_metrics"
        private const val TEST_ACCESS_TOKEN = "access-token"
        private const val TEST_REFRESH_TOKEN = "refresh-token"
        private const val TEST_NEW_ACCESS_TOKEN = "new-access"
        private const val TEST_NEW_REFRESH_TOKEN = "new-refresh"
        private const val TEST_OLD_REFRESH_TOKEN = "old-refresh"
        private const val TEST_EXPIRES_AT = "9999999999"
        private const val TEST_EXPIRY = "expiry"
        private const val TEST_FCM_TOKEN = "fcm-token"
        private const val TEST_HEIGHT = 1780
    }

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
        id = TEST_ACCOUNT_ID,
        email = TEST_EMAIL,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        weightUnit = TEST_WEIGHT_UNIT,
        isWeightlessOn = false,
        height = TEST_HEIGHT,
        activityLevel = TEST_ACTIVITY_LEVEL,
        dob = TEST_DOB,
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = TEST_DASHBOARD_TYPE,
        dashboardMetrics = emptyList(),
        progressMetrics = emptyList(),
        goalType = null,
        goalWeight = null,
        initialWeight = null,
        shouldSendEntryNotifications = false,
        shouldSendWeightInEntryNotifications = false,
    )

    private val loginResponse = LoginResponse(
        id = TEST_ACCOUNT_ID,
        email = TEST_EMAIL,
        accessToken = TEST_ACCESS_TOKEN,
        refreshToken = TEST_REFRESH_TOKEN,
        expiresAt = TEST_EXPIRES_AT,
        account = accountInfo,
    )

    private val accountEntity = AccountEntity(
        id = TEST_ACCOUNT_ID,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        dob = TEST_DOB,
        email = TEST_EMAIL,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
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
        repository = AccountRepository(accountDao, userDataStore, tokenManager, mockk(relaxed = true), authAPI, userAPI)
    }

    // -------------------------------------------------------------------------
    // Auth: login
    // -------------------------------------------------------------------------

    @Test
    fun `login calls authAPI and returns mapped Account`() = runTest {
        coEvery { authAPI.login(any()) } returns loginResponse
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val result = repository.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result.id).isEqualTo(TEST_ACCOUNT_ID)
        assertThat(result.email).isEqualTo(TEST_EMAIL)
        coVerify { authAPI.login(any()) }
    }

    @Test
    fun `login when API throws rethrows exception`() = runTest {
        coEvery { authAPI.login(any()) } throws RuntimeException("Network error")

        val e = assertFailsWith<RuntimeException> {
            repository.login(TEST_EMAIL, TEST_PASSWORD)
        }
        assertThat(e.message).isEqualTo("Network error")
    }

    // -------------------------------------------------------------------------
    // Auth: signup
    // -------------------------------------------------------------------------

    @Test
    fun `signup calls createAccount and returns mapped Account`() = runTest {
        coEvery { authAPI.createAccount(any()) } returns loginResponse
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val request = SignupRequest(
            email = TEST_EMAIL,
            firstName = TEST_FIRST_NAME,
            lastName = TEST_LAST_NAME,
            gender = TEST_GENDER,
            zipcode = TEST_ZIPCODE,
            password = TEST_PASSWORD,
            dob = TEST_DOB,
            height = TEST_HEIGHT,
        )
        val result = repository.signup(request)

        assertThat(result.id).isEqualTo(TEST_ACCOUNT_ID)
        coVerify { authAPI.createAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // Auth: getAccountFromAPI
    // -------------------------------------------------------------------------

    @Test
    fun `getAccountFromAPI returns AccountInfo from API`() = runTest {
        coEvery { authAPI.getAccountWithToken(TEST_ACCOUNT_ID) } returns accountInfo

        val result = repository.getAccountFromAPI(TEST_ACCOUNT_ID)

        assertThat(result).isEqualTo(accountInfo)
    }

    @Test
    fun `getAccountFromAPI when API throws rethrows`() = runTest {
        coEvery { authAPI.getAccountWithToken(any()) } throws RuntimeException("Server error")

        try {
            repository.getAccountFromAPI(TEST_ACCOUNT_ID)
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
        val response = ChangePasswordResponse(TEST_NEW_ACCESS_TOKEN, TEST_NEW_REFRESH_TOKEN, TEST_EXPIRY)
        coEvery { userAPI.changePassword(any()) } returns response

        val result = repository.updatePassword(TEST_ACCOUNT_ID, "old", "new")

        assertThat(result.accessToken).isEqualTo(TEST_NEW_ACCESS_TOKEN)
        coVerify { tokenManager.setTokens(any()) }
    }

    // -------------------------------------------------------------------------
    // Auth: refreshToken
    // -------------------------------------------------------------------------

    @Test
    fun `refreshToken returns Token with correct fields`() = runTest {
        coEvery { authAPI.refreshToken(any()) } returns RefreshTokenResponse(TEST_NEW_ACCESS_TOKEN, TEST_NEW_REFRESH_TOKEN, TEST_EXPIRY)

        val result = repository.refreshToken(TEST_OLD_REFRESH_TOKEN, TEST_ACCOUNT_ID)

        assertThat(result.accountId).isEqualTo(TEST_ACCOUNT_ID)
        assertThat(result.accessToken).isEqualTo(TEST_NEW_ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(TEST_NEW_REFRESH_TOKEN)
        assertThat(result.isActive).isTrue()
    }

    @Test
    fun `refreshToken with null accountId uses empty string`() = runTest {
        coEvery { authAPI.refreshToken(any()) } returns RefreshTokenResponse("access", "refresh", "exp")

        val result = repository.refreshToken(TEST_OLD_REFRESH_TOKEN, null)

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
        coVerify { accountDao.updateWeightCompSettings(any()) }
        coVerify { accountDao.updateNotificationSettings(any()) }
        coVerify { accountDao.updateStreaksSettings(any()) }
        coVerify { accountDao.updateWeightlessSettings(any()) }
        coVerify { accountDao.updateGoalSettings(any()) }
        coVerify { accountDao.updateIntegrationsSettings(any()) }
        coVerify { accountDao.updateDashboardSettings(any()) }
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
        coEvery { accountDao.getAccountEntity(TEST_ACCOUNT_ID) } returns accountEntity

        repository.updateAccount(TEST_ACCOUNT_ID, PartialAccount(firstName = "Jane"))

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
        assertThat(account!!.id).isEqualTo(TEST_ACCOUNT_ID)
        assertThat(account.email).isEqualTo(TEST_EMAIL)
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
        assertThat(accounts.first().id).isEqualTo(TEST_ACCOUNT_ID)
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAccount API succeeds performs local logout and returns true`() = runTest {
        val result = repository.logoutAccount(TEST_ACCOUNT_ID, null, isActiveAccount = true)

        assertThat(result).isTrue()
        coVerify { accountDao.deactivateAllAccounts() }
        coVerify { accountDao.logoutAccount(TEST_ACCOUNT_ID) }
        coVerify { accountDao.markAccountExpired(TEST_ACCOUNT_ID) }
        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
    }

    @Test
    fun `logoutAccount API throws still performs local logout and returns true`() = runTest {
        coEvery { authAPI.logoutWithToken(any(), any()) } throws RuntimeException("network")

        val result = repository.logoutAccount(TEST_ACCOUNT_ID, TEST_FCM_TOKEN, isActiveAccount = false)

        assertThat(result).isTrue()
        coVerify { accountDao.logoutAccount(TEST_ACCOUNT_ID) }
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

        assertFailsWith<HttpException> {
            repository.updateProfile(buildProfileUpdateRequest())
        }

        coVerify { accountDao.updateAccount(match { !it.isSynced }) }
    }

    @Test
    fun `updateProfile non-network HTTP error skips offline update and rethrows`() = runTest {
        val httpException = mockk<HttpException>()
        every { httpException.code() } returns 500
        coEvery { userAPI.updateProfile(any()) } throws httpException

        assertFailsWith<HttpException> {
            repository.updateProfile(buildProfileUpdateRequest())
        }

        coVerify(exactly = 0) { accountDao.updateAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // Tokens / DataStore
    // -------------------------------------------------------------------------

    @Test
    fun `updateTokens calls tokenManager setTokens with correct Token`() = runTest {
        repository.updateTokens(AccountToken(TEST_ACCOUNT_ID, "access", "refresh", TEST_EXPIRY))

        coVerify { tokenManager.setTokens(match { it.accountId == TEST_ACCOUNT_ID && it.accessToken == "access" }) }
    }

    @Test
    fun `clearAccountTokens delegates to userDataStore`() = runTest {
        repository.clearAccountTokens(TEST_ACCOUNT_ID)

        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
    }

    // -------------------------------------------------------------------------
    // switchToAccount
    // -------------------------------------------------------------------------

    @Test
    fun `switchToAccount activates account deactivates others and sets active in DataStore`() = runTest {
        repository.switchToAccount(TEST_ACCOUNT_ID)

        coVerify { accountDao.activateAccount(TEST_ACCOUNT_ID) }
        coVerify { accountDao.deactivateOtherAccounts(TEST_ACCOUNT_ID) }
        coVerify { userDataStore.setActiveAccount(TEST_ACCOUNT_ID) }
    }

    // -------------------------------------------------------------------------
    // markAccountExpired
    // -------------------------------------------------------------------------

    @Test
    fun `markAccountExpired calls DAO markAccountExpired`() = runTest {
        repository.markAccountExpired(TEST_ACCOUNT_ID)

        coVerify { accountDao.markAccountExpired(TEST_ACCOUNT_ID) }
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
        coVerify { accountDao.markAccountSynced(TEST_ACCOUNT_ID) }
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
        repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = true)

        coVerify { accountDao.deleteAccountById(TEST_ACCOUNT_ID) }
        coVerify { accountDao.deactivateAllAccounts() }
        coVerify { tokenManager.clearTokens() }
        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
    }

    // -------------------------------------------------------------------------
    // Notification alert
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownNotificationAlertForAccount delegates to userDataStore`() = runTest {
        coEvery { userDataStore.hasShownNotificationAlertForAccount(TEST_ACCOUNT_ID) } returns true

        val result = repository.hasShownNotificationAlertForAccount(TEST_ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `setNotificationAlertShownForAccount delegates to userDataStore`() = runTest {
        repository.setNotificationAlertShownForAccount(TEST_ACCOUNT_ID, true)

        coVerify { userDataStore.setNotificationAlertShownForAccount(TEST_ACCOUNT_ID, true) }
    }

    // -------------------------------------------------------------------------
    // Auth: signup error
    // -------------------------------------------------------------------------

    @Test
    fun `signup when API throws rethrows exception`() = runTest {
        coEvery { authAPI.createAccount(any()) } throws RuntimeException("Signup failed")

        try {
            repository.signup(SignupRequest("e@e.com", "John", "Doe", "male", "12345", "pass", "1990-01-01", 1780))
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Signup failed")
        }
    }

    // -------------------------------------------------------------------------
    // Auth: updatePassword error
    // -------------------------------------------------------------------------

    @Test
    fun `updatePassword when API throws rethrows exception`() = runTest {
        coEvery { userAPI.changePassword(any()) } throws RuntimeException("Change failed")

        try {
            repository.updatePassword(TEST_ACCOUNT_ID, "old", "new")
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Change failed")
        }
    }

    // -------------------------------------------------------------------------
    // Auth: resetPassword error
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword when API throws rethrows exception`() = runTest {
        coEvery { authAPI.requestPasswordReset(any()) } throws RuntimeException("Reset failed")

        try {
            repository.resetPassword("test@example.com")
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Reset failed")
        }
    }

    // -------------------------------------------------------------------------
    // DB: updateLocalDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `updateLocalDashboardType with null existing settings inserts with empty lists`() = runTest {
        every { accountDao.getDashboardSettings(TEST_ACCOUNT_ID) } returns flowOf(null)

        repository.updateLocalDashboardType(TEST_ACCOUNT_ID, DashboardType.DASHBOARD_4_METRICS)

        coVerify { accountDao.insertDashboardSettings(match { it.dashboardType == DashboardType.DASHBOARD_4_METRICS.value && it.dashboardMetrics.isEmpty() }) }
    }

    @Test
    fun `updateLocalDashboardType preserves existing metrics and milestones`() = runTest {
        val existingSettings = DashboardSettingsEntity(
            accountId = TEST_ACCOUNT_ID,
            dashboardMetrics = listOf("weight", "bmi"),
            dashboardMilestones = listOf("streak"),
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            isSynced = true,
        )
        every { accountDao.getDashboardSettings(TEST_ACCOUNT_ID) } returns flowOf(existingSettings)

        repository.updateLocalDashboardType(TEST_ACCOUNT_ID, DashboardType.DASHBOARD_12_METRICS)

        coVerify {
            accountDao.insertDashboardSettings(match {
                it.dashboardType == DashboardType.DASHBOARD_12_METRICS.value &&
                    it.dashboardMetrics == listOf("weight", "bmi") &&
                    it.dashboardMilestones == listOf("streak")
            })
        }
    }

    // -------------------------------------------------------------------------
    // DB: updateDashboardSettings
    // -------------------------------------------------------------------------

    @Test
    fun `updateDashboardSettings inserts entity with provided values`() = runTest {
        repository.updateDashboardSettings(
            accountId = TEST_ACCOUNT_ID,
            dashboardMetrics = listOf("weight"),
            dashboardMilestones = listOf("streak"),
            dashboardType = DashboardType.DASHBOARD_4_METRICS,
            isSynced = false,
        )

        coVerify {
            accountDao.insertDashboardSettings(match {
                it.accountId == TEST_ACCOUNT_ID &&
                    it.dashboardMetrics == listOf("weight") &&
                    it.dashboardMilestones == listOf("streak") &&
                    it.dashboardType == DashboardType.DASHBOARD_4_METRICS.value &&
                    !it.isSynced
            })
        }
    }

    // -------------------------------------------------------------------------
    // updateSyncTimeStamp
    // -------------------------------------------------------------------------

    @Test
    fun `updateSyncTimeStamp saves timestamp for active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(entityAccountWithRelations)

        repository.updateSyncTimeStamp("ts-123")

        coVerify { userDataStore.updateSyncTimestamp(TEST_ACCOUNT_ID, "ts-123") }
    }

    @Test
    fun `updateSyncTimeStamp uses empty string when no active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        repository.updateSyncTimeStamp("ts-123")

        coVerify { userDataStore.updateSyncTimestamp("", "ts-123") }
    }

    // -------------------------------------------------------------------------
    // getSyncTimeStamp
    // -------------------------------------------------------------------------

    @Test
    fun `getSyncTimeStamp returns syncTimestamp from currentAccountFlow`() = runTest {
        val userAccount = mockk<UserAccount>()
        every { userAccount.syncTimestamp } returns "ts-abc"
        every { userDataStore.currentAccountFlow } returns flowOf(userAccount)

        val result = repository.getSyncTimeStamp().first()

        assertThat(result).isEqualTo("ts-abc")
    }

    @Test
    fun `getSyncTimeStamp returns empty string when currentAccountFlow emits null`() = runTest {
        every { userDataStore.currentAccountFlow } returns flowOf(null)

        val result = repository.getSyncTimeStamp().first()

        assertThat(result).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // updateAccountInfo
    // -------------------------------------------------------------------------

    @Test
    fun `updateAccountInfo success updates account via updateAccount`() = runTest {
        coEvery { accountDao.getAccountEntity(TEST_ACCOUNT_ID) } returns accountEntity

        repository.updateAccountInfo(TEST_ACCOUNT_ID, accountInfo)

        coVerify { accountDao.updateAccount(match { it.firstName == "John" }) }
    }

    @Test
    fun `updateAccountInfo exception is swallowed silently`() = runTest {
        coEvery { accountDao.getAccountEntity(TEST_ACCOUNT_ID) } throws RuntimeException("DB error")

        // Should not throw
        repository.updateAccountInfo(TEST_ACCOUNT_ID, accountInfo)
    }

    // -------------------------------------------------------------------------
    // markAccountExpired
    // -------------------------------------------------------------------------

    @Test
    fun `markAccountExpired exception is swallowed silently`() = runTest {
        coEvery { accountDao.markAccountExpired(any()) } throws RuntimeException("DB error")

        // Should not throw
        repository.markAccountExpired(TEST_ACCOUNT_ID)
    }

    // -------------------------------------------------------------------------
    // getUnsyncedActiveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `getUnsyncedActiveAccount returns null when DAO emits null`() = runTest {
        every { accountDao.getUnsyncedActiveAccount() } returns flowOf(null)

        val result = repository.getUnsyncedActiveAccount()

        assertThat(result).isNull()
    }

    @Test
    fun `getUnsyncedActiveAccount returns mapped account when DAO emits account`() = runTest {
        every { accountDao.getUnsyncedActiveAccount() } returns flowOf(entityAccountWithRelations)

        val result = repository.getUnsyncedActiveAccount()

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(TEST_ACCOUNT_ID)
    }

    // -------------------------------------------------------------------------
    // getUnsyncedActiveDashboardSettings
    // -------------------------------------------------------------------------

    @Test
    fun `getUnsyncedActiveDashboardSettings returns null when DAO emits null`() = runTest {
        every { accountDao.getUnsyncedActiveDashboardSettings() } returns flowOf(null)

        val result = repository.getUnsyncedActiveDashboardSettings()

        assertThat(result).isNull()
    }

    @Test
    fun `getUnsyncedActiveDashboardSettings returns entity when DAO emits entity`() = runTest {
        val entity = DashboardSettingsEntity(
            accountId = TEST_ACCOUNT_ID,
            dashboardMetrics = listOf("weight"),
            dashboardMilestones = emptyList(),
            dashboardType = TEST_DASHBOARD_TYPE,
            isSynced = false,
        )
        every { accountDao.getUnsyncedActiveDashboardSettings() } returns flowOf(entity)

        val result = repository.getUnsyncedActiveDashboardSettings()

        assertThat(result).isEqualTo(entity)
    }

    // -------------------------------------------------------------------------
    // logoutAccount additional branch
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAccount non-active account does not deactivate all accounts`() = runTest {
        val result = repository.logoutAccount(TEST_ACCOUNT_ID, null, isActiveAccount = false)

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountDao.deactivateAllAccounts() }
        coVerify { accountDao.logoutAccount(TEST_ACCOUNT_ID) }
        coVerify { accountDao.markAccountExpired(TEST_ACCOUNT_ID) }
        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
    }

    // -------------------------------------------------------------------------
    // logoutAllAccounts error
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAllAccounts when DAO throws returns false`() = runTest {
        every { accountDao.getAllLoggedInAccounts() } returns flowOf(listOf(entityAccountWithRelations))
        coEvery { accountDao.logoutAllAccounts() } throws RuntimeException("DB error")

        val result = repository.logoutAllAccounts()

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // removeAccount
    // -------------------------------------------------------------------------

    @Test
    fun `removeAccount clears account tokens via userDataStore`() = runTest {
        repository.removeAccount(TEST_ACCOUNT_ID)

        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
    }

    @Test
    fun `removeAccount swallows exception from clearAccountTokens`() = runTest {
        coEvery { userDataStore.clearAccountTokens(any()) } throws RuntimeException("error")

        // Should not throw
        repository.removeAccount(TEST_ACCOUNT_ID)
    }

    // -------------------------------------------------------------------------
    // deleteAccount additional branches
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount non-active account skips server delete and DB delete`() = runTest {
        repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = false)

        coVerify(exactly = 0) { accountDao.deleteAccountById(any()) }
        coVerify(exactly = 0) { accountDao.deactivateAllAccounts() }
        coVerify { userDataStore.clearAccountTokens(TEST_ACCOUNT_ID) }
        coVerify { tokenManager.clearTokens() }
    }

    @Test
    fun `deleteAccount when DAO throws rethrows exception`() = runTest {
        coEvery { accountDao.deleteAccountById(any()) } throws RuntimeException("DB error")

        try {
            repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = true)
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("DB error")
        }
    }

    // -------------------------------------------------------------------------
    // getActiveAccountWeightUnitFlow
    // -------------------------------------------------------------------------

    @Test
    fun `getActiveAccountWeightUnitFlow emits weight unit from active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(entityAccountWithRelations)

        val result = repository.getActiveAccountWeightUnitFlow().first()

        assertThat(result).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `getActiveAccountWeightUnitFlow emits null when no active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        val result = repository.getActiveAccountWeightUnitFlow().first()

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getActiveAccountWeightlessFlow
    // -------------------------------------------------------------------------

    @Test
    fun `getActiveAccountWeightlessFlow emits Weightless from active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(entityAccountWithRelations)

        val result = repository.getActiveAccountWeightlessFlow().first()

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // currentThemeModeFlow
    // -------------------------------------------------------------------------

    @Test
    fun `currentThemeModeFlow is backed by userDataStore currentThemeModeFlow`() = runTest {
        val result = repository.currentThemeModeFlow.first()

        assertThat(result).isEqualTo(ThemeMode.SYSTEM)
    }

    // -------------------------------------------------------------------------
    // setCurrentThemeMode
    // -------------------------------------------------------------------------

    @Test
    fun `setCurrentThemeMode with active account calls userDataStore setThemeMode`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(entityAccountWithRelations)

        repository.setCurrentThemeMode(ThemeMode.DARK)

        coVerify { userDataStore.setThemeMode(TEST_ACCOUNT_ID, ThemeMode.DARK) }
    }

    @Test
    fun `setCurrentThemeMode with no active account does not call setThemeMode`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        repository.setCurrentThemeMode(ThemeMode.DARK)

        coVerify(exactly = 0) { userDataStore.setThemeMode(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // syncAccountSettingsWithServer error propagation
    // -------------------------------------------------------------------------

    @Test
    fun `syncAccountSettingsWithServer when DAO throws rethrows exception`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity
        coEvery { accountDao.updateWeightlessSettings(any()) } throws RuntimeException("DB error")

        try {
            repository.syncAccountSettingsWithServer(accountInfo, isOnline = true)
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("DB error")
        }
    }

    // -------------------------------------------------------------------------
    // Branch coverage: addAccount with null optional fields
    // -------------------------------------------------------------------------

    @Test
    fun `addAccount new account with null height and activityLevel uses defaults`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null
        val account = buildDomainAccount().copy(height = null, activityLevel = null)

        repository.addAccount(account)

        coVerify { accountDao.insertWeightCompSettings(match { it.height == 1700 && it.activityLevel == "normal" }) }
    }

    @Test
    fun `addAccount new account with null streak isWeightlessOn uses false defaults`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null
        val account = buildDomainAccount().copy(isStreakOn = null, isWeightlessOn = null, weightlessWeight = null)

        repository.addAccount(account)

        coVerify { accountDao.insertStreaksSettings(match { !it.isStreakOn }) }
        coVerify { accountDao.insertWeightlessSettings(match { !it.isWeightlessOn && it.weightlessWeight == 0.0f }) }
    }

    // -------------------------------------------------------------------------
    // Branch coverage: updateAccount with multiple fields
    // -------------------------------------------------------------------------

    @Test
    fun `updateAccount with all partial fields set overwrites all provided fields`() = runTest {
        coEvery { accountDao.getAccountEntity(TEST_ACCOUNT_ID) } returns accountEntity

        repository.updateAccount(
            TEST_ACCOUNT_ID,
            PartialAccount(
                firstName = "Jane",
                lastName = "Smith",
                email = "jane@example.com",
                dob = "1995-05-05",
                gender = "female",
                zipcode = "99999",
                isActiveAccount = false,
                isLoggedIn = false,
                isSynced = true,
            ),
        )

        coVerify {
            accountDao.updateAccount(match {
                it.firstName == "Jane" && it.lastName == "Smith" &&
                    it.email == "jane@example.com" && it.gender == "female" &&
                    !it.isActiveAccount && !it.isLoggedIn && it.isSynced
            })
        }
    }

    // -------------------------------------------------------------------------
    // Branch coverage: logoutAccount outer exception → returns false
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAccount when accountDao logoutAccount throws returns false`() = runTest {
        coEvery { accountDao.logoutAccount(any()) } throws RuntimeException("DB crash")

        val result = repository.logoutAccount(TEST_ACCOUNT_ID, null, isActiveAccount = false)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // Branch coverage: logoutAllAccounts with empty account list
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAllAccounts with no logged-in accounts still clears and returns true`() = runTest {
        every { accountDao.getAllLoggedInAccounts() } returns flowOf(emptyList())

        val result = repository.logoutAllAccounts()

        assertThat(result).isTrue()
        coVerify { accountDao.logoutAllAccounts() }
        coVerify { tokenManager.clearTokens() }
    }

    // -------------------------------------------------------------------------
    // Branch coverage: addAccountFromResponse non-null goalWeight/initialWeight
    // -------------------------------------------------------------------------

    @Test
    fun `login with non-null goalWeight and initialWeight maps them correctly`() = runTest {
        val infoWithGoal = accountInfo.copy(
            goalWeight = 70.0f,
            initialWeight = 80.0f,
            isFitbitOn = false,
            isFitbitValid = false,
            isHealthConnectOn = false,
            isHealthKitOn = false,
            isMFPOn = false,
            isMFPValid = false,
            goalPercent = 0,
        )
        val responseWithGoal = loginResponse.copy(account = infoWithGoal)
        coEvery { authAPI.login(any()) } returns responseWithGoal
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val result = repository.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result.goalWeight).isEqualTo(70.0)
        assertThat(result.initialWeight).isEqualTo(80.0)
    }

    // -------------------------------------------------------------------------
    // Branch coverage: syncAccountSettingsWithServer with non-null optional fields
    // -------------------------------------------------------------------------

    @Test
    fun `syncAccountSettingsWithServer with non-null weightless and goal fields uses provided values`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity
        val infoWithValues = accountInfo.copy(
            weightlessTimestamp = "ts-123",
            weightlessWeight = 65.5f,
            goalWeight = 70.0f,
            initialWeight = 80.0f,
            isFitbitOn = false,
            isFitbitValid = false,
            isHealthConnectOn = false,
            isHealthKitOn = false,
            isMFPOn = false,
            isMFPValid = false,
            goalPercent = 0,
        )

        repository.syncAccountSettingsWithServer(infoWithValues, isOnline = false)

        coVerify { accountDao.updateWeightlessSettings(match { it.weightlessTimestamp == "ts-123" && it.weightlessWeight == 65.5f }) }
        coVerify { accountDao.updateGoalSettings(match { it.goalWeight == "70.0" && it.weight == 80.0f }) }
    }

    // -------------------------------------------------------------------------
    // updateLastActiveTime
    // -------------------------------------------------------------------------

    @Test
    fun `updateLastActiveTime calls accountDao updateLastActiveTime with accountId and timestamp`() = runTest {
        repository.updateLastActiveTime(TEST_ACCOUNT_ID)

        coVerify { accountDao.updateLastActiveTime(eq(TEST_ACCOUNT_ID), any()) }
    }

    @Test
    fun `updateLastActiveTime passes a non-empty timestamp string`() = runTest {
        repository.updateLastActiveTime(TEST_ACCOUNT_ID)

        coVerify { accountDao.updateLastActiveTime(eq(TEST_ACCOUNT_ID), match { it.isNotEmpty() }) }
    }

    // -------------------------------------------------------------------------
    // addAccountFromLoginResponse
    // -------------------------------------------------------------------------

    @Test
    fun `addAccountFromLoginResponse adds account and sets active with tokens`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null

        val result = repository.addAccountFromLoginResponse(loginResponse)

        assertThat(result.id).isEqualTo(TEST_ACCOUNT_ID)
        assertThat(result.email).isEqualTo(TEST_EMAIL)
        // Verifies setActiveAccountAndTokens was called (activateAccount + deactivateOtherAccounts + setActiveAccount)
        coVerify { accountDao.activateAccount(TEST_ACCOUNT_ID) }
        coVerify { accountDao.deactivateOtherAccounts(TEST_ACCOUNT_ID) }
        coVerify { userDataStore.setActiveAccount(TEST_ACCOUNT_ID) }
        // Verifies setTokensForAccount was called (tokenManager.setTokens with accessToken)
        coVerify { tokenManager.setTokens(match { it.accessToken == TEST_ACCESS_TOKEN && it.refreshToken == TEST_REFRESH_TOKEN }) }
    }

    @Test
    fun `addAccountFromLoginResponse updates existing account when account already exists`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns accountEntity

        val result = repository.addAccountFromLoginResponse(loginResponse)

        assertThat(result.id).isEqualTo(TEST_ACCOUNT_ID)
        coVerify { accountDao.updateAccount(any()) }
        coVerify(exactly = 0) { accountDao.insertAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // deleteAccountFromServer (private, tested via deleteAccount)
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount active account calls userAPI deleteAccount`() = runTest {
        repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = true)

        coVerify { userAPI.deleteAccount() }
    }

    @Test
    fun `deleteAccount active account when server delete fails still clears local data`() = runTest {
        coEvery { userAPI.deleteAccount() } throws RuntimeException("Server error")

        try {
            repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = true)
            error("Expected exception not thrown")
        } catch (_: RuntimeException) {
            // expected — deleteAccount rethrows
        }

        coVerify { userAPI.deleteAccount() }
    }

    @Test
    fun `deleteAccount non-active account does not call server delete`() = runTest {
        repository.deleteAccount(TEST_ACCOUNT_ID, isActiveAccount = false)

        coVerify(exactly = 0) { userAPI.deleteAccount() }
    }

    // -------------------------------------------------------------------------
    // setActiveAccountAndTokens (private, tested via switchToAccount and addAccountFromLoginResponse)
    // -------------------------------------------------------------------------

    @Test
    fun `switchToAccount calls setActiveAccountAndTokens which updates lastActiveTime`() = runTest {
        repository.switchToAccount(TEST_ACCOUNT_ID)

        // setActiveAccountAndTokens calls updateLastActiveTime
        coVerify { accountDao.updateLastActiveTime(eq(TEST_ACCOUNT_ID), any()) }
    }

    @Test
    fun `switchToAccount calls setActiveAccountAndTokens with null tokens so tokenManager is not called`() = runTest {
        repository.switchToAccount(TEST_ACCOUNT_ID)

        // setTokensForAccount with null tokens should not call tokenManager.setTokens
        coVerify(exactly = 0) { tokenManager.setTokens(any()) }
    }

    // -------------------------------------------------------------------------
    // setTokensForAccount (private, tested via updatePassword and addAccountFromLoginResponse)
    // -------------------------------------------------------------------------

    @Test
    fun `updatePassword sets tokens via setTokensForAccount with response tokens`() = runTest {
        val response = ChangePasswordResponse(TEST_NEW_ACCESS_TOKEN, TEST_NEW_REFRESH_TOKEN, TEST_EXPIRY)
        coEvery { userAPI.changePassword(any()) } returns response

        repository.updatePassword(TEST_ACCOUNT_ID, "old", "new")

        coVerify {
            tokenManager.setTokens(match {
                it.accountId == TEST_ACCOUNT_ID &&
                    it.accessToken == TEST_NEW_ACCESS_TOKEN &&
                    it.refreshToken == TEST_NEW_REFRESH_TOKEN &&
                    it.expiresAt == TEST_EXPIRY
            })
        }
    }

    @Test
    fun `addAccountFromLoginResponse passes tokens through setTokensForAccount to tokenManager`() = runTest {
        coEvery { accountDao.getAccountEntity(any()) } returns null

        repository.addAccountFromLoginResponse(loginResponse)

        coVerify {
            tokenManager.setTokens(match {
                it.accountId == TEST_ACCOUNT_ID &&
                    it.accessToken == TEST_ACCESS_TOKEN &&
                    it.refreshToken == TEST_REFRESH_TOKEN &&
                    it.expiresAt == TEST_EXPIRES_AT &&
                    it.isActive
            })
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildDomainAccount() = Account(
        id = TEST_ACCOUNT_ID,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        dob = TEST_DOB,
        email = TEST_EMAIL,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        weightUnit = WeightUnit.LB,
        height = TEST_HEIGHT,
        activityLevel = TEST_ACTIVITY_LEVEL,
        isActiveAccount = true,
        isLoggedIn = true,
    )

    private fun buildProfileUpdateRequest() = ProfileUpdateRequest(
        id = TEST_ACCOUNT_ID,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        dob = TEST_DOB,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        email = TEST_EMAIL,
    )
}
