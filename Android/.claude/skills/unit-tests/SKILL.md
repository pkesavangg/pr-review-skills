---
name: unit-tests
description: Generates comprehensive MockK unit tests for Android service and repository classes following the MeApp testing pattern. Use when given a source file path and asked to write unit tests for it.
---

<objective>
Read an Android service or repository class and generate a complete, passing unit test file following MeApp conventions:
- JUnit 4 — NO JUnit 5 (`@Before`/`@After`, never `@BeforeEach`/`@AfterEach`)
- MockK for all mocking (no Mockito)
- Truth for assertions (`assertThat`)
- `runTest` for suspend functions
- Turbine for Flow testing (`.test { }`)
- MainDispatcherRule for coroutine dispatcher control
- `@OptIn(ExperimentalCoroutinesApi::class)` on the test class declaration
- `assertThrows(X::class.java) { runBlocking { ... } }` for exception tests — never manual try/catch
- Inline test fixtures as `private val` members — no factory classes
- Tests grouped by method with clear `// -----` section comments
- Shared stub helpers for DRY — extract repeated `every`/`coEvery` blocks
- Shared `httpException(code)` helper for HTTP error tests
- Target: 95%+ JaCoCo line coverage AND 95%+ branch coverage
- Cover ALL public methods: happy path, error paths, edge cases, network guards, gating conditions
</objective>

<quick_start>
1. Ask the user for the source file path if not provided in the arguments.
2. Read the source file + its interface.
3. For services: read `BaseService.kt` to understand inherited methods (`isNetworkAvailable`, `requireNetworkAvailable`, `showNetworkErrorAndThrow`, `showNetworkError`, `showSuccessToast`, `showErrorToast`, `checkInternetError`).
4. Read constructor dependency interfaces to know what methods to stub.
5. Identify ALL public methods, their dependencies, branches, catch blocks, and flows.
6. Cross-check method coverage completeness (Step 4) — every public method must have tests.
7. Verify ALL imports are present (Step 5) — especially exception types and domain models.
8. Generate the test file into `app/src/test/java/...` mirroring the source package.
9. Run tests → fix failures → re-run (iterative loop until green).
10. Run JaCoCo coverage → check method-level LINE + BRANCH → add missing tests → re-run.
</quick_start>

<process>

## Step 1: Read the source files

Read these files in parallel:
- The source file the user provided
- The interface it implements (e.g., `IAccountService` for `AccountService`)
- For services: `app/src/main/java/com/dmdbrands/gurus/weight/core/service/BaseService.kt`
- The primary repository/service interface(s) it depends on (constructor params)
- Any model classes used as parameters or return types

Key things to note while reading:
- **Constructor parameters** → these become your mocks
- **Which methods call `requireNetworkAvailable()`** → must have an offline test verifying the repo is never called
- **Which methods call `isNetworkAvailable()`** → test both online and offline paths
- **Which methods are `suspend`** → use `runTest`
- **Which methods return `Flow<T>`** → use Turbine `.test { }` block
- **Which methods enqueue dialogs** → test callbacks using `slot<DialogModel>()` + `capture()`
- **Exception `catch` blocks** → write one test per catch block
- **`when (e.code())` inside catch** → one test per HTTP code + one for `else`
- **Boolean state fields** (`isExpired`, `isActive`, `isCompleted`) → test both `true` and `false`
- **Methods that iterate collections** → verify per-item side effects
- **Flows collected at construction time** (in `init {}` blocks) → stub in `@Before` BEFORE `createService()`
- **Methods that check `checkInternetError()`** → test with `httpException(0)` for no-internet case

## Step 2: Plan the test groups

For each public method, list:
- Happy path(s)
- Error paths (exception → returns null/false, shows toast, emits auth event)
- Network routing (online vs offline) if `requireNetworkAvailable()` or `isNetworkAvailable()` is called
- Gating conditions (account null, isExpired, empty string, max accounts reached, etc.)
- Side effects to verify (toast shown, auth event emitted, repository writes, navigation)
- **Dialog callbacks** — if the method enqueues a `DialogModel.Confirm`, plan tests for `onConfirm`, `onCancel`, `onDismiss`
- **Exception catch blocks** — one test per distinct catch block
- **`when (e.code())` branches** — one test per HTTP code + one for `else`
- **Iteration side effects** — per-item verification for methods that loop over collections
- **Boolean state variants** — both `true` and `false` for every boolean flag

