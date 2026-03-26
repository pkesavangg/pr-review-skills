# Repository Test Patterns

These patterns apply when testing classes in `data/repository/`.

Repository tests differ significantly from service tests. There is no `BaseService` inheritance, no `connectivityObserver`, `dialogQueueService`, or `appNavigationService`. Instead, repositories typically depend on:
- **Room DAOs** (e.g., `AccountDao`) — for local DB operations
- **Retrofit API interfaces** (e.g., `IAuthAPI`, `IUserAPI`) — for remote API calls
- **DataStores** (e.g., `UserDataStore`) — for key-value storage
- **TokenManager** (`ITokenManager`) — for auth token management

## Step 9.1: Read the source files (repositories)

Read these files in parallel:
- The repository source file (e.g., `AccountRepository.kt`)
- The interface it implements (e.g., `IAccountRepository`)
- All constructor dependency interfaces (DAO, API, DataStore, TokenManager)
- Entity mapper classes used (e.g., `AccountEntityMapper`)
- Request/response model classes (e.g., `LoginRequest`, `LoginResponse`, `ProfileUpdateRequest`)

Key things to note while reading:
- **Constructor parameters** → these become your mocks
- **Which methods call API then write to DB** → test both API call mapping AND DB write side effects
- **Which methods are DB-only** → test DAO interactions directly, no API stubs needed
- **Which methods have `try { } catch { throw e }` (re-throw)** → test that exception propagates
- **Which methods have `try { } catch { }` (swallow)** → test that exception is caught and method returns gracefully
- **Which methods return `Flow<T>`** → test with Turbine `.test { }` block
- **Which methods call multiple DAOs/APIs** → verify ALL side effects
- **Private helper methods called by public ones** → cover through public method tests
- **Entity mapper usage** → may need `mockkObject(EntityMapper)` for edge cases

## Step 9.2: Repository mocking rules

| Dependency | Mock Style | Reason |
|---|---|---|
| Room DAOs (`AccountDao`, etc.) | `mockk(relaxUnitFun = true)` | Strict for query returns, relaxed for insert/update/delete Unit functions |
| Retrofit API interfaces (`IAuthAPI`, `IUserAPI`) | `mockk()` strict | Catch unexpected API calls |
| `UserDataStore` | `mockk(relaxed = true)` | Many write-only methods |
| `ITokenManager` | `mockk(relaxed = true)` | Fire-and-forget token storage |

> **Why `relaxUnitFun = true` for DAOs?** DAOs have many `suspend fun insert/update/delete` returning Unit that you don't always need to stub per-test. But query methods (`getActiveAccount()`, `getAccountEntity()`) return non-Unit types and MUST be stubbed explicitly — `relaxUnitFun` leaves those strict.

## Step 9.3: Repository file structure

```kotlin
package com.dmdbrands.gurus.weight.data.repository   // mirror source package

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
// + DAO entity imports, API request/response imports, domain model imports

@OptIn(ExperimentalCoroutinesApi::class)
class {RepositoryName}Test {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val accountDao: AccountDao = mockk(relaxUnitFun = true)
    private val userDataStore: UserDataStore = mockk(relaxed = true)
    private val tokenManager: ITokenManager = mockk(relaxed = true)
    private val authAPI: IAuthAPI = mockk()
    private val userAPI: IUserAPI = mockk()

    private lateinit var repository: AccountRepository

    // --- Inline Test Fixtures ---
    // Use real domain objects. Build API response fixtures too.
    private val fakeAccount = Account(
        id = "acc-1",
        firstName = "John",
        lastName = "Doe",
        email = "john@example.com",
        // ... all required fields
    )

    // DB entity fixture (what DAO returns)
    private val fakeAccountEntity = // ... AccountEntity or Account-with-relations object

    // API response fixtures
    private val fakeLoginResponse = LoginResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = "2026-12-31T00:00:00Z",
        account = AccountInfo(
            id = "acc-1",
            firstName = "John",
            // ... all fields matching API response
        ),
    )

    @Before
    fun setUp() {
        repository = createRepository()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createRepository() = AccountRepository(
        accountDao,
        userDataStore,
        tokenManager,
        authAPI,
        userAPI,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    private fun httpException(code: Int): HttpException {
        val response = mockk<Response<*>> {
            every { code() } returns code
            every { message() } returns "Mock HTTP error"
            every { errorBody() } returns null
        }
        return HttpException(response)
    }

    // Stub helpers for repeated patterns
    private fun stubDaoGetActiveAccount(account: Account? = fakeAccountEntity) {
        every { accountDao.getActiveAccount() } returns flowOf(account)
    }

    private fun stubDaoGetAccountEntity(account: AccountEntity? = fakeAccountEntity) {
        coEvery { accountDao.getAccountEntity(any()) } returns account
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    fun `login calls authAPI and saves account to DAO`() = runTest {
        coEvery { authAPI.login(any()) } returns fakeLoginResponse
        coEvery { accountDao.getAccountEntity(any()) } returns null  // new account
        // ... other stubs for addAccount chain

        val result = repository.login("john@example.com", "password")

        assertThat(result.id).isEqualTo("acc-1")
        coVerify { authAPI.login(match { it.email == "john@example.com" }) }
        coVerify { accountDao.insertAccount(any()) }
        coVerify { tokenManager.setTokens(any()) }
    }

    @Test
    fun `login propagates HttpException from API`() = runTest {
        coEvery { authAPI.login(any()) } throws httpException(401)

        assertThrows(HttpException::class.java) {
            runBlocking { repository.login("john@example.com", "wrong") }
        }
        coVerify(exactly = 0) { accountDao.insertAccount(any()) }
    }
}
```

