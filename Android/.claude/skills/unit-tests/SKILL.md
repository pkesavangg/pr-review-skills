---
name: unit-tests
description: Generates comprehensive MockK unit tests for Android service, repository, ViewModel, and Reducer classes following the MeApp testing pattern. Use when given a source file path and asked to write unit tests for it.
---

<objective>
Read an Android service, repository, ViewModel, or Reducer class and generate a complete, passing unit test file following project conventions:
- **JUnit version**: detect from `build.gradle.kts` / `libs.versions.toml` (see Step 0 below)
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
3. **For services**: read `BaseService.kt` to understand inherited methods (`isNetworkAvailable`, `requireNetworkAvailable`, `showNetworkErrorAndThrow`, `showNetworkError`, `showSuccessToast`, `showErrorToast`, `checkInternetError`).
   **For repositories**: skip `BaseService.kt`. Instead read DAO interfaces, API interfaces, DataStore, TokenManager, and entity/mapper classes.
4. Read constructor dependency interfaces to know what methods to stub.
5. Identify ALL public methods, their dependencies, branches, catch blocks, and flows.
6. Cross-check method coverage completeness (Step 4) — every public method must have tests.
7. Verify ALL imports are present (Step 5) — especially exception types and domain models.
8. Generate the test file into `app/src/test/java/...` mirroring the source package.
9. Run tests → fix failures → re-run (iterative loop until green).
10. Run JaCoCo coverage → check method-level LINE + BRANCH → add missing tests → re-run.

> **Detect class type and load patterns**: Check the source file:
> - Files in `core/service/` or `domain/services/` → **Service** — Read `.claude/skills/unit-tests/patterns/service.md`
> - Files in `data/repository/` → **Repository** — Read `.claude/skills/unit-tests/patterns/repository.md`
> - Files named `*Reducer.kt` implementing `IReducer` → **Reducer** — Read `.claude/skills/unit-tests/patterns/reducer.md`
> - Files named `*ViewModel.kt` extending `BaseIntentViewModel` → **ViewModel** — Read `.claude/skills/unit-tests/patterns/reducer.md` AND `.claude/skills/unit-tests/patterns/viewmodel.md`
> - For ViewModels: generate **ReducerTest first** (simpler), then **ViewModelTest** (side effects)
>
> **On-demand reference files** (Read only when needed):
> - At Step 8 (coverage verification): Read `.claude/skills/unit-tests/reference/jacoco-coverage.md`
> - If tests fail with unfamiliar errors: Read `.claude/skills/unit-tests/reference/troubleshooting.md`
> - For advanced testing patterns (dispatchers, flow testing, anti-patterns): Read `.claude/skills/unit-tests/reference/android-testing-practices.md`
</quick_start>

<process>

## Step 0: Detect testing framework and libraries

Before writing any test, read `gradle/libs.versions.toml` and `app/build.gradle.kts` to detect the project's testing stack. This ensures the generated tests match the actual project dependencies.

**Check for these in `libs.versions.toml` or `build.gradle.kts`:**

```
grep -i "junit" gradle/libs.versions.toml app/build.gradle.kts
grep -i "mockk\|mockito\|turbine\|truth\|coroutines-test" gradle/libs.versions.toml
```

### JUnit 4 vs JUnit 5 compatibility

| Aspect | JUnit 4 (MeApp current) | JUnit 5 |
|---|---|---|
| **Setup/teardown** | `@Before` / `@After` | `@BeforeEach` / `@AfterEach` |
| **Test annotation** | `@org.junit.Test` | `@org.junit.jupiter.api.Test` |
| **Assertions** | `org.junit.Assert.assertThrows` | `org.junit.jupiter.api.assertThrows` (Kotlin ext) |
| **Rules** | `@get:Rule val rule = MainDispatcherRule()` | `@RegisterExtension val ext = MainDispatcherExtension()` |
| **Test class lifecycle** | New instance per test (default) | New instance per test (default, configurable) |
| **Exception testing** | `assertThrows(X::class.java) { runBlocking { } }` | `assertThrows<X> { runBlocking { } }` (reified) |
| **Parameterized** | `@RunWith(Parameterized::class)` | `@ParameterizedTest` + `@ValueSource` / `@CsvSource` |

### Mocking library detection

| Library | Detect via | Import prefix |
|---|---|---|
| **MockK** | `mockk` in versions.toml | `io.mockk.*` |
| **Mockito** | `mockito` in versions.toml | `org.mockito.*` / `org.mockito.kotlin.*` |