Target: minimum 3–4 tests per non-trivial method; 2 per simple delegation method.

## Step 3: Write the test file

### Complete import block

Always include ALL of these. Add additional imports as needed for the specific service's types:

```kotlin
package com.dmdbrands.gurus.weight.core.service   // mirror source package

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
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
import retrofit2.Response
// + domain model imports for the specific service
```

> **CRITICAL**: Always import `kotlinx.coroutines.runBlocking` — needed for `assertThrows` with suspend functions. Always import `retrofit2.HttpException` and `retrofit2.Response` — needed for `httpException()` helper.

### Full file structure

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
    // ... other constructor dependencies as mocks

    private lateinit var service: {ServiceName}

    // --- Inline Test Fixtures ---
    // Construct real domain objects directly. Use `.copy()` for variants.
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
        // Stub any other flows the service collects in init {}
        service = createService()
    }

    @After
    fun tearDown() {
        // clearAllMocks() is cheaper than unmockkAll() — it resets stub/verify state
        // but reuses mock objects. unmockkAll() destroys and recreates them.
        // Both work in JUnit 4 (new class instance per test), but clearAllMocks() is preferred.
        clearAllMocks()
    }

    /**
     * Single place to construct the service under test.
     * Pass constructor args positionally to match source.
     * Tests that need different flow stubs: re-stub THEN call createService() again.
     */
    private fun createService() = {ServiceName}(
        accountRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
        // ... all constructor args in order
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates an HttpException with a mocked Response for the given HTTP status code.
     * Used in all HTTP error path tests.
     */
    private fun httpException(code: Int): HttpException {
        val response = mockk<Response<*>> {
            every { code() } returns code
            every { message() } returns "Mock HTTP error"
            every { errorBody() } returns null
        }
        return HttpException(response)
    }

    private fun stubNetworkAvailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = true, unAvailable = false)
    }

    private fun stubNetworkUnavailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = false, unAvailable = true)
    }

    // Add more shared stub helpers here as needed:
    // private fun stubLoginSuccess(account: Account = fakeAccount) { ... }
    // private fun stubLoginThrows(exception: Throwable) { ... }

    // -------------------------------------------------------------------------
    // {methodName}
    // -------------------------------------------------------------------------

    @Test
    fun `{method} does X when Y`() = runTest {
        // Arrange — stubs
        // Act — ONE function call on service
        // Assert — Truth assertions + coVerify
    }
}
```

### Mocking rules

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

> **`relaxed = true` vs `relaxUnitFun = true`**: Prefer `relaxUnitFun = true` when the dependency has mostly Unit-returning methods but also some non-Unit methods you want strict checking on. `relaxUnitFun = true` relaxes ONLY Unit functions — all others throw on unstubbed calls, catching more bugs. Use full `relaxed = true` only for fire-and-forget dependencies where ALL return values are irrelevant.

> **`every` vs `coEvery` — CRITICAL**: Always use `coEvery` for `suspend` functions and `every` for non-suspend functions. Using `every` on a suspend function silently fails to stub it, causing `no answer found` errors at runtime. Similarly, use `coVerify` for suspend calls and `verify` for non-suspend calls.

### Test fixture construction — complete constructors

When creating test fixtures, you MUST include ALL required constructor parameters.
Read the model's data class to know every field. Use `.copy()` for variants.

**Account** (37+ fields — most have defaults, but always specify key fields):
```kotlin
private val fakeAccount = Account(
    id = "acc-1",
    firstName = "John",
    lastName = "Doe",
    dob = "1990-01-01",
    email = "john@example.com",
    gender = "male",
    isActiveAccount = true,
    isLoggedIn = true,
    isExpired = false,
    zipcode = "12345",
    weightUnit = WeightUnit.LB,
    height = 1750,
    activityLevel = "normal",
)