## Step 9.4: Repository test patterns by category

### Pattern A: API call → DB write → return result

Most repository methods call an API, then persist to the DAO, then return:

```kotlin
@Test
fun `signup calls API then saves account to DAO`() = runTest {
    coEvery { authAPI.createAccount(any()) } returns fakeLoginResponse
    coEvery { accountDao.getAccountEntity(any()) } returns null

    val result = repository.signup(fakeSignupRequest)

    assertThat(result.id).isEqualTo("acc-1")
    // Verify API called with correct request
    coVerify { authAPI.createAccount(match { it.email == fakeSignupRequest.email }) }
    // Verify DB write happened
    coVerify { accountDao.insertAccount(any()) }
    // Verify tokens stored
    coVerify { tokenManager.setTokens(match { it.accessToken == "access-token" }) }
}
```

### Pattern B: API call with token update

Methods that update tokens after API call (e.g., `updatePassword`):

```kotlin
@Test
fun `updatePassword calls API and updates tokens`() = runTest {
    val fakeResponse = ChangePasswordResponse(
        accessToken = "new-access",
        refreshToken = "new-refresh",
        expiresAt = "2026-12-31",
    )
    coEvery { userAPI.changePassword(any()) } returns fakeResponse

    val result = repository.updatePassword("acc-1", "old", "new")

    assertThat(result.accessToken).isEqualTo("new-access")
    coVerify { userAPI.changePassword(match { it.oldPassword == "old" && it.newPassword == "new" }) }
    coVerify { tokenManager.setTokens(match { it.accessToken == "new-access" && it.accountId == "acc-1" }) }
}

@Test
fun `updatePassword propagates exception`() = runTest {
    coEvery { userAPI.changePassword(any()) } throws httpException(401)

    assertThrows(HttpException::class.java) {
        runBlocking { repository.updatePassword("acc-1", "old", "new") }
    }
}
```

### Pattern C: DB-only methods (no API call)

Methods that only interact with the DAO — no API stubs needed:

```kotlin
@Test
fun `updateAccount merges partial update with existing entity`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns fakeAccountEntity

    repository.updateAccount("acc-1", PartialAccount(firstName = "Jane"))

    coVerify {
        accountDao.updateAccount(match {
            it.firstName == "Jane" && it.lastName == fakeAccountEntity.lastName
        })
    }
}

@Test
fun `updateAccount throws when account not found`() = runTest {
    coEvery { accountDao.getAccountEntity("missing") } returns null

    assertThrows(IllegalStateException::class.java) {
        runBlocking { repository.updateAccount("missing", PartialAccount(firstName = "Jane")) }
    }
}

@Test
fun `deactivateOtherAccounts delegates to DAO`() = runTest {
    repository.deactivateOtherAccounts("acc-1")

    coVerify(exactly = 1) { accountDao.deactivateOtherAccounts("acc-1") }
}
```

### Pattern D: Insert-or-update branching

Methods that check for existing records and branch on insert vs update:

