# Repository Test Patterns

These patterns apply when testing classes in `data/repository/`.

Repository tests differ significantly from service tests. There is no `BaseService` inheritance, no `connectivityObserver`, `dialogQueueService`, or `appNavigationService`. Instead, repositories typically depend on:
- **Room DAOs** (e.g., `AccountDao`) — for local DB operations
- **Retrofit API interfaces** (e.g., `IAuthAPI`, `IUserAPI`) — for remote API calls
- **DataStores** (e.g., `UserDataStore`) — for key-value storage
- **TokenManager** (`ITokenManager`) — for auth token management

## What to read

Read these files in parallel:
- The repository source file (e.g., `AccountRepository.kt`)
- The interface it implements (e.g., `IAccountRepository`)
- All constructor dependency interfaces (DAO, API, DataStore, TokenManager)
- Entity mapper classes used (e.g., `AccountEntityMapper`)
- Request/response model classes (e.g., `LoginRequest`, `LoginResponse`, `ProfileUpdateRequest`)

Key things to note while reading:
- **Constructor parameters** -> become mocks
- **Methods that call API then write to DB** -> test both API call mapping AND DB write side effects
- **DB-only methods** -> test DAO interactions directly, no API stubs needed
- **`try { } catch { throw e }` (re-throw)** -> test that exception propagates
- **`try { } catch { }` (swallow)** -> test that exception is caught and method returns gracefully
- **`Flow<T>` returns** -> test with Turbine `.test { }` block
- **Methods that call multiple DAOs/APIs** -> verify ALL side effects
- **Entity mapper usage** -> may need `mockkObject(EntityMapper)` for edge cases

## Mocking rules

| Dependency | Mock Style | Reason |
|---|---|---|
| Room DAOs (`AccountDao`, etc.) | `mockk(relaxUnitFun = true)` | Strict for query returns, relaxed for insert/update/delete |
| Retrofit API interfaces (`IAuthAPI`, `IUserAPI`) | `mockk()` strict | Catch unexpected API calls |
| `UserDataStore` | `mockk(relaxed = true)` | Many write-only methods |
| `ITokenManager` | `mockk(relaxed = true)` | Fire-and-forget token storage |

> **Why `relaxUnitFun = true` for DAOs?** DAOs have many `suspend fun insert/update/delete` returning Unit that you don't always need to stub per-test. But query methods return non-Unit types and MUST be stubbed explicitly.

## File structure

```kotlin
package com.dmdbrands.gurus.weight.data.repository   // mirror source package

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.*
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
    private val fakeAccount = Account(/* ... all required fields ... */)
    private val fakeAccountEntity = /* AccountEntity or Account-with-relations */
    private val fakeLoginResponse = LoginResponse(/* ... */)

    @Before
    fun setUp() {
        repository = createRepository()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createRepository() = AccountRepository(
        accountDao, userDataStore, tokenManager, authAPI, userAPI,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    // httpException(code) — imported from com.dmdbrands.gurus.weight.core.helpers.httpException
    // No private copy needed.

    // -------------------------------------------------------------------------
    // {methodName}
    // -------------------------------------------------------------------------
}
```

## Test patterns by category

### Pattern A: API call -> DB write -> return result

```kotlin
@Test
fun `signup calls API then saves account to DAO`() = runTest {
    coEvery { authAPI.createAccount(any()) } returns fakeLoginResponse
    coEvery { accountDao.getAccountEntity(any()) } returns null

    val result = repository.signup(fakeSignupRequest)

    assertThat(result.id).isEqualTo("acc-1")
    coVerify { authAPI.createAccount(match { it.email == fakeSignupRequest.email }) }
    coVerify { accountDao.insertAccount(any()) }
    coVerify { tokenManager.setTokens(match { it.accessToken == "access-token" }) }
}
```

### Pattern B: API call with token update

```kotlin
@Test
fun `updatePassword calls API and updates tokens`() = runTest {
    coEvery { userAPI.changePassword(any()) } returns fakeChangePasswordResponse

    val result = repository.updatePassword("acc-1", "old", "new")

    assertThat(result.accessToken).isEqualTo("new-access")
    coVerify { userAPI.changePassword(match { it.oldPassword == "old" && it.newPassword == "new" }) }
    coVerify { tokenManager.setTokens(match { it.accessToken == "new-access" && it.accountId == "acc-1" }) }
}
```

### Pattern C: DB-only methods (no API call)

```kotlin
@Test
fun `updateAccount merges partial update with existing entity`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns fakeAccountEntity

    repository.updateAccount("acc-1", PartialAccount(firstName = "Jane"))

    coVerify {
        accountDao.updateAccount(match { it.firstName == "Jane" && it.lastName == fakeAccountEntity.lastName })
    }
}

@Test
fun `deactivateOtherAccounts delegates to DAO`() = runTest {
    repository.deactivateOtherAccounts("acc-1")
    coVerify(exactly = 1) { accountDao.deactivateOtherAccounts("acc-1") }
}
```

### Pattern D: Insert-or-update branching

```kotlin
// INSERT path (new)
@Test
fun `addAccount inserts new account when not in DB`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns null
    repository.addAccount(fakeAccount)
    coVerify { accountDao.insertAccount(any()) }
    coVerify(exactly = 0) { accountDao.updateAccount(any()) }
}

// UPDATE path (existing)
@Test
fun `addAccount updates existing account when already in DB`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns fakeAccountEntity
    repository.addAccount(fakeAccount)
    coVerify { accountDao.updateAccount(any()) }
    coVerify(exactly = 0) { accountDao.insertAccount(any()) }
}
```