// Variants use .copy() — only override what changes
private val fakeAccount2 = fakeAccount.copy(
    id = "acc-2",
    firstName = "Jane",
    email = "jane@example.com",
    isActiveAccount = false,
)
private val expiredAccount = fakeAccount.copy(isExpired = true)
private val nullAccount: Account? = null
```

> Always read the data class source before constructing fixtures — missing required fields cause compilation errors.

### AAA pattern — mandatory for every test

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

> Always use `service` (not `sut`) — consistent with the codebase convention.

### Stub helpers — DRY rule

Never repeat the same `every { }` / `coEvery { }` block in multiple tests.
Extract into private stub helpers:

```kotlin
private fun stubLoginSuccess(account: Account = fakeAccount) {
    coEvery { accountRepository.login(any(), any()) } returns account
}

private fun stubLoginThrows(exception: Throwable) {
    coEvery { accountRepository.login(any(), any()) } throws exception
}
```

> Stub helpers only share **Arrange** code — never Act or Assert steps.

### assertThrows — exception tests (CRITICAL: runBlocking wrapper)

`assertThrows` does NOT support suspend lambdas. You MUST wrap the call in `runBlocking`:

```kotlin
// ❌ WRONG — won't compile or won't catch the exception
assertThrows(MaxAccountsReachedException::class.java) {
    service.login("new@example.com", "password")  // suspend fun!
}

