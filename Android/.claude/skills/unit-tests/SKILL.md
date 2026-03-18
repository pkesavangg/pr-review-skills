---
name: unit-tests
description: Generates comprehensive MockK unit tests for Android service, repository, ViewModel, and Reducer classes following the MeApp testing pattern. Use when given a source file path and asked to write unit tests for it.
argument-hint: [source-file-path]
allowed-tools: Read, Grep, Glob, Bash(cd Android && ./gradlew *), Write, Edit
---

# Unit Test Generator

Read an Android source class and generate a complete, passing unit test file following MeApp conventions: MockK mocking, Truth assertions, Turbine for Flows, `runTest` for coroutines, and 95%+ JaCoCo coverage.

## Detect class type

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

**All class types:**
- [ ] BUILD SUCCESSFUL with 0 test failures
- [ ] Every public method has at least one test
- [ ] `@OptIn(ExperimentalCoroutinesApi::class)` on test class (except reducers)
- [ ] `clearAllMocks()` in `@After` (except reducers)
- [ ] Suspend functions use `runTest`; `assertThrows` wraps suspend calls in `runBlocking`
- [ ] Flow methods use Turbine `.test { }`; multi-flow tests use `turbineScope`
- [ ] Shared helpers imported from `core.helpers.TestHelpers` — no private copies of `httpException()` or `stubNetwork*()`
- [ ] `coEvery`/`coVerify` for suspend functions; `every`/`verify` for non-suspend
- [ ] No `Thread.sleep()` — use `advanceUntilIdle()` or `advanceTimeBy()`
- [ ] Inline fixtures with `.copy()` for variants (no factory classes)
- [ ] Consistent naming: `service`/`repository`/`viewModel` (never `sut`)
- [ ] No Mockito imports — MockK only
- [ ] Post-write review pass completed (Step 7)
- [ ] JaCoCo per-method LINE + BRANCH at 95%+
- [ ] Class-type-specific criteria from the loaded reference file satisfied
