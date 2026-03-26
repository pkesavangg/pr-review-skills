---
name: unit-tests
description: Generates comprehensive unit tests AND instrumented tests for Android classes following the MeApp testing pattern. Supports service, repository, ViewModel, Reducer, DAO, and Compose UI classes. Auto-detects whether to generate unit tests (test/) or instrumented tests (androidTest/) based on the source file type. Use when given a source file path and asked to write tests for it.
---

<objective>
Read an Android source class and generate a complete, passing test file following project conventions. **Auto-detects** whether to produce a unit test or instrumented test based on file type.

**Unit tests** (services, repositories, ViewModels, reducers):
- MockK for all mocking (no Mockito)
- Truth for assertions (`assertThat`)
- `runTest` for suspend functions
- Turbine for Flow testing (`.test { }`)
- MainDispatcherRule for coroutine dispatcher control
- `@OptIn(ExperimentalCoroutinesApi::class)` on the test class declaration
- `assertThrows(X::class.java) { runBlocking { ... } }` for exception tests — never manual try/catch
- Inline test fixtures as `private val` members — no factory classes
- Shared stub helpers for DRY — extract repeated `every`/`coEvery` blocks
- Shared `httpException(code)` helper for HTTP error tests
- Target: 95%+ JaCoCo line coverage AND 95%+ branch coverage

**Instrumented tests** (DAOs, Compose UI components):
- **DAO tests**: Extend `BaseDaoTest`, use `DaoTestFixtures`, in-memory Room DB, `runTest`, Truth assertions, `flow.first()` for Room Flow testing, FK parent-first insertion, CASCADE verification
- **Compose UI tests**: `createComposeRule()`, `MeAppTheme` wrapping, semantic matchers (`onNodeWithText`, `onNodeWithTag`), actions (`performClick`, `performTextInput`), assertions (`assertIsDisplayed`, `assertDoesNotExist`)
- File in `androidTest/`, `@RunWith(AndroidJUnit4::class)`, no MockK/Turbine (version conflicts with compose-ui-test)
- Cover ALL public methods/queries: happy path, error paths, edge cases, gating conditions
</objective>

<quick_start>
1. Ask the user for the source file path if not provided in the arguments.
2. **Auto-detect class type** (see routing table below) and read the corresponding pattern file.
3. Read the source file + its interface/abstract class.
4. **Route by test type:**
   - **Unit tests** (services, repositories, ViewModels, reducers): Steps 0–9 below.
   - **Instrumented tests** (DAOs, Compose UI): Steps I-1 through I-7 below.
5. Read constructor/dependency interfaces to know what to mock or set up.
6. Identify ALL public methods, their dependencies, branches, catch blocks, and flows.
7. Generate the test file into the correct directory:
   - Unit tests → `app/src/test/java/...`
   - Instrumented tests → `app/src/androidTest/java/...`
8. Run tests → fix failures → re-run (iterative loop until green).
9. Post-write review pass.

> **Detect class type and load patterns**: Check the source file path and content:
>
> **Unit test types** (output to `test/`):
> - Files in `core/service/` or `domain/services/` → **Service** — Read `.claude/skills/unit-tests/patterns/service.md`
> - Files in `data/repository/` → **Repository** — Read `.claude/skills/unit-tests/patterns/repository.md`
> - Files named `*Reducer.kt` implementing `IReducer` → **Reducer** — Read `.claude/skills/unit-tests/patterns/reducer.md`
> - Files named `*ViewModel.kt` extending `BaseIntentViewModel` → **ViewModel** — Read `.claude/skills/unit-tests/patterns/reducer.md` AND `.claude/skills/unit-tests/patterns/viewmodel.md`
> - For ViewModels: generate **ReducerTest first** (simpler), then **ViewModelTest** (side effects)
>
> **Instrumented test types** (output to `androidTest/`):
> - Files in `data/storage/db/dao/` or named `*Dao.kt` → **DAO** — Read `.claude/skills/unit-tests/patterns/dao.md`
> - Files in `features/*/components/` or `features/*/views/` or any `@Composable` file → **Compose UI** — Read `.claude/skills/unit-tests/patterns/compose-ui.md`
>
> **On-demand reference files** (Read only when needed):
> - At Step 8 (coverage verification): Read `.claude/skills/unit-tests/reference/jacoco-coverage.md`
> - If tests fail with unfamiliar errors: Read `.claude/skills/unit-tests/reference/troubleshooting.md`
> - For advanced testing patterns (dispatchers, flow testing, anti-patterns): Read `.claude/skills/unit-tests/reference/android-testing-practices.md`
</quick_start>