### Assertion library detection

| Library | Detect via | Usage |
|---|---|---|
| **Truth** | `truth` in versions.toml | `assertThat(x).isEqualTo(y)` |
| **AssertJ** | `assertj` in versions.toml | `assertThat(x).isEqualTo(y)` (similar API, different import) |
| **JUnit assertions** | Always available | `assertEquals(expected, actual)` |

> **Rule**: Always match the project's existing test dependencies. Read `libs.versions.toml` first. If an existing test file exists in the same package, read it to follow the same import/pattern conventions.

### MeApp current stack (as detected)

| Library | Version | Config |
|---|---|---|
| JUnit | 4.13.2 | `testImplementation` |
| MockK | 1.14.9 | `testImplementation` |
| Truth | 1.4.5 | `testImplementation` |
| Turbine | 1.2.1 | `testImplementation` |
| coroutines-test | 1.10.2 | `testImplementation` |

> If the project migrates to JUnit 5, update all `@Before`→`@BeforeEach`, `@After`→`@AfterEach`, `@Rule`→`@RegisterExtension`, and `org.junit.Test`→`org.junit.jupiter.api.Test` throughout this skill.

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

> **IMPORTANT**: Before writing, Read the class-type-specific pattern file identified in `<quick_start>`. The pattern file contains essential patterns, code templates, and mocking rules for the detected class type.

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

> **`coJustRun` — idiomatic Unit-returning suspend stubs**: Instead of `coEvery { mock.method() } just Runs`, prefer the cleaner `coJustRun { mock.method() }`. Both are equivalent; `coJustRun` is more concise and readable.

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

> Always use `service` or `repository` (not `sut`) — consistent with the codebase convention.

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

// returnsMany — different values on successive calls
coEvery { api.fetchGoal(any()) } returnsMany listOf(goal1, goal2, goal3)
// First call returns goal1, second returns goal2, third returns goal3
```

> Stub helpers only share **Arrange** code — never Act or Assert steps.

> **Stubbing order**: When multiple stubs match, **the last-defined stub wins**. Put general catch-all stubs first, specific overrides after:
> ```kotlin
> every { calc.add(any(), any()) } returns 0    // general
> every { calc.add(1, 1) } returns 2            // specific override — must come AFTER
> ```

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

**`withArg` — inline argument assertions (alternative to `slot` + `capture`)**:

When you only need to assert argument values (not re-invoke a callback), `withArg` is simpler than `slot`:

```kotlin
// ✅ withArg — inline assertions, no slot variable needed
coVerify {
    dialogQueueService.enqueue(withArg<DialogModel.Confirm> { dialog ->
        assertThat(dialog.title).isEqualTo("Goal Complete!")
        assertThat(dialog.confirmText).isEqualTo("OK")
    })
}

// ✅ slot — needed when you must invoke captured lambdas (e.g., dialog callbacks)
val dialogSlot = slot<DialogModel>()
every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
// ... act ...
(dialogSlot.captured as DialogModel.Confirm).onConfirm?.invoke()
```

> Use `withArg` for assertion-only verification. Use `slot` + `capture` when you need to invoke callbacks or store the captured value for later use.

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

// Assert nothing was emitted — non-suspending, checks buffer immediately
service.stateFlow.test {
    skipItems(1)  // consume initial
    expectNoEvents()  // ✅ asserts no further emissions buffered
    cancelAndIgnoreRemainingEvents()
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

**Multi-flow testing — use `turbineScope` (preferred) or `testIn(backgroundScope)`**:
When testing multiple flows simultaneously, `test {}` blocks cannot be nested. Use `turbineScope` (preferred, better error diagnostics) or `testIn`:

```kotlin
// ✅ PREFERRED — turbineScope manages lifecycle and provides named diagnostics
@Test
fun `active account and theme flows emit correct values`() = runTest {
    turbineScope {
        val accountTurbine = service.activeAccountFlow.testIn(backgroundScope, name = "account")
        val themeTurbine = service.currentThemeModeFlow.testIn(backgroundScope, name = "theme")

        assertThat(accountTurbine.awaitItem()).isEqualTo(fakeAccount)
        assertThat(themeTurbine.awaitItem()).isEqualTo(ThemeMode.SYSTEM)

        accountTurbine.cancelAndIgnoreRemainingEvents()
        themeTurbine.cancelAndIgnoreRemainingEvents()
    }
}