### Pattern E: API call with offline fallback

```kotlin
@Test
fun `updateProfile saves synced data on API success`() = runTest {
    coEvery { userAPI.updateProfile(any()) } returns apiResponse
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity
    repository.updateProfile(fakeProfileRequest)
    coVerify { accountDao.updateAccount(match { it.isSynced == true }) }
}

@Test
fun `updateProfile saves unsynced data on network error and re-throws`() = runTest {
    coEvery { userAPI.updateProfile(any()) } throws httpException(0)
    coEvery { accountDao.getAccountEntity(any()) } returns fakeAccountEntity
    assertThrows(HttpException::class.java) {
        runBlocking { repository.updateProfile(fakeProfileRequest) }
    }
    coVerify { accountDao.updateAccount(match { it.isSynced == false }) }
}
```

### Pattern F: Flow-returning methods

```kotlin
@Test
fun `getActiveAccount returns mapped domain model from DAO flow`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(fakeAccountWithRelations)
    repository.getActiveAccount().test {
        assertThat(awaitItem()?.id).isEqualTo("acc-1")
        awaitComplete()
    }
}
```

### Pattern G: Cascading side effects (logout)

```kotlin
@Test
fun `logoutAccount performs API logout then clears local data`() = runTest {
    coEvery { authAPI.logoutWithToken(any(), any()) } returns Unit
    val result = repository.logoutAccount("acc-1", "fcm-token", isActiveAccount = true)
    assertThat(result).isTrue()
    coVerify { authAPI.logoutWithToken(match { it.fcmToken == "fcm-token" }, "acc-1") }
    coVerify { accountDao.logoutAccount("acc-1") }
    coVerify { userDataStore.clearAccountTokens("acc-1") }
}

@Test
fun `logoutAccount succeeds even when API call fails`() = runTest {
    coEvery { authAPI.logoutWithToken(any(), any()) } throws httpException(500)
    val result = repository.logoutAccount("acc-1", "fcm-token", isActiveAccount = true)
    assertThat(result).isTrue()
    coVerify { accountDao.logoutAccount("acc-1") }
}
```

### Pattern H: Catch-and-swallow vs catch-and-rethrow

```kotlin
// catch-and-rethrow
@Test
fun `login rethrows exception from API`() = runTest {
    coEvery { authAPI.login(any()) } throws RuntimeException("network")
    assertThrows(RuntimeException::class.java) {
        runBlocking { repository.login("email", "pass") }
    }
}

// catch-and-swallow
@Test
fun `updateAccountInfo swallows exception and does not throw`() = runTest {
    coEvery { accountDao.getAccountEntity(any()) } throws RuntimeException("DB error")
    repository.updateAccountInfo("acc-1", fakeAccountInfo)  // no throw
}
```

### Pattern I: Verify request object construction

```kotlin
@Test
fun `updatePassword builds correct ChangePasswordRequest`() = runTest {
    val requestSlot = slot<ChangePasswordRequest>()
    coEvery { userAPI.changePassword(capture(requestSlot)) } returns fakeChangePasswordResponse
    repository.updatePassword("acc-1", "oldPwd", "newPwd")
    assertThat(requestSlot.captured.oldPassword).isEqualTo("oldPwd")
    assertThat(requestSlot.captured.newPassword).isEqualTo("newPwd")
}
```

### Pattern J: Delegation methods (thin wrappers)

2 tests each (happy + error):

```kotlin
@Test
fun `clearAccountTokens delegates to userDataStore`() = runTest {
    repository.clearAccountTokens("acc-1")
    coVerify(exactly = 1) { userDataStore.clearAccountTokens("acc-1") }
}
```

### Pattern K: Entity settings insert/update verification

```kotlin
@Test
fun `addAccount inserts all settings entities for new account`() = runTest {
    coEvery { accountDao.getAccountEntity("acc-1") } returns null
    repository.addAccount(fakeAccount)
    coVerify { accountDao.insertWeightCompSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertNotificationSettings(match { it.accountId == "acc-1" }) }
    coVerify { accountDao.insertStreaksSettings(match { it.accountId == "acc-1" }) }
}
```

## Repository vs Service — key differences

| Aspect | Service Test | Repository Test |
|---|---|---|
| **SUT name** | `service` | `repository` |
| **Base class** | Extends `BaseService` | No base class |
| **Network checks** | `isNetworkAvailable()` / `requireNetworkAvailable()` | None — caller (service) handles network |
| **Primary mocks** | Repositories, DataStores | DAOs, APIs, TokenManager, DataStores |
| **Dialog/toast testing** | `slot<DialogModel>()` captures | N/A — repositories don't show UI |
| **Error pattern** | Catches -> shows toast, returns null | Catches -> re-throws or swallows |
| **`relaxUnitFun = true`** | Rarely needed | Common for DAOs |

## Repository-specific success criteria

- [ ] DAOs mocked with `mockk(relaxUnitFun = true)`
- [ ] API interfaces mocked with `mockk()` strict
- [ ] `repository` naming used (not `service`)
- [ ] API -> DB write chains verified end-to-end
- [ ] Insert-or-update branches both tested
- [ ] Catch-and-rethrow tested with `assertThrows`; catch-and-swallow tested to verify no throw
- [ ] Request object construction verified via `slot` + `capture`
- [ ] All settings entity inserts/updates verified
- [ ] Cascading operations verified (API + DAO + DataStore + TokenManager)
- [ ] Flow-returning methods tested with Turbine
- [ ] Shared `httpException()` imported from `core.helpers.TestHelpers` — no private copy