Read the source file and load the corresponding reference file:

| Source location / signature | Type | Load reference file(s) |
|---|---|---|
| `core/service/` or `domain/services/` | **Service** | [reference/service.md](reference/service.md) |
| `data/repository/` | **Repository** | [reference/repository.md](reference/repository.md) |
| `*Reducer.kt` implementing `IReducer` | **Reducer** | [reference/reducer.md](reference/reducer.md) |
| `*ViewModel.kt` extending `BaseIntentViewModel` | **ViewModel** | [reference/reducer.md](reference/reducer.md) AND [reference/viewmodel.md](reference/viewmodel.md) |

For ViewModels: generate **ReducerTest first** (simpler, pure function), then **ViewModelTest** (side effects).

## Workflow

### Step 1: Read source files

Ask for the source file path if not provided via `$ARGUMENTS`. Read in parallel:
- The source file + the interface it implements
- **Services**: `BaseService.kt` at `core/service/BaseService.kt` (for inherited `isNetworkAvailable`, `requireNetworkAvailable`, `showNetworkErrorAndThrow`, etc.)
- **Repositories**: DAO interfaces, API interfaces, DataStore, TokenManager, entity/mapper classes
- **ViewModels**: `BaseIntentViewModel.kt` at `features/common/service/BaseIntentViewModel.kt` and `BaseViewModel.kt` at `features/common/viewmodel/BaseViewModel.kt`
- Constructor dependency interfaces (to know what to mock and stub)

Note while reading:
- Constructor parameters -> become mocks
- `suspend` methods -> use `runTest`
- `Flow<T>` returns -> use Turbine `.test { }`
- `requireNetworkAvailable()` / `isNetworkAvailable()` -> need offline tests
- `catch` blocks -> one test per catch; `when (e.code())` -> one test per code + else
- Boolean state fields -> test both true and false
- `init {}` flow subscriptions -> stub BEFORE `createService()`/`createViewModel()`
- Hardcoded `Dispatchers.IO` -> inject `ioDispatcher` for testability

### Step 2: Plan test groups

For each public method, plan:
- Happy path(s)
- Error paths (exceptions, catch blocks, HTTP error codes)
- Network routing (online/offline) if applicable
- Gating conditions (null account, empty string, max limits, isExpired)
- Side effects (toasts, dialogs, auth events, navigation, repository writes)
- Dialog callbacks (`onConfirm`, `onCancel`, `onDismiss`)
- Iteration side effects (per-item verification for collection loops)
- Boolean state variants (both true and false)

Target: 3-4 tests per non-trivial method, 2 per simple delegation method.

### Step 3: Write the test file

Load the class-type-specific reference file (see table above). Also load:
- [reference/test-templates.md](reference/test-templates.md) — file structure, imports, mocking rules, shared testing patterns
- [reference/testing-stack.md](reference/testing-stack.md) — only if unsure about JUnit version or dependency versions

Place the test file mirroring the source path:
```
Source: app/src/main/java/com/dmdbrands/gurus/weight/{path}/Foo.kt
Test:   app/src/test/java/com/dmdbrands/gurus/weight/{path}/FooTest.kt
```

### Step 4: Verify method coverage completeness

Before writing, cross-check every public method in the interface has tests:
- 1 happy-path test minimum
- 1 error-path test (if method has a catch block)
- 1 offline test (if uses `requireNetworkAvailable` or `isNetworkAvailable`)
- 1 null/empty gate test (if applicable)
- 1 Flow emission test (for Flow properties)

### Step 5: Verify all imports are present

Scan for ALL types used. Common misses: `java.io.IOException`, `java.net.UnknownHostException`, `kotlinx.coroutines.runBlocking`, `retrofit2.HttpException`, shared helpers from `core.helpers.*`.

### Step 6: Run and iterate

```bash
cd Android && ./gradlew :app:testDebugUnitTest --tests "*.{ClassName}Test"

# Step 7a (DAO): Run instrumented tests (requires emulator/device)
cd Android && ./gradlew :app:connectedDebugAndroidTest --tests "*.{DaoName}Test"
```

Fix failures -> re-run until BUILD SUCCESSFUL. If errors are unfamiliar, read [reference/troubleshooting.md](reference/troubleshooting.md).

### Step 7: Post-write review pass

After all tests are green, do a single review pass:

1. **Dedup**: Remove tests with identical mock setup + assertion
2. **Input transformations**: Test `.trim()`, `.lowercase()` reach dependencies correctly
3. **Propagation boundaries**: If method catches only `HttpException`, test that `RuntimeException` propagates uncaught
4. **Relaxed mock audit**: Remove redundant `coEvery { } just Runs` stubs on `relaxed = true` mocks
5. **DRY**: Extract setup patterns appearing 3+ times into helper methods

### Step 8: Verify coverage

```bash
cd Android && ./gradlew :app:jacocoTestReport
```

Read [reference/jacoco-coverage.md](reference/jacoco-coverage.md) for the method-level coverage script. Target: 95%+ LINE and BRANCH coverage per method. Add tests for missed methods/branches and re-run.

## Success criteria

### 8a: Dedup pass
Scan test names for similar wording. If two tests stub the same mock path and assert the same outcome, remove the weaker one (keep the one with more thorough verification).

### 8b: Input transformation coverage
For every method under test, check if inputs are `.trim()`'d, `.lowercase()`'d, mapped, or transformed before being passed to dependencies. Write a test asserting the **transformed** value reaches the dependency, not just the raw input.

```kotlin
// Source: val email = email.trim()
// Test:
@Test
fun `resetPassword trims whitespace from email before calling repository`() = runTest {
    coEvery { accountRepository.resetPassword("john@example.com") } returns mockResponse
    service.resetPassword("  john@example.com  ")
    coVerify { accountRepository.resetPassword("john@example.com") }
}
```

### 8c: Propagation boundary coverage
If a method only catches a **specific** exception type (e.g., `HttpException`), write a test proving that other exceptions **propagate uncaught**. The absence of a generic `catch` block IS the behavior under test.

```kotlin
// Source catches only HttpException — RuntimeException should propagate
@Test
fun `updateProfile propagates non-HttpException`() = runTest {
    coEvery { accountRepository.updateProfile(any()) } throws RuntimeException("DB error")
    assertThrows(RuntimeException::class.java) {
        runBlocking { service.updateProfile(request, isFromProfile = false, showToast = true) }
    }
}
```

### 8d: Relaxed mock audit
If a mock is declared `relaxed = true`, do NOT add `coEvery { ... } just Runs` stubs for Unit-returning suspend functions — the relaxed mock already handles them. Scan the file and remove all redundant stubs.

### 8e: DRY pass
Any setup pattern appearing **3+ times** should be extracted to a helper method. Common candidates:

```kotlin
// Before: appears 11 times
every { accountRepository.getActiveAccount() } returns flowOf(null)
service = createService()

// After: extracted helper
private fun withNoActiveAccount() {
    every { accountRepository.getActiveAccount() } returns flowOf(null)
    service = createService()
}

// Before: appears 15 times
every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, fakeAccount2))
service = createService()

// After: extracted helper
private fun withAccounts(
    active: Account? = fakeAccount,
    loggedIn: List<Account> = listOfNotNull(active),
) {
    every { accountRepository.getActiveAccount() } returns flowOf(active)
    every { accountRepository.getLoggedInAccounts() } returns flowOf(loggedIn)
    service = createService()
}
```

## Step 9: Verify coverage — method-level LINE + BRANCH

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

---

## Instrumented Test Process (DAOs, Compose UI)

> These steps replace Steps 0–9 above when the detected class type is **DAO** or **Compose UI**.

## Step I-1: Detect instrumented test type

Check the source file to determine which instrumented test pattern to use:

| Source file location / type | Test type | Pattern file | Test output directory |
|---|---|---|---|
| `data/storage/db/dao/*Dao.kt` | **DAO** | `patterns/dao.md` | `androidTest/.../dao/` |
| `features/*/components/*.kt` with `@Composable` | **Compose UI** | `patterns/compose-ui.md` | `androidTest/.../` mirroring source |
| `features/*/views/screens/*.kt` with `@Composable` | **Compose UI** | `patterns/compose-ui.md` | `androidTest/.../` mirroring source |

## Step I-2: Read the source files

**For DAOs:**
- The DAO interface (e.g., `AccountDao.kt`)
- `BaseDaoTest.kt` at `androidTest/.../dao/BaseDaoTest.kt` — understand available DAOs and setup
- `DaoTestFixtures.kt` at `androidTest/.../dao/DaoTestFixtures.kt` — available fixture builders
- The entity classes the DAO operates on
- `AppDatabase.kt` — entity list, type converters, database views
- An existing DAO test in the same package (e.g., `AccountDaoTest.kt`) — follow the same conventions