// ✅ ALSO VALID — testIn inside a .test {} block (which provides turbineScope implicitly)
@Test
fun `flows emit correct values`() = runTest {
    service.activeAccountFlow.test {
        val themeTurbine = service.currentThemeModeFlow.testIn(backgroundScope)

        assertThat(awaitItem()).isEqualTo(fakeAccount)  // from outer .test {}
        assertThat(themeTurbine.awaitItem()).isEqualTo(ThemeMode.SYSTEM)

        cancelAndIgnoreRemainingEvents()
        themeTurbine.cancelAndIgnoreRemainingEvents()
    }
}
```

> **Why `turbineScope`?** It validates that all turbines are properly terminated when the block exits. Named turbines (`name = "account"`) produce clearer error messages when tests fail. Requires Turbine 1.1.0+.

> **Turbine timeout**: Default is 3 seconds (wall clock, ignores virtual time). If tests hang, the flow isn't emitting — check your stubs, not the timeout.

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

### `runTest` best practices (per official docs)

- `runTest` uses `StandardTestDispatcher` by default — new coroutines are **queued**, not executed eagerly. You must call `advanceUntilIdle()` to run them.
- Use **one `runTest` per test method** via expression body: `fun myTest() = runTest { }`. Never call `runTest` twice in one test.
- `runTest` automatically skips `delay()` calls (virtual time). Default timeout is 60 seconds (`dispatchTimeoutMs`).
- Our `MainDispatcherRule` replaces `Dispatchers.Main` with `UnconfinedTestDispatcher` for simpler tests (eager execution, less boilerplate). This is a deliberate trade-off — `StandardTestDispatcher` is more production-like but requires explicit `advanceUntilIdle()` after every `launch`.

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
5. If errors are unfamiliar, Read `.claude/skills/unit-tests/reference/troubleshooting.md` for the full error→fix table.

```bash
# Step 7b: Generate JaCoCo coverage report
cd Android && ./gradlew :app:jacocoTestReport
```

## Step 8: Verify coverage — method-level LINE + BRANCH

> Read `.claude/skills/unit-tests/reference/jacoco-coverage.md` for the full Python coverage script, JaCoCo version pinning, and known Kotlin false positives table.

**Quick coverage check:**
1. Run `./gradlew :app:jacocoTestReport` after tests pass
2. Use the method-level coverage Python script from the reference file to check per-method LINE and BRANCH coverage
3. If coverage is below 95%, add tests for the specific missed methods/branches identified by the script
4. Re-run tests and coverage until both LINE and BRANCH are 95%+

> **JaCoCo gotcha**: If coverage still shows 0% or stale data, run:
> ```bash
> cd Android && ./gradlew cleanTestDebugUnitTest :app:testDebugUnitTest --tests "*.{ClassName}Test" :app:jacocoTestReport
> ```

</process>

<success_criteria>
unit-tests skill is complete when:

**Universal (all class types):**
- [ ] Test file compiles with no errors
- [ ] `./gradlew :app:testDebugUnitTest --tests "*.{ClassName}Test"` reports BUILD SUCCESSFUL with 0 failures
- [ ] `@OptIn(ExperimentalCoroutinesApi::class)` on the test class declaration
- [ ] Lifecycle annotations match project's JUnit version (`@Before`/`@After` for JUnit 4, `@BeforeEach`/`@AfterEach` for JUnit 5)
- [ ] `clearAllMocks()` in teardown (preferred over `unmockkAll()`)
- [ ] All public methods have at least one test
- [ ] Suspend functions use `runTest`
- [ ] `assertThrows` with suspend functions uses `runBlocking` wrapper
- [ ] Flow methods use Turbine `.test { }` block
- [ ] Shared `httpException(code)` helper used for all HTTP error tests
- [ ] Shared stub helpers extract repeated `coEvery`/`every` blocks
- [ ] Inline test fixtures (no FakeFactory class) — `private val` members with `.copy()` for variants
- [ ] `service` or `repository` naming used consistently (never `sut`)
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

**Class-type-specific criteria** — see the corresponding pattern file:
- **Service**: `patterns/service.md` — network guards, gating conditions, dialog callbacks, iteration side effects
- **Repository**: `patterns/repository.md` — DAO mocking, API chains, insert-or-update, catch-and-rethrow/swallow
- **Reducer**: `patterns/reducer.md` — pure function, null returns, boolean toggles, state preservation
- **ViewModel**: `patterns/viewmodel.md` — initial state, side effects, flow subscriptions, @Assisted, three test categories
</success_criteria>