// ✅ CORRECT — runBlocking bridges the suspend call
assertThrows(MaxAccountsReachedException::class.java) {
    runBlocking { service.login("new@example.com", "password") }
}
```

> **When to use `runBlocking` inside `assertThrows`**: ALWAYS when the method under test is `suspend`. The outer `runTest` handles the test scope, but `assertThrows` needs a synchronous lambda.

### Stubbing is not verifying

Every `coEvery` stub that is essential to the test's assertion must have a matching `coVerify`:

```kotlin
// ✅ Stub AND verify
coEvery { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) } just Runs
// ... act ...
coVerify(exactly = 1) { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
```

In failure tests, verify the call was NOT made:
```kotlin
coVerify(exactly = 0) { accountRepository.setNotificationAlertShownForAccount(any(), any()) }
```

> **Exception**: Stubs set up in `@Before` for construction-time flows (e.g., `getActiveAccount()`) don't need per-test verification unless the test specifically asserts flow behavior.

### Typed matchers — `any<Type>()` and `match<Type> { }`

For sealed class parameters (like `AuthState`), use typed matchers to verify the correct subtype:

```kotlin
// ✅ Verify a specific sealed class subtype was emitted
coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
coVerify { appNavigationService.emitAuthEvent(any<AuthState.AccountAdded>()) }

// ✅ Verify specific properties inside the sealed class — use match<>
coVerify {
    appNavigationService.emitAuthEvent(
        match<AuthState.AccountSwitched> { it.account == fakeAccount2 && it.showToast }
    )
}

coVerify {
    appNavigationService.emitAuthEvent(
        match<AuthState.LoggedOut> { it.isActiveAccount && it.isLastAccount }
    )
}
```

> Use `any<Type>()` when you only care about the subtype. Use `match<Type> { predicate }` when you need to verify specific field values inside the sealed class.

### Network guard clause pattern

Every method with `requireNetworkAvailable()` must have an offline test:

```kotlin
@Test
fun `deleteAccount throws when offline`() = runTest {
    stubNetworkUnavailable()

    assertThrows(Exception::class.java) {
        runBlocking { service.deleteAccount(fakeAccount.id, isActiveAccount = true) }
    }
    coVerify(exactly = 0) { accountRepository.deleteAccount(any(), any()) }
}
```

Methods that use `isNetworkAvailable()` (soft check, no throw) — test both paths:

```kotlin
@Test
fun `refreshAccount skips API call when offline`() = runTest {
    stubNetworkUnavailable()

    service.refreshAccount()

    coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
}
```

### Gating condition pattern

Test methods that exit early based on state:

```kotlin
// Gate: account null — re-stub flow, re-create service
@Test
fun `changePassword returns false when no active account`() = runTest {
    every { accountRepository.getActiveAccount() } returns flowOf(null)
    service = createService()

    val result = service.changePassword("old", "new")

    assertThat(result).isFalse()
    coVerify(exactly = 0) { accountRepository.updatePassword(any(), any(), any()) }
}

// Gate: empty/null string parameter
@Test
fun `handleUnauthorizedLogout returns null when accountId is empty`() = runTest {
    val result = service.handleUnauthorizedLogout("")

    assertThat(result).isNull()
}
```

> **Important**: When changing a flow stub that the service collects at init, you MUST call `service = createService()` after re-stubbing.

### HttpException error path pattern

Test all HTTP error codes the service handles in catch blocks:

```kotlin
@Test
fun `login returns null on HttpException 401`() = runTest {
    coEvery { accountRepository.login(any(), any()) } throws httpException(401)

    val result = service.login(fakeAccount.email, "wrong")

    assertThat(result).isNull()
    coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
}

@Test
fun `login returns null on HttpException 0 (no internet)`() = runTest {
    coEvery { accountRepository.login(any(), any()) } throws httpException(0)

    val result = service.login(fakeAccount.email, "password")

    assertThat(result).isNull()
}
```

### Flow tests — Turbine

```kotlin
// Finite flow (flowOf) — use awaitComplete()
service.observeAccount(fakeAccount.id).test {
    assertThat(awaitItem()).isEqualTo(fakeAccount)
    awaitComplete()
}

// Infinite flow (Room / StateFlow) — use cancelAndIgnoreRemainingEvents()
service.observeAccount(fakeAccount.id).test {
    assertThat(awaitItem()).isEqualTo(fakeAccount)
    cancelAndIgnoreRemainingEvents()
}

// Error flow — use awaitError() instead of assertThrows
flow<Account> { throw IOException("network") }.test {
    val error = awaitError()
    assertThat(error).isInstanceOf(IOException::class.java)
    assertThat(error.message).isEqualTo("network")
}

// StateFlow with rapid emissions — use expectMostRecentItem() to skip intermediates
service.stateFlow.test {
    // skip initial value
    skipItems(1)
    service.triggerMultipleUpdates()
    // grab only the final emission, discard intermediates
    val latest = expectMostRecentItem()
    assertThat(latest.isComplete).isTrue()
}
```

**Multi-flow testing — use `testIn(backgroundScope)`**:
When testing multiple flows simultaneously, `test {}` blocks cannot be nested. Use `testIn`:

```kotlin
@Test
fun `active account and theme flows emit correct values`() = runTest {
    val accountTurbine = service.activeAccountFlow.testIn(backgroundScope)
    val themeTurbine = service.currentThemeModeFlow.testIn(backgroundScope)

    assertThat(accountTurbine.awaitItem()).isEqualTo(fakeAccount)
    assertThat(themeTurbine.awaitItem()).isEqualTo(ThemeMode.SYSTEM)

    accountTurbine.cancelAndIgnoreRemainingEvents()
    themeTurbine.cancelAndIgnoreRemainingEvents()
}
```

> **Turbine timeout**: Default is 3 seconds (wall clock, ignores virtual time). If tests hang, the flow isn't emitting — check your stubs, not the timeout.

### Dialog callback pattern

```kotlin
/** Capture the enqueued dialog for callback testing. */
private fun captureDialog(): DialogModel {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    return dialogSlot.captured
}

@Test
fun `given dialog shown, when onConfirm invoked, then deletes account`() = runTest {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    // Arrange to trigger dialog...
    service.showDeleteConfirmation(fakeAccount.id)

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify(exactly = 1) { accountRepository.deleteAccount(any()) }
}
```

> **Why `advanceUntilIdle()`?** Callbacks may launch new coroutines. `advanceUntilIdle()` drains pending coroutines before verify.

### Virtual time control — NEVER use Thread.sleep()

`Thread.sleep()` makes tests slow and flaky. Always use virtual time from `runTest`:

```kotlin
// ❌ WRONG — non-deterministic, slow, CI-flaky
service.subscribeAccount()
Thread.sleep(300)
assertThat(service.activeAccount.value).isEqualTo(fakeAccount)

// ✅ CORRECT — instant, deterministic
service.subscribeAccount()
advanceUntilIdle()   // drains ALL pending coroutines
assertThat(service.activeAccount.value).isEqualTo(fakeAccount)
```

For methods with `delay()`, use `advanceTimeBy()` for precise control:

```kotlin
@Test
fun `debounced search waits 500ms before calling API`() = runTest {
    service.onSearchTextChanged("query")

    advanceTimeBy(499)
    coVerify(exactly = 0) { repository.search(any()) }  // not yet

    advanceTimeBy(1)
    coVerify(exactly = 1) { repository.search("query") }  // now fires
}
```

> `advanceUntilIdle()` is the default choice. Use `advanceTimeBy()` only when you need to assert timing behavior.

### Iteration side effects — per-item verification

```kotlin
@Test
fun `logoutAll resets notification for every account`() = runTest {
    every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, fakeAccount2))
    service = createService()
    coEvery { accountRepository.logoutAllAccounts() } returns true
    coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

    service.logoutAll()

    coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
    coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) }
}
```

### Max accounts / collection edge cases

```kotlin
@Test
fun `login throws MaxAccountsReachedException when at max and email is new`() = runTest {
    every { accountRepository.getLoggedInAccounts() } returns flowOf(
        (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) }
    )
    service = createService()

    assertThrows(MaxAccountsReachedException::class.java) {
        runBlocking { service.login("brandnew@example.com", "password") }
    }
}
```

## Step 4: Verify method coverage completeness

Before writing the file, cross-check that EVERY public method in the interface has tests.

**Systematic checklist** (do this for every service/repository):
1. List ALL public methods from the interface (e.g., `IAccountService`)
2. List ALL public properties (Flow properties, StateFlow properties)
3. For each method, verify you have at minimum:
   - 1 happy-path test
   - 1 error-path test (if method has a catch block)
   - 1 offline test (if method calls `requireNetworkAvailable` or `isNetworkAvailable`)
   - 1 null/empty gate test (if method checks for null account or empty input)
4. For Flow properties: 1 emission test using Turbine
5. Mark any methods you intentionally skip (trivial getters, delegations) with a comment

**Example checklist output**:
```
✅ login — 6 tests (happy, max-accounts, 401, 500, 0, non-http)
✅ signup — 5 tests (happy, max-accounts, 401, 400, generic)
✅ resetPassword — 4 tests (success, failure-response, offline, 500)
✅ changePassword — 4 tests (success, null-account, 401, 500)
✅ activeAccountFlow — 1 test (emission)
⚠️ subscribeAccount — 2 tests (need advanceUntilIdle, not Thread.sleep)
```

## Step 5: Verify imports before writing

After generating the test file content, scan for ALL types used and ensure imports exist:

1. **Domain models**: `Account`, `AccountInfo`, `SignupRequest`, `ProfileUpdateRequest`, `ChangePasswordResponse`, `WeightUnit`, `DashboardType`, `ThemeMode`, `AuthState`, etc.
2. **Service interfaces**: `IDialogQueueService`, `IAppNavigationService`, `IOfflineHandlerService`, `StorageClearService`, etc.
3. **Repository interfaces**: `IAccountRepository`, etc.
4. **Exception types**: `HttpException`, `IOException`, `UnknownHostException`, `InterruptedIOException`, `SocketTimeoutException`, `MaxAccountsReachedException`
5. **MockK**: `Runs`, `clearAllMocks`, `coEvery`, `coVerify`, `every`, `just`, `mockk`, `slot`, `verify`
6. **Coroutines**: `ExperimentalCoroutinesApi`, `flow`, `flowOf`, `runBlocking`, `advanceUntilIdle`, `runTest`
7. **Testing**: `Truth.assertThat`, `assertThrows`, `Before`, `After`, `Rule`, `Test`
8. **Retrofit**: `HttpException`, `Response`
9. **Turbine**: `test` (from `app.cash.turbine`)

> **Common miss**: `java.io.IOException`, `java.net.UnknownHostException`, `java.io.InterruptedIOException`, `java.net.SocketTimeoutException` — these need explicit imports when testing exception catch blocks.

## Step 6: Place the file

Mirror the source package path:
```
Source:  app/src/main/java/com/dmdbrands/gurus/weight/core/service/FooService.kt
Test:    app/src/test/java/com/dmdbrands/gurus/weight/core/service/FooServiceTest.kt