**For Compose UI:**
- The composable source file
- The State/Intent classes if the composable reads ViewModel state
- `MeAppTheme` and any theme tokens used
- An existing Compose test (e.g., `MainBottomNavTest.kt`) — follow the same conventions
- Any shared composables the component depends on (`features/common/components/`)

## Step I-3: Plan the test groups

**For DAOs — test every query method:**
- CRUD operations: insert, read, update, delete
- FK cascade behavior (delete parent → verify children deleted)
- Conflict strategies (insert duplicate → verify REPLACE/IGNORE behavior)
- Flow queries: verify emissions after mutations using `flow.first()`
- Database views (e.g., `entry_view`): verify soft-delete filtering
- Aggregation queries: verify computed values with known inputs
- Edge cases: empty results, null returns, boundary values

**For Compose UI — test every user-visible behavior:**
- All text labels are displayed
- Click actions trigger correct callbacks
- State changes reflect in UI (loading → loaded, error states)
- Conditional rendering (show/hide based on props)
- Input fields accept and display text
- Navigation triggers (button clicks → route changes)
- Accessibility: content descriptions present

## Step I-4: Write the test file

> **IMPORTANT**: Before writing, Read the class-type-specific pattern file identified in Step I-1.

**For DAOs:**
```kotlin
package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
// ... other fixture imports
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class {DaoName}Test : BaseDaoTest() {
    // No @Before needed — BaseDaoTest handles DB + DAO setup
    // Use: accountDao, deviceDao, entryDao, logDao (from BaseDaoTest)
    // Use: DaoTestFixtures.account(), .device(), .entryEntity(), etc.

    @Test
    fun insertAndRetrieve() = runTest {
        val entity = account()
        accountDao.insertAccount(entity)
        assertThat(accountDao.getAccountEntity(entity.id)).isEqualTo(entity)
    }
}
```

**For Compose UI:**
```kotlin
package com.dmdbrands.gurus.weight.features.{feature}

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dmdbrands.gurus.weight.features.common.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

class {ComponentName}Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(/* params with defaults */) {
        composeTestRule.setContent {
            MeAppTheme {
                {ComponentName}(/* pass params */)
            }
        }
    }

    @Test
    fun displaysExpectedText() {
        setContent()
        composeTestRule.onNodeWithText("Expected Label").assertIsDisplayed()
    }
}
```

## Step I-5: Place the file

```
Source:  app/src/main/java/.../dao/FooDao.kt
Test:    app/src/androidTest/java/.../dao/FooDaoTest.kt

Source:  app/src/main/java/.../features/settings/components/SettingsCard.kt
Test:    app/src/androidTest/java/.../features/settings/components/SettingsCardTest.kt
```

> **All instrumented tests go in `androidTest/`** — never `test/`.

## Step I-6: Iterative test-fix loop (instrumented)

```bash
# Compile check (no emulator needed — catches 90% of errors)
cd Android && ./gradlew :app:compileDebugAndroidTestKotlin

# Run DAO tests (requires emulator/device)
cd Android && ./gradlew :app:connectedDebugAndroidTest --tests "*.{DaoName}Test"

# Run Compose UI tests (requires emulator/device)
cd Android && ./gradlew :app:connectedDebugAndroidTest --tests "*.{ComponentName}Test"

# Run ALL instrumented tests
cd Android && ./gradlew :app:connectedDebugAndroidTest
```

**Common instrumented test errors:**

| Error | Fix |
|---|---|
| `No connected devices` | Start an emulator: `emulator -avd <name>` |
| `IllegalStateException: Cannot access database on main thread` | Ensure `allowMainThreadQueries()` is set (BaseDaoTest handles this) |
| `SQLiteConstraintException: FOREIGN KEY constraint failed` | Insert parent entities before children (FK order) |
| `ComposeNotIdleException` | Add `composeTestRule.waitForIdle()` or `waitUntil { }` |
| `AssertionError: Expected node count: 1 but found: 0` | Check text spelling, use `substring = true`, or `useUnmergedTree = true` |
| `Unresolved reference: createComposeRule` | Add `androidTestImplementation(libs.androidx.ui.test.junit4)` and `debugImplementation(libs.androidx.ui.test.manifest)` |

## Step I-7: Post-write review pass (instrumented)

### I-7a: FK dependency order audit
Verify all tests insert parent entities before children. Use `insertFullAccount()` helper for Account FK dependencies.