```kotlin
// Test the INSERT path (new account)
@Test
fun `addAccount inserts new account when not in DB`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns null

    repository.addAccount(fakeAccount)

    coVerify { accountDao.insertAccount(any()) }
    coVerify(exactly = 0) { accountDao.updateAccount(any()) }
    // Also verify related settings inserted
    coVerify { accountDao.insertWeightCompSettings(any()) }
    coVerify { accountDao.insertNotificationSettings(any()) }
    coVerify { accountDao.insertStreaksSettings(any()) }
}

// Test the UPDATE path (existing account)
@Test
fun `addAccount updates existing account when already in DB`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns fakeAccountEntity

    repository.addAccount(fakeAccount)

    coVerify { accountDao.updateAccount(any()) }
    coVerify(exactly = 0) { accountDao.insertAccount(any()) }
    // Also verify related settings updated
    coVerify { accountDao.updateWeightCompSettings(any()) }
    coVerify { accountDao.updateNotificationSettings(any()) }
    coVerify { accountDao.updateStreaksSettings(any()) }
}
```

### Pattern E: API call with HttpException offline fallback

Methods that catch `HttpException` and store locally when offline (e.g., `updateProfile`):

```kotlin
@Test
fun `updateProfile saves synced data on API success`() = runTest {
    val apiResponse = // ... response with updated account
    coEvery { userAPI.updateProfile(any()) } returns apiResponse
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity

    repository.updateProfile(fakeProfileRequest)

    coVerify {
        accountDao.updateAccount(match { it.isSynced == true })
    }
}

@Test
fun `updateProfile saves unsynced data on network error and re-throws`() = runTest {
    coEvery { userAPI.updateProfile(any()) } throws httpException(0)
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity

    assertThrows(HttpException::class.java) {
        runBlocking { repository.updateProfile(fakeProfileRequest) }
    }

    // Verify local save with isSynced = false
    coVerify {
        accountDao.updateAccount(match { it.isSynced == false })
    }
}

@Test
fun `updateProfile re-throws non-network HttpException without local save`() = runTest {
    coEvery { userAPI.updateProfile(any()) } throws httpException(500)

    assertThrows(HttpException::class.java) {
        runBlocking { repository.updateProfile(fakeProfileRequest) }
    }

    // No local fallback for server errors
    coVerify(exactly = 0) { accountDao.updateAccount(any()) }
}
```

> **Key**: Use `HttpErrorResponse.isNetworkError(code)` to know which codes trigger the offline fallback. Test both the fallback path (network error code) and the re-throw-only path (server error code).

### Pattern F: Flow-returning methods

Methods that return `Flow<T>` from DAO queries:

```kotlin
@Test
fun `getActiveAccount returns mapped domain model from DAO flow`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(fakeAccountWithRelations)

    repository.getActiveAccount().test {
        val account = awaitItem()
        assertThat(account?.id).isEqualTo("acc-1")
        assertThat(account?.firstName).isEqualTo("John")
        awaitComplete()
    }
}

@Test
fun `getActiveAccount emits null when no active account in DAO`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(null)

    repository.getActiveAccount().test {
        assertThat(awaitItem()).isNull()
        awaitComplete()
    }
}

@Test
fun `getLoggedInAccounts maps all entities to domain models`() = runTest {
    every { accountDao.getAllLoggedInAccounts() } returns flowOf(
        listOf(fakeAccountWithRelations, fakeAccountWithRelations2)
    )

    repository.getLoggedInAccounts().test {
        val accounts = awaitItem()
        assertThat(accounts).hasSize(2)
        assertThat(accounts[0].id).isEqualTo("acc-1")
        assertThat(accounts[1].id).isEqualTo("acc-2")
        awaitComplete()
    }
}
```

### Pattern G: Logout with cascading side effects

Methods that perform multiple operations (API + DAO + DataStore + TokenManager):

