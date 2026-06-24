# Test File Templates & Shared Patterns

## Complete import block

Always include ALL of these. Add additional imports as needed for the specific class's types:

```kotlin
package com.dmdbrands.gurus.weight.core.service   // mirror source package

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
// + domain model imports for the specific class
```

> **CRITICAL**: Always import `kotlinx.coroutines.runBlocking` (needed for `assertThrows` with suspend), `retrofit2.HttpException`, and shared helpers from `com.dmdbrands.gurus.weight.core.helpers.*`.

## Full file structure (Service example)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class {ServiceName}Test {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks (see mocking rules table) ---
    private val accountRepository: IAccountRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: {ServiceName}

    // --- Inline Test Fixtures ---
    private val fakeAccount = Account(
        id = "acc-1",
        firstName = "John",
        lastName = "Doe",
        email = "john@example.com",
        // ... all required fields with sensible test values
    )

    private val fakeAccount2 = fakeAccount.copy(
        id = "acc-2",
        firstName = "Jane",
        email = "jane@example.com",
        isActiveAccount = false,
    )

    @Before
    fun setUp() {
        stubNetworkAvailable()
        // Stub flows collected at construction time (init blocks)
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount))
        service = createService()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createService() = {ServiceName}(
        accountRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
        ioDispatcher = mainDispatcherRule.dispatcher,  // if service has ioDispatcher param
    )

    // -------------------------------------------------------------------------
    // Shared Helpers (delegate to shared TestHelpers.kt)
    // -------------------------------------------------------------------------

    // httpException(code) — imported from core.helpers.httpException. No private copy.

    private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
    private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()

    // -------------------------------------------------------------------------
    // {methodName}
    // -------------------------------------------------------------------------

    @Test
    fun `{method} does X when Y`() = runTest {
        // Arrange
        // Act
        // Assert
    }
}
```

## Shared test helpers — TestHelpers.kt

Common helpers live at `app/src/test/java/com/dmdbrands/gurus/weight/core/helpers/TestHelpers.kt`. **Always import from here** — never write private copies:

```kotlin
import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
```

Available helpers:
- `httpException(code: Int): HttpException` — creates a mocked HttpException with the given HTTP status code
- `IConnectivityObserver.stubNetworkAvailable()` — extension to stub network as available
- `IConnectivityObserver.stubNetworkUnavailable()` — extension to stub network as unavailable

In the test class, delegate via private methods:
```kotlin
private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()
```

## Mocking rules

| Dependency | Mock Style | Reason |
|---|---|---|
| Repository interfaces (`IAccountRepository`, etc.) | `mockk()` strict | Catch unexpected calls |
| `IConnectivityObserver` | `mockk()` strict | Always stub explicitly |
| DataStore classes | `mockk(relaxed = true)` | Avoid stubbing every write |
| `IDialogQueueService` | `mockk(relaxed = true)` | Lambda-based enqueue, showToast, showLoader |
| `IAppNavigationService` | `mockk(relaxed = true)` | Fire-and-forget auth events |
| `StorageClearService` | `mockk(relaxed = true)` | Fire-and-forget |
| `IOfflineHandlerService` | `mockk(relaxed = true)` | Fire-and-forget |
| `IDeviceService` | `mockk(relaxed = true)` | Boolean returns default to false |
| Room DAOs | `mockk(relaxUnitFun = true)` | Strict for queries, relaxed for insert/update/delete |
| Retrofit API interfaces | `mockk()` strict | Catch unexpected API calls |
| `ITokenManager` | `mockk(relaxed = true)` | Fire-and-forget token storage |

> **`relaxed = true` vs `relaxUnitFun = true`**: Prefer `relaxUnitFun = true` when the dependency has mostly Unit-returning methods but also non-Unit methods you want strict checking on. Use full `relaxed = true` only for fire-and-forget dependencies where ALL return values are irrelevant.

> **`every` vs `coEvery` — CRITICAL**: Always use `coEvery` for `suspend` functions and `every` for non-suspend. Using `every` on a suspend function silently fails to stub it. Similarly, `coVerify` for suspend calls, `verify` for non-suspend.

> **`coJustRun`**: Instead of `coEvery { mock.method() } just Runs`, prefer the cleaner `coJustRun { mock.method() }`.

## Test fixture construction

Always read the data class source before constructing. Use `.copy()` for variants:

```kotlin
private val fakeAccount = Account(
    id = "acc-1", firstName = "John", lastName = "Doe",
    dob = "1990-01-01", email = "john@example.com", gender = "male",
    isActiveAccount = true, isLoggedIn = true, isExpired = false,
    zipcode = "12345", weightUnit = WeightUnit.LB, height = 1750,
    activityLevel = "normal",
)

