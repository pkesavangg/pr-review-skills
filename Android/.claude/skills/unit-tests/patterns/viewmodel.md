# ViewModel Test Patterns

These patterns apply when testing classes named `*ViewModel.kt` that extend `BaseIntentViewModel`.

ViewModels are the most complex class to test because they combine:
- State management (via reducer)
- Side effects (navigation, dialogs, toasts)
- Coroutine-based async operations (`viewModelScope.launch`)
- Flow subscriptions (init blocks)
- Service/repository dependencies

Per [official Android docs](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-viewmodel): ViewModels are the **#1 priority** for unit testing.

## Step 12.1: What to read for ViewModels

Read these files in parallel:
- The ViewModel source file (e.g., `SettingsViewModel.kt`)
- The Reducer it uses (e.g., `SettingsReducer.kt`) — already tested separately
- `BaseIntentViewModel.kt` at `features/common/service/BaseIntentViewModel.kt`
- `BaseViewModel.kt` at `features/common/viewmodel/BaseViewModel.kt` — provides `navigationService`, `dialogQueueService`, `customTabManager`
- All constructor dependency interfaces (services, repositories)

Key things to note:
- **Constructor parameters** → these become your mocks
- **`init {}` blocks** → flows subscribed at construction time, must be stubbed before `createViewModel()`
- **`handleIntent()` method** → which intents trigger side effects vs pure state changes
- **`viewModelScope.launch` blocks** → async operations need `advanceUntilIdle()`
- **`@Assisted` parameters** → runtime values passed via factory

## Step 12.2: ViewModel mocking rules

| Dependency | Mock Style | Reason |
|---|---|---|
| Service interfaces (`IAccountService`, etc.) | `mockk()` strict | Catch unexpected calls |
| `IAppNavigationService` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, fire-and-forget |
| `IDialogQueueService` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, lambda-based |
| `ICustomTabManager` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, fire-and-forget |
| Manager/helper classes | `mockk(relaxed = true)` | Usually fire-and-forget |

> **BaseViewModel injection**: `navigationService`, `dialogQueueService`, and `customTabManager` are `@Inject lateinit var` in `BaseViewModel`. In tests, set them via reflection or use `mockk(relaxed = true)` — they're not constructor params.

## Step 12.3: ViewModel test structure

```kotlin
package com.dmdbrands.gurus.weight.features.{feature}.viewmodel

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
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class {Feature}ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks: constructor dependencies ---
    private val accountService: IAccountService = mockk()
    private val entryService: IEntryService = mockk()
    // ... other constructor dependencies

    // --- Mocks: BaseViewModel injected services ---
    private val navigationService: IAppNavigationService = mockk(relaxed = true)
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)

    private lateinit var viewModel: {Feature}ViewModel

    @Before
    fun setUp() {
        // Stub flows collected in init {} blocks
        every { accountService.activeAccountFlow } returns flowOf(fakeAccount)
        // ... stub other init-block flows

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createViewModel(): {Feature}ViewModel {
        val vm = {Feature}ViewModel(
            accountService,
            entryService,
            // ... all constructor args
        )
        // Inject BaseViewModel dependencies via reflection or direct assignment
        vm.navigationService = navigationService
        vm.dialogQueueService = dialogQueueService
        return vm
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has correct defaults`() {
        val state = viewModel.state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.items).isEmpty()
    }

    // -------------------------------------------------------------------------
    // {IntentName} — state changes
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading intent updates isLoading in state`() {
        viewModel.handleIntent({Feature}Intent.SetLoading(true))

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // {IntentName} — side effects
    // -------------------------------------------------------------------------

    @Test
    fun `NavigateToDetails calls navigationService`() = runTest {
        viewModel.handleIntent({Feature}Intent.NavigateToDetails("item-1"))
        advanceUntilIdle()

        verify { navigationService.navigateTo(any()) }
    }
}
```

## Step 12.4: ViewModel test patterns

### Pattern A: Test initial state (per official Android docs)

Every ViewModel test file must start with an initial state test:

```kotlin
@Test
fun `initial state has correct defaults`() {
    val state = viewModel.state.value

    assertThat(state.isLoading).isFalse()
    assertThat(state.errorMessage).isNull()
    assertThat(state.account).isNull()
    assertThat(state.currentThemeMode).isEqualTo("System Settings")
}
```