### I-7b: Flow reactivity verification
For DAO tests with Flow-returning queries, verify reactive behavior:
```kotlin
// Before mutation
assertThat(dao.getCount().first()).isEqualTo(0)
// After mutation
dao.insert(entity)
assertThat(dao.getCount().first()).isEqualTo(1)
```

### I-7c: Soft-delete verification (EntryDao)
If testing entry queries that use `entry_view`, verify that soft-deleted entries are excluded.

### I-7d: DRY helpers
Extract repeated parent insertion into `private suspend fun insertParentAccount()` helpers.

### I-7e: Compose state testing
Verify state changes cause UI updates — set content, trigger action, assert new state visible.

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
- [ ] Shared `httpException(code)` imported from `core.helpers.TestHelpers` — no private copy
- [ ] Shared `stubNetworkAvailable/Unavailable` imported from `core.helpers.TestHelpers` — delegate via private methods
- [ ] Shared stub helpers extract repeated `coEvery`/`every` blocks
- [ ] Services with `CoroutineScope(Dispatchers.IO)` have `ioDispatcher` injected and test passes `mainDispatcherRule.dispatcher`
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
- [ ] **Post-write review pass completed (Step 8)**:
  - [ ] No duplicate tests (same mock setup + same assertion = duplicate)
  - [ ] Input transformations tested (`.trim()`, `.lowercase()`, etc.)
  - [ ] Exception propagation boundaries tested (if method only catches `HttpException`, test that `RuntimeException` propagates)
  - [ ] No redundant stubs on `relaxed = true` mocks (Unit-returning suspend funs auto-return Unit)
  - [ ] Repeated setup patterns (3+ occurrences) extracted to helper methods
- [ ] JaCoCo per-method LINE coverage 95%+ AND per-method BRANCH coverage 95%+
- [ ] Coverage verified using method-level JaCoCo script (not just class-level)
- [ ] Phantom branch misses from Kotlin compiler artifacts acknowledged, not force-tested

**Class-type-specific criteria (unit tests)** — see the corresponding pattern file:
- **Service**: `patterns/service.md` — network guards, gating conditions, dialog callbacks, iteration side effects
- **Repository**: `patterns/repository.md` — DAO mocking, API chains, insert-or-update, catch-and-rethrow/swallow
- **Reducer**: `patterns/reducer.md` — pure function, null returns, boolean toggles, state preservation
- **ViewModel**: `patterns/viewmodel.md` — initial state, side effects, flow subscriptions, @Assisted, three test categories

**Instrumented test criteria (DAO)** — see `patterns/dao.md`:
- [ ] Test extends `BaseDaoTest` (provides in-memory DB + all DAOs)
- [ ] `@RunWith(AndroidJUnit4::class)` annotation on class
- [ ] Uses `DaoTestFixtures` for entity construction (not manual constructors)
- [ ] FK parents inserted before children (`insertFullAccount()` helper)
- [ ] Flow queries tested with `flow.first()` (NOT Turbine — version conflict with compose-ui-test)
- [ ] Reactive behavior verified: `first()` before mutation + `first()` after mutation
- [ ] `entry_view` soft-delete filtering tested (if EntryDao)
- [ ] CASCADE delete behavior tested (delete parent → children gone)
- [ ] Aggregation queries use noon UTC timestamps for determinism
- [ ] Conflict strategies tested (duplicate insert → verify REPLACE/IGNORE)
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` passes (compile check without emulator)
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "*.{DaoName}Test"` passes on emulator
- [ ] File placed in `androidTest/` (NOT `test/`)

**Instrumented test criteria (Compose UI)** — see `patterns/compose-ui.md`:
- [ ] Uses `createComposeRule()` (standalone, no Activity)
- [ ] Content wrapped in `MeAppTheme { }` (supports light/dark themes)
- [ ] Private `setContent()` helper method to reduce duplication
- [ ] All visible text labels verified with `onNodeWithText().assertIsDisplayed()`
- [ ] Click actions tested: `performClick()` → verify callback invoked
- [ ] Conditional rendering tested (show/hide based on props/state)
- [ ] `Modifier.testTag()` used for non-text elements, tested via `onNodeWithTag()`
- [ ] State changes cause UI updates (set content → trigger action → assert new state)
- [ ] No MockK usage (use callback flags/counters instead)
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` passes
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "*.{ComponentName}Test"` passes on emulator
- [ ] File placed in `androidTest/` mirroring source package
</success_criteria>