private val fakeAccount2 = fakeAccount.copy(id = "acc-2", firstName = "Jane", email = "jane@example.com", isActiveAccount = false)
private val expiredAccount = fakeAccount.copy(isExpired = true)
private val nullAccount: Account? = null
```

## AAA pattern — mandatory for every test

```kotlin
@Test
fun `login returns account when credentials are valid`() = runTest {
    // Arrange
    coEvery { accountRepository.login(any(), any()) } returns fakeAccount

    // Act
    val result = service.login(fakeAccount.email, "password")

    // Assert
    assertThat(result).isEqualTo(fakeAccount)
    coVerify(exactly = 1) { accountRepository.login(fakeAccount.email, "password") }
}
```

> Always use `service`/`repository`/`viewModel` naming (never `sut`).

## Stub helpers — DRY rule

Never repeat the same `coEvery` block in 3+ tests. Extract into private helpers:

```kotlin
private fun stubLoginSuccess(account: Account = fakeAccount) {
    coEvery { accountRepository.login(any(), any()) } returns account
}
private fun stubLoginThrows(exception: Throwable) {
    coEvery { accountRepository.login(any(), any()) } throws exception
}
```

> Stub helpers only share Arrange code — never Act or Assert.

> **Stubbing order**: Last-defined stub wins. Put general stubs first, specific overrides after.

## assertThrows — suspend function pattern

`assertThrows` does NOT support suspend lambdas. Wrap in `runBlocking`:

```kotlin
// WRONG
assertThrows(MaxAccountsReachedException::class.java) {
    service.login("new@example.com", "password")  // suspend fun!
}

// CORRECT
assertThrows(MaxAccountsReachedException::class.java) {
    runBlocking { service.login("new@example.com", "password") }
}
```

## Stubbing is not verifying

Every essential `coEvery` stub must have a matching `coVerify`. In failure tests, verify the call was NOT made:

```kotlin
coVerify(exactly = 0) { accountRepository.setNotificationAlertShownForAccount(any(), any()) }
```

> Exception: Stubs in `@Before` for construction-time flows don't need per-test verification unless the test asserts flow behavior.

## Typed matchers for sealed classes

```kotlin
// Verify subtype
coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }

// Verify subtype + properties
coVerify {
    appNavigationService.emitAuthEvent(
        match<AuthState.AccountSwitched> { it.account == fakeAccount2 && it.showToast }
    )
}
```

Use `withArg` for assertion-only verification, `slot` + `capture` when you need to invoke captured lambdas:

```kotlin
// withArg — assertion only
coVerify {
    dialogQueueService.enqueue(withArg<DialogModel.Confirm> { dialog ->
        assertThat(dialog.title).isEqualTo("Goal Complete!")
    })
}

// slot — invoke captured callbacks
val dialogSlot = slot<DialogModel>()
every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
// ... act ...
(dialogSlot.captured as DialogModel.Confirm).onConfirm?.invoke()
```

## Flow testing with Turbine

```kotlin
// Finite flow (flowOf) — awaitComplete()
service.observeAccount(fakeAccount.id).test {
    assertThat(awaitItem()).isEqualTo(fakeAccount)
    awaitComplete()
}

// Infinite flow (Room/StateFlow) — cancelAndIgnoreRemainingEvents()
service.observeAccount(fakeAccount.id).test {
    assertThat(awaitItem()).isEqualTo(fakeAccount)
    cancelAndIgnoreRemainingEvents()
}

// Error flow — awaitError()
flow<Account> { throw IOException("network") }.test {
    val error = awaitError()
    assertThat(error).isInstanceOf(IOException::class.java)
}

// Multi-flow — turbineScope (preferred)
turbineScope {
    val accountTurbine = service.activeAccountFlow.testIn(backgroundScope, name = "account")
    val themeTurbine = service.currentThemeModeFlow.testIn(backgroundScope, name = "theme")
    assertThat(accountTurbine.awaitItem()).isEqualTo(fakeAccount)
    assertThat(themeTurbine.awaitItem()).isEqualTo(ThemeMode.SYSTEM)
    accountTurbine.cancelAndIgnoreRemainingEvents()
    themeTurbine.cancelAndIgnoreRemainingEvents()
}
```

> Turbine timeout is 3 seconds. If tests hang, the flow isn't emitting — check stubs.

## Virtual time control — NEVER use Thread.sleep()

```kotlin
// WRONG — non-deterministic, slow
Thread.sleep(300)

// CORRECT — instant, deterministic (requires ioDispatcher injection)
advanceUntilIdle()
```

| Method | Use when |
|---|---|
| `advanceUntilIdle()` | Default choice — drains ALL pending coroutines |
| `advanceTimeBy(ms)` | Testing `delay()`-based logic (debounce, throttle) |
| `runCurrent()` | Fine-grained control — rarely needed |

> `advanceUntilIdle()` only controls coroutines on the test dispatcher. Services with `CoroutineScope(Dispatchers.IO)` MUST have `ioDispatcher` injected.

## Dispatcher injection pattern

Services with hardcoded `Dispatchers.IO` need injection for testability:

```kotlin
// Production code
class FooService @Inject constructor(
    private val fooRepository: IFooRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
    appNavigationService: IAppNavigationService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService) {
    private var scope = CoroutineScope(SupervisorJob() + ioDispatcher)
}

// Test code
private fun createService() = FooService(
    fooRepository, connectivityObserver, dialogQueueService, appNavigationService,
    ioDispatcher = mainDispatcherRule.dispatcher,
)
```

> **Hilt note**: If adding `ioDispatcher` causes Hilt errors, check if any class injects the concrete service type instead of the interface. Fix to use the interface.

## runTest best practices

- Uses `StandardTestDispatcher` by default — new coroutines are queued, not eagerly executed. Call `advanceUntilIdle()` to run them.
- One `runTest` per test method via expression body: `fun myTest() = runTest { }`
- Automatically skips `delay()` calls (virtual time). Default timeout: 60 seconds.
- Our `MainDispatcherRule` uses `UnconfinedTestDispatcher` for simpler tests (eager execution).