> Per [official docs](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-viewmodel#3): always test initialization state as a boundary case.

### Pattern B: Test state changes via handleIntent (success path)

```kotlin
@Test
fun `LoadSettings sets isLoading then populates account`() = runTest {
    coEvery { accountService.getActiveAccount() } returns fakeAccount

    viewModel.handleIntent(SettingsIntent.LoadSettings)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.isLoading).isFalse()
    assertThat(state.account).isEqualTo(fakeAccount)
}
```

> **Always access state via `viewModel.state.value`** — this is the canonical way to assert ViewModel state per official docs.

### Pattern C: Test state changes (error path)

```kotlin
@Test
fun `LoadSettings sets error when service throws`() = runTest {
    coEvery { accountService.getActiveAccount() } throws RuntimeException("Network error")

    viewModel.handleIntent(SettingsIntent.LoadSettings)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.isLoading).isFalse()
    assertThat(state.errorMessage).isNotNull()
}
```

### Pattern D: Test navigation side effects

```kotlin
@Test
fun `OpenAddScales navigates to AddEditScales route`() = runTest {
    viewModel.handleIntent(SettingsIntent.OpenAddScales)
    advanceUntilIdle()

    verify { navigationService.navigateTo(AppRoute.AccountSettings.AddEditScales) }
}

@Test
fun `back navigation calls navigateBack`() = runTest {
    viewModel.handleIntent({Feature}Intent.NavigateBack)
    advanceUntilIdle()

    verify { navigationService.navigateBack() }
}
```

### Pattern E: Test dialog/toast side effects

```kotlin
@Test
fun `DeleteEntry shows confirmation dialog`() = runTest {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(fakeEntry))
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    assertThat(dialog.title).isNotNull()
}

@Test
fun `error shows error toast`() = runTest {
    coEvery { entryService.syncOperations() } throws RuntimeException("sync failed")

    viewModel.handleIntent({Feature}Intent.Refresh)
    advanceUntilIdle()

    verify { dialogQueueService.showToast(any()) }
}
```

### Pattern F: Test flow subscriptions from init block

```kotlin
@Test
fun `init subscribes to active account flow and updates state`() = runTest {
    // Flow was stubbed in setUp() before createViewModel()
    advanceUntilIdle()

    assertThat(viewModel.state.value.account).isEqualTo(fakeAccount)
}

@Test
fun `state updates when account flow emits new value`() = runTest {
    val accountFlow = MutableStateFlow(fakeAccount)
    every { accountService.activeAccountFlow } returns accountFlow
    viewModel = createViewModel()
    advanceUntilIdle()

    assertThat(viewModel.state.value.account).isEqualTo(fakeAccount)

    // Emit new account
    accountFlow.value = fakeAccount2
    advanceUntilIdle()

    assertThat(viewModel.state.value.account).isEqualTo(fakeAccount2)
}
```

### Pattern G: Test @AssistedInject ViewModels

ViewModels with runtime parameters use `@Assisted`:

```kotlin
private fun createViewModel(month: String = "2026-03"): HistoryDetailViewModel {
    val vm = HistoryDetailViewModel(
        entryService = entryService,
        healthConnectService = healthConnectService,
        month = month,  // @Assisted parameter
    )
    vm.navigationService = navigationService
    vm.dialogQueueService = dialogQueueService
    return vm
}

@Test
fun `init loads history for the given month`() = runTest {
    coEvery { entryService.monthDetails("2026-03") } returns flowOf(listOf(fakeEntry))

    viewModel = createViewModel(month = "2026-03")
    advanceUntilIdle()

    coVerify { entryService.monthDetails("2026-03") }
    assertThat(viewModel.state.value.historyItems).isNotEmpty()
}
```

### Pattern H: Test StateFlow emissions with Turbine

When you need to verify intermediate state transitions (loading → loaded):

```kotlin
@Test
fun `Refresh emits loading then loaded states`() = runTest {
    coEvery { entryService.syncOperations() } returns Unit

    viewModel.state.test {
        // Initial state
        val initial = awaitItem()
        assertThat(initial.isRefreshing).isFalse()

        viewModel.handleIntent({Feature}Intent.Refresh)

        // Loading state
        val loading = awaitItem()
        assertThat(loading.isRefreshing).isTrue()

        // Loaded state
        val loaded = awaitItem()
        assertThat(loaded.isRefreshing).isFalse()

        cancelAndIgnoreRemainingEvents()
    }
}
```

> **Strong preference**: Use `viewModel.state.value` for all final-state assertions — it's the [official Android recommendation](https://developer.android.com/kotlin/coroutines/test). Reserve Turbine `.test {}` for cases where you **must** verify the sequence of intermediate state transitions (e.g., loading → loaded). StateFlow is conflated, so Turbine may miss rapid intermediate emissions.

### Pattern I: Three test categories (per official docs)

Per [Android testing codelab](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-viewmodel#3), every ViewModel must have tests in these three categories:

| Category | What to test | Example |
|---|---|---|
| **Success path** | Valid inputs → correct state | Correct word guessed → score increases |
| **Error path** | Invalid inputs / failures → error state | API throws → error message shown |
| **Boundary cases** | Initial state, max limits, empty data | First load, all items processed, empty list |

## Step 12.5: ViewModel vs Reducer — test separation

| What to test | Where | Why |
|---|---|---|
| Pure state transitions (`state.copy(...)`) | **ReducerTest** | Pure function, no mocks needed |
| Side effects (navigation, dialogs, API calls) | **ViewModelTest** | Requires mocks and coroutines |
| Async operations (`viewModelScope.launch`) | **ViewModelTest** | Requires `MainDispatcherRule` + `advanceUntilIdle()` |
| Flow subscriptions (init block) | **ViewModelTest** | Requires stubbed flows before construction |
| Intent routing (`handleIntent` dispatches) | **ViewModelTest** | Verifies correct intent → side effect mapping |

> **Do NOT re-test reducer logic in ViewModel tests.** The reducer is tested separately. ViewModel tests focus on: (1) correct intents dispatched, (2) side effects triggered, (3) flow subscriptions work.

## Step 12.6: ViewModel test naming convention

Per official docs, use descriptive names following: `thingUnderTest_condition_expectedResult`

```kotlin
// ✅ Good names
fun `handleIntent LoadSettings sets isLoading then populates account`()
fun `handleIntent Refresh shows error toast when sync fails`()
fun `initial state has correct defaults`()
fun `init subscribes to account flow and updates state`()

// ❌ Bad names (too vague)
fun `test load`()
fun `settings work`()
```

## Step 12.7: ViewModel quick_start adjustment

When testing a ViewModel, modify the quick_start steps:
1. Ask the user for the source file path if not provided.
2. Read the ViewModel source file + its Reducer + its State/Intent definitions.
3. Read `BaseIntentViewModel.kt` and `BaseViewModel.kt` to understand inherited behavior.
4. Read constructor dependency interfaces (services, repositories, managers).
5. Identify: init blocks (flow subscriptions), handleIntent branches (side effects), viewModelScope.launch calls.
6. Generate **ReducerTest** first (simpler, no mocks) → then **ViewModelTest** (side effects, async).
7. Run tests → fix failures → re-run.
8. Run JaCoCo coverage → check method-level LINE + BRANCH → add missing tests → re-run.

## ViewModel-specific success criteria

- [ ] `MainDispatcherRule` used (ViewModels use `viewModelScope` which needs `Dispatchers.Main`)
- [ ] All constructor dependencies mocked
- [ ] `BaseViewModel` injected services (`navigationService`, `dialogQueueService`) set on the ViewModel
- [ ] Initial state tested (boundary case per official docs)
- [ ] Init block flow subscriptions tested (stub flows before `createViewModel()`)
- [ ] Side effects tested: navigation, dialogs, toasts, service calls
- [ ] `advanceUntilIdle()` used after `handleIntent()` for async side effects
- [ ] Three test categories covered: success path, error path, boundary cases
- [ ] Reducer logic NOT re-tested in ViewModel tests (tested separately in ReducerTest)
- [ ] `@Assisted` parameters handled in `createViewModel()` helper
- [ ] `viewModel.state.value` used for final-state assertions (Turbine only for intermediate emissions)

## Coroutine/Flow testing checklist (from official Android docs)

- [ ] Single `TestCoroutineScheduler` shared across all `TestDispatcher` instances in each test
- [ ] `backgroundScope` used for non-completing hot flows (auto-cancelled at test end)
- [ ] StateFlow assertions use `.value` (not Turbine) unless testing intermediate emissions
- [ ] `SharingStarted.WhileSubscribed`/`Lazily` flows have active collector before assertion
- [ ] `advanceUntilIdle()` / `advanceTimeBy()` / `runCurrent()` chosen appropriately for the scenario
- [ ] Dispatcher/scope injected via constructor (no hardcoded `Dispatchers.IO` in testable code)