Source:  app/src/main/java/com/dmdbrands/gurus/weight/data/repository/FooRepository.kt
Test:    app/src/test/java/com/dmdbrands/gurus/weight/data/repository/FooRepositoryTest.kt
```

## Step 7: Iterative test-fix loop

Run tests and fix until green. This is an iterative process — do NOT stop at the first run.

```bash
# Step 7a: Run tests
cd Android && ./gradlew :app:testDebugUnitTest --tests "*.{ClassName}Test"
```

**If tests fail:**
1. Read the error output carefully
2. Common fixes:
   - `no answer found for ...` → add a missing stub (`coEvery`/`every`)
   - `Unresolved reference` → add missing import
   - `Type mismatch` → fix the mock return type
   - `UncompletedCoroutinesError` → add `cancelAndIgnoreRemainingEvents()` in Turbine
   - `assertThrows` not catching → add `runBlocking` wrapper for suspend functions
3. Fix the test file
4. Re-run. Repeat until `BUILD SUCCESSFUL`.

```bash
# Step 7b: Generate JaCoCo coverage report
cd Android && ./gradlew :app:jacocoTestReport
```

## Step 8: Verify coverage — method-level LINE + BRANCH

> **Why method-level?** Class-level coverage can show 85%+ while missing entire methods or branches. Always check per-method.

```bash
# Check BOTH line AND branch coverage per METHOD for the target class
cd Android && python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml')
for cls in tree.getroot().iter('class'):
    name = cls.get('name', '')
    if '{ClassName}' in name and '\$' not in name:
        print(f'=== {name} ===')
        # Class-level summary
        for c in cls.findall('counter'):
            t = c.get('type')
            missed, covered = int(c.get('missed')), int(c.get('covered'))
            total = missed + covered
            pct = round(covered / total * 100) if total > 0 else 0
            if t in ('LINE', 'BRANCH', 'METHOD'):
                flag = ' *** BELOW 95% ***' if pct < 95 else ''
                print(f'  {t}: {covered}/{total} = {pct}%{flag}')
        # Per-method detail (shows which methods lack coverage)
        print('  --- Per method ---')
        for method in cls.findall('method'):
            mname = method.get('name')
            if mname in ('<init>', '<clinit>'): continue
            for c in method.findall('counter'):
                t = c.get('type')
                if t == 'LINE':
                    missed = int(c.get('missed'))
                    if missed > 0:
                        covered = int(c.get('covered'))
                        print(f'    {mname}: {missed} lines MISSED (covered {covered})')
                if t == 'BRANCH':
                    missed = int(c.get('missed'))
                    if missed > 0:
                        covered = int(c.get('covered'))
                        print(f'    {mname}: {missed} branches MISSED (covered {covered})')