```kotlin
@Test
fun `logoutAccount performs API logout then clears local data`() = runTest {
    coEvery { authAPI.logoutWithToken(any(), any()) } returns Unit

    val result = repository.logoutAccount("acc-1", "fcm-token", isActiveAccount = true)

    assertThat(result).isTrue()
    // Verify API called
    coVerify { authAPI.logoutWithToken(match { it.fcmToken == "fcm-token" }, "acc-1") }
    // Verify DB operations
    coVerify { accountDao.deactivateAllAccounts() }
    coVerify { accountDao.logoutAccount("acc-1") }
    coVerify { accountDao.markAccountExpired("acc-1") }
    // Verify token/datastore cleanup
    coVerify { userDataStore.clearAccountTokens("acc-1") }
}

@Test
fun `logoutAccount succeeds even when API call fails`() = runTest {
    coEvery { authAPI.logoutWithToken(any(), any()) } throws httpException(500)

    val result = repository.logoutAccount("acc-1", "fcm-token", isActiveAccount = true)

    // Should still succeed — API failure is caught
    assertThat(result).isTrue()
    // Local cleanup still happens
    coVerify { accountDao.logoutAccount("acc-1") }
    coVerify { userDataStore.clearAccountTokens("acc-1") }
}

@Test
fun `logoutAccount skips deactivateAll when not active account`() = runTest {
    coEvery { authAPI.logoutWithToken(any(), any()) } returns Unit

    repository.logoutAccount("acc-2", null, isActiveAccount = false)

    coVerify(exactly = 0) { accountDao.deactivateAllAccounts() }
    coVerify { accountDao.logoutAccount("acc-2") }
}

@Test
fun `logoutAccount returns false when outer catch triggers`() = runTest {
    // Force the DB operation to fail (after API succeeds)
    coEvery { authAPI.logoutWithToken(any(), any()) } returns Unit
    coEvery { accountDao.logoutAccount(any()) } throws RuntimeException("DB error")

    val result = repository.logoutAccount("acc-1", null, isActiveAccount = false)

    assertThat(result).isFalse()
}
```

### Pattern H: Catch-and-swallow vs catch-and-rethrow

Repositories use two distinct error handling strategies. Test both:

```kotlin
// catch-and-rethrow: exception propagates to caller
@Test
fun `login rethrows exception from API`() = runTest {
    coEvery { authAPI.login(any()) } throws RuntimeException("network")

    assertThrows(RuntimeException::class.java) {
        runBlocking { repository.login("email", "pass") }
    }
}

// catch-and-swallow: method handles error internally
@Test
fun `updateAccountInfo swallows exception and does not throw`() = runTest {
    coEvery { accountDao.getAccountEntity(any()) } throws RuntimeException("DB error")

    // Should NOT throw — catch block swallows
    repository.updateAccountInfo("acc-1", fakeAccountInfo)
    // Verify no crash — test passes if no exception thrown
}

@Test
fun `markAccountExpired swallows exception`() = runTest {
    coEvery { accountDao.markAccountExpired(any()) } throws RuntimeException("DB error")

    // Should not throw
    repository.markAccountExpired("acc-1")
}
```

### Pattern I: Verify request object construction

When the repository builds request objects from parameters, verify the mapping:

```kotlin
@Test
fun `updatePassword builds correct ChangePasswordRequest`() = runTest {
    val requestSlot = slot<ChangePasswordRequest>()
    coEvery { userAPI.changePassword(capture(requestSlot)) } returns fakeChangePasswordResponse

    repository.updatePassword("acc-1", "oldPwd", "newPwd")

    val captured = requestSlot.captured
    assertThat(captured.oldPassword).isEqualTo("oldPwd")
    assertThat(captured.newPassword).isEqualTo("newPwd")
}

@Test
fun `resetPassword builds PasswordResetRequest with correct email`() = runTest {
    val requestSlot = slot<PasswordResetRequest>()
    coEvery { authAPI.requestPasswordReset(capture(requestSlot)) } returns mockk()

    repository.resetPassword("john@example.com")

    assertThat(requestSlot.captured.email).isEqualTo("john@example.com")
}
```

### Pattern J: Delegation methods (thin wrappers)

Simple methods that delegate to a single dependency — 2 tests each (happy + error):

```kotlin
@Test
fun `clearAccountTokens delegates to userDataStore`() = runTest {
    repository.clearAccountTokens("acc-1")

    coVerify(exactly = 1) { userDataStore.clearAccountTokens("acc-1") }
}

@Test
fun `activateAccount delegates to accountDao`() = runTest {
    repository.activateAccount("acc-1")

    coVerify(exactly = 1) { accountDao.activateAccount("acc-1") }
}

@Test
fun `updateDashboardMetrics delegates to userAPI`() = runTest {
    coEvery { userAPI.updateDashboardMetrics(any()) } returns Unit

    repository.updateDashboardMetrics(listOf("weight", "bmi"))

    coVerify {
        userAPI.updateDashboardMetrics(match {
            it.dashboardMetrics == listOf("weight", "bmi")
        })
    }
}
```

### Pattern K: Entity settings insert/update verification

When the repository creates multiple settings entities, verify each one:

```kotlin
@Test
fun `addAccount inserts all settings entities for new account`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns null

    repository.addAccount(fakeAccount)

    // Verify ALL settings entities created
    coVerify { accountDao.insertWeightCompSettings(match { it.accountId == "acc-1" && it.weightUnit == "lb" }) }
    coVerify { accountDao.insertNotificationSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertStreaksSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertWeightlessSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertGoalSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertIntegrationsSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertDashboardSettings(match { it.accountId == "acc-1" }) }
}

@Test
fun `syncAccountSettingsWithServer marks synced when online`() = runTest {
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity

    repository.syncAccountSettingsWithServer(fakeAccountInfo, isOnline = true)

    coVerify { accountDao.markAccountSynced("acc-1") }
    coVerify { accountDao.updateWeightlessSettings(match { it.isSynced == true }) }
    coVerify { accountDao.updateWeightCompSettings(match { it.isSynced == true }) }
}

@Test
fun `syncAccountSettingsWithServer does NOT mark synced when offline`() = runTest {
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity

    repository.syncAccountSettingsWithServer(fakeAccountInfo, isOnline = false)

    coVerify(exactly = 0) { accountDao.markAccountSynced(any()) }
}
```

## Step 9.5: Repository vs Service — key differences summary

| Aspect | Service Test | Repository Test |
|---|---|---|
| **SUT name** | `service` | `repository` |
| **Base class** | Extends `BaseService` | No base class |
| **Network checks** | `isNetworkAvailable()` / `requireNetworkAvailable()` | None — caller (service) handles network |
| **Primary mocks** | Repositories, DataStores | DAOs, APIs, TokenManager, DataStores |
| **Dialog/toast testing** | `slot<DialogModel>()` captures | N/A — repositories don't show UI |
| **Error pattern** | Catches exception → shows toast, returns null | Catches exception → re-throws or swallows |
| **Flow sources** | Combines repository flows | Wraps DAO flows with `.map()` |
| **Side effect verification** | Repository writes, auth events, navigation | DAO writes, API calls, token storage |
| **`relaxUnitFun = true`** | Rarely needed | Common for DAOs |

## Step 9.6: Repository quick_start adjustment

When testing a repository, modify the quick_start steps:
1. Ask the user for the source file path if not provided.
2. Read the source file + its interface.
3. **Skip** `BaseService.kt` — repositories don't extend it.
4. Read constructor dependencies: DAO interface, API interfaces, DataStore, TokenManager.
5. Read entity/mapper classes used by the repository (e.g., `AccountEntityMapper`, entity data classes).
6. Read request/response model classes (e.g., `LoginRequest`, `LoginResponse`, `ProfileUpdateRequest`).
7. Identify ALL public methods, their dependencies, branches, catch blocks, and flows.
8. Cross-check method coverage completeness — every public method must have tests.
9. Verify ALL imports are present.
10. Generate the test file into `app/src/test/java/...` mirroring the source package.
11. Run tests → fix failures → re-run (iterative loop until green).
12. Run JaCoCo coverage → check method-level LINE + BRANCH → add missing tests → re-run.

## Repository-specific success criteria

- [ ] DAOs mocked with `mockk(relaxUnitFun = true)` — strict for queries, relaxed for Unit writes
- [ ] API interfaces mocked with `mockk()` strict — catch unexpected API calls
- [ ] `repository` naming used (not `service`)
- [ ] API call → DB write chains verified end-to-end (API called, DAO write happened, tokens stored)
- [ ] Insert-or-update branches both tested (existing vs new record)
- [ ] Catch-and-rethrow methods tested with `assertThrows`
- [ ] Catch-and-swallow methods tested to verify no exception escapes
- [ ] Request object construction verified via `slot<RequestType>()` + `capture()`
- [ ] All settings entity inserts/updates verified for multi-entity methods (e.g., `addAccount`)
- [ ] Cascading operations verified (API + DAO + DataStore + TokenManager side effects)
- [ ] Offline fallback paths tested where `HttpErrorResponse.isNetworkError()` is checked
- [ ] Flow-returning methods tested with Turbine for emission correctness
- [ ] Delegation methods have at least happy-path test verifying correct delegate call