"
```

**If coverage is below 95%:**
1. The script above shows EXACTLY which methods and branches are missed
2. For missed lines in catch blocks → add exception tests targeting that specific catch
3. For missed branches → check `if`/`when`/`else` paths — write tests for the untested path
4. For missed dialog callbacks → add `slot<DialogModel>()` + `capture()` tests
5. For missed iteration code → add tests with multi-item lists
6. **Do NOT write artificial tests for unreachable dead code** — delete the dead code instead
7. After adding tests, re-run Step 7a + 7b + 8 until both LINE and BRANCH are 95%+

> **JaCoCo gotcha**: If coverage still shows 0% or stale data, run:
> ```bash
> cd Android && ./gradlew cleanTestDebugUnitTest :app:testDebugUnitTest --tests "*.{ClassName}Test" :app:jacocoTestReport
> ```

### JaCoCo version — pin to 0.8.14+ for accurate Kotlin coverage

The project may use an older JaCoCo version bundled with Gradle/AGP. Older versions report **false-positive branch misses** for Kotlin constructs. If you see phantom branch misses, recommend adding to `app/build.gradle.kts`:

```kotlin
jacoco {
    toolVersion = "0.8.14"  // or latest — fixes Kotlin coroutine/elvis/safe-call false positives
}
```

**JaCoCo 0.8.14 fixes** (Kotlin-specific):
- Coroutine state machine branches filtered out (no longer counted as missed)
- Elvis operator following safe call operator filtered
- Chained safe call operators filtered
- Suspend lambdas with parameters filtered
- Inline value class return branches filtered
- Default arguments > 32 parameters filtered

### Known JaCoCo/Kotlin false positives — do NOT write tests for these

Some "missed branches" are compiler-generated bytecode artifacts, NOT real untested paths:

| False positive | Explanation | Action |
|---|---|---|
| `when` exhaustiveness check | Kotlin compiler adds an unreachable `else` branch for sealed `when` | Ignore — cannot be tested |
| Coroutine state machine | Suspend point creates synthetic branches for continuation resume | Ignore — JaCoCo 0.8.14 filters these |
| Elvis + safe call chain | `foo?.bar ?: default` generates extra null-check branches | Ignore — JaCoCo 0.8.14 filters these |
| `flow { emit() }` branches | Flow builder's suspension points create synthetic branches | Ignore — JaCoCo 0.8.14 filters these |
| Data class `copy()`/`toString()` | Compiler-generated functions with many branches | Ignore if > 32 params, else test normally |

> **Rule**: If the method-level script shows 1-2 missed branches in a method you've fully tested (all logical paths covered), it's likely a Kotlin compiler artifact. Do NOT write artificial tests — verify with the HTML report that the missed line is in compiler-generated code.

## Step 9: Repository test pattern (when testing a repository)

Repository tests differ from service tests:
- No `BaseService` inheritance, no `connectivityObserver`/`dialogQueueService`/`appNavigationService`
- Mock the Retrofit API interface and Room DAOs instead
- Use `MockWebServer` for integration-level API tests (optional, advanced)
- Focus on: API call mapping, response parsing, DAO interactions, error transformation

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class {RepositoryName}Test {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mock the API and DAO
    private val api: {ApiInterface} = mockk()
    private val dao: {DaoInterface} = mockk(relaxed = true)

    private lateinit var repository: {RepositoryName}

    @Before
    fun setUp() {
        repository = {RepositoryName}(api, dao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getAccount returns mapped entity from API`() = runTest {
        val apiResponse = // ...
        coEvery { api.getAccount(any()) } returns apiResponse

        val result = repository.getAccount("acc-1")

        assertThat(result.id).isEqualTo("acc-1")
        coVerify { api.getAccount("acc-1") }
    }

    @Test
    fun `getAccount propagates HttpException from API`() = runTest {
        coEvery { api.getAccount(any()) } throws HttpException(
            mockk<Response<*>> {
                every { code() } returns 404
                every { message() } returns "Not Found"
                every { errorBody() } returns null
            }
        )

        assertThrows(HttpException::class.java) {
            runBlocking { repository.getAccount("acc-1") }
        }
    }
}
```

## Troubleshooting

| Error | Fix |
|---|---|
| `no answer found for: {Mock}.method()` | Add stub: `coEvery { mock.method() } returns value` or use `mockk(relaxed = true)` |
| `UncompletedCoroutinesError` | Add `cancelAndIgnoreRemainingEvents()` in Turbine `.test { }` block |
| `advanceUntilIdle opt-in` | Add `@OptIn(ExperimentalCoroutinesApi::class)` on the class |
| Coverage still 0% | Run `./gradlew cleanTestDebugUnitTest :app:jacocoTestReport` |
| `Unresolved reference: Runs` | Import `io.mockk.Runs` AND `io.mockk.just` (both needed) |
| `Unresolved reference: httpException` | Add the `httpException(code)` helper method to the test class |
| `assertThrows` not catching suspend exception | Wrap in `runBlocking`: `assertThrows(X::class.java) { runBlocking { ... } }` |
| `Unresolved reference: HttpException` | Import `retrofit2.HttpException` |
| `Unresolved reference: Response` | Import `retrofit2.Response` |
| `Unresolved reference: runBlocking` | Import `kotlinx.coroutines.runBlocking` |
| `Unresolved reference: flow` | Import `kotlinx.coroutines.flow.flow` |
| `Type mismatch: expected X got Unit` | Check if the mock returns the right type; suspend funs need `coEvery` not `every` |
| Init block flow not collected | Stub the flow in `@Before` BEFORE calling `createService()` |
| Test passes but coverage doesn't increase | Ensure JaCoCo XML is being regenerated: run `jacocoTestReport` after `testDebugUnitTest` |
| `Thread.sleep` in tests | Replace with `advanceUntilIdle()` — never use real sleeps in unit tests |
| Turbine test hangs/times out | Flow not emitting — check stubs. Add `cancelAndIgnoreRemainingEvents()` for infinite flows |
| Phantom "missed branch" in fully-tested method | Likely Kotlin compiler artifact — upgrade JaCoCo to 0.8.14+ (see false positives section) |
| `every` used for suspend function — silently fails | Use `coEvery` for suspend functions, `every` for non-suspend only |
| Mock returns default instead of stubbed value | Verify `relaxed = true` isn't hiding a missing stub — prefer strict mocks or `relaxUnitFun = true` |

</process>

<success_criteria>
unit-tests skill is complete when:
- [ ] Test file compiles with no errors
- [ ] `./gradlew :app:testDebugUnitTest --tests "*.{ClassName}Test"` reports BUILD SUCCESSFUL with 0 failures
- [ ] `@OptIn(ExperimentalCoroutinesApi::class)` on the test class declaration
- [ ] `@Before` / `@After` used — never `@BeforeEach` / `@AfterEach`
- [ ] `clearAllMocks()` in `@After` (preferred over `unmockkAll()`)
- [ ] All public methods have at least one test
- [ ] Suspend functions use `runTest`
- [ ] `assertThrows` with suspend functions uses `runBlocking` wrapper
- [ ] Flow methods use Turbine `.test { }` block
- [ ] Network routing (online/offline) tested for every method with `requireNetworkAvailable()` or `isNetworkAvailable()`
- [ ] Offline tests verify repository/API never called with `coVerify(exactly = 0)`
- [ ] Every distinct `catch` block triggered by its own test
- [ ] Every `when (e.code())` branch has one test per code + one for `else`
- [ ] Dialog callbacks tested via `slot<DialogModel>()` + `capture()` where applicable
- [ ] Shared `httpException(code)` helper used for all HTTP error tests
- [ ] Shared stub helpers extract repeated `coEvery`/`every` blocks
- [ ] Inline test fixtures (no FakeFactory class) — `private val` members with `.copy()` for variants
- [ ] `service` naming used consistently (never `sut`)
- [ ] Gating conditions tested (account null, empty string, isExpired, max accounts, etc.)
- [ ] Typed matchers used for sealed class verification (`any<AuthState.Error>()`, `match<Type> { }`)
- [ ] Method coverage checklist completed — every public method in the interface has tests
- [ ] No Mockito imports — MockK only
- [ ] All required imports present (retrofit2, runBlocking, flow, mockk, clearAllMocks, truth, java.io/net exceptions, domain models)
- [ ] No `Thread.sleep()` — use `advanceUntilIdle()` or `advanceTimeBy()` instead
- [ ] `coEvery`/`coVerify` used for suspend functions, `every`/`verify` for non-suspend
- [ ] Turbine `awaitError()` used for error flow tests (not `assertThrows`)
- [ ] Multi-flow tests use `testIn(backgroundScope)` not nested `.test {}` blocks
- [ ] JaCoCo per-method LINE coverage 95%+ AND per-method BRANCH coverage 95%+
- [ ] Coverage verified using method-level JaCoCo script (not just class-level)
- [ ] Phantom branch misses from Kotlin compiler artifacts acknowledged, not force-tested
</success_criteria>
