# ViewModel Test Patterns

These patterns apply when testing classes named `*ViewModel.kt` that extend `BaseIntentViewModel`.

ViewModels are the most complex class to test because they combine:
- State management (via reducer)
- Side effects (navigation, dialogs, toasts)
- Coroutine-based async operations (`viewModelScope.launch`)
- Flow subscriptions (init blocks)
- Service/repository dependencies

Per [official Android docs](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-viewmodel): ViewModels are the **#1 priority** for unit testing.

## What to read

Read these files in parallel:
- The ViewModel source file (e.g., `SettingsViewModel.kt`)
- The Reducer it uses (e.g., `SettingsReducer.kt`) — already tested separately
- `BaseIntentViewModel.kt` at `features/common/service/BaseIntentViewModel.kt`
- `BaseViewModel.kt` at `features/common/viewmodel/BaseViewModel.kt` — provides `navigationService`, `dialogQueueService`, `customTabManager`
- All constructor dependency interfaces (services, repositories)

Key things to note:
- **Constructor parameters** -> become mocks
- **`init {}` blocks** -> flows subscribed at construction time, must stub before `createViewModel()`
- **`handleIntent()` method** -> which intents trigger side effects vs pure state changes
- **`viewModelScope.launch` blocks** -> async operations need `advanceUntilIdle()`
- **`@Assisted` parameters** -> runtime values passed via factory

## Mocking rules

| Dependency | Mock Style | Reason |
|---|---|---|
| Service interfaces (`IAccountService`, etc.) | `mockk()` strict | Catch unexpected calls |
| `IAppNavigationService` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, fire-and-forget |
| `IDialogQueueService` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, lambda-based |
| `ICustomTabManager` | `mockk(relaxed = true)` | Inherited from `BaseViewModel`, fire-and-forget |
| Manager/helper classes | `mockk(relaxed = true)` | Usually fire-and-forget |

> **BaseViewModel injection**: `navigationService`, `dialogQueueService`, and `customTabManager` are `@Inject lateinit var` in `BaseViewModel`. In tests, set them directly after construction.

## Test structure

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class {Feature}ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks: constructor dependencies ---
    private val accountService: IAccountService = mockk()

    // --- Mocks: BaseViewModel injected services ---
    private val navigationService: IAppNavigationService = mockk(relaxed = true)
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)

    private lateinit var viewModel: {Feature}ViewModel

    @Before
    fun setUp() {
        every { accountService.activeAccountFlow } returns flowOf(fakeAccount)
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createViewModel(): {Feature}ViewModel {
        val vm = {Feature}ViewModel(accountService /* ... */)
        vm.navigationService = navigationService
        vm.dialogQueueService = dialogQueueService
        return vm
    }
}
```

## Test patterns

### Pattern A: Initial state (always test first)

```kotlin
@Test
fun `initial state has correct defaults`() {
    val state = viewModel.state.value
    assertThat(state.isLoading).isFalse()
    assertThat(state.errorMessage).isNull()
    assertThat(state.items).isEmpty()
}
```

> Per [official docs](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-viewmodel#3): always test initialization state as a boundary case.

### Pattern B: State changes via handleIntent (success)

```kotlin
@Test
fun `LoadSettings sets isLoading then populates account`() = runTest {
    coEvery { accountService.getActiveAccount() } returns fakeAccount
    viewModel.handleIntent(SettingsIntent.LoadSettings)
    advanceUntilIdle()
    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.account).isEqualTo(fakeAccount)
}
```

> **Always access state via `viewModel.state.value`** — canonical way per official docs.

### Pattern C: State changes (error path)

```kotlin
@Test
fun `LoadSettings sets error when service throws`() = runTest {
    coEvery { accountService.getActiveAccount() } throws RuntimeException("Network error")
    viewModel.handleIntent(SettingsIntent.LoadSettings)
    advanceUntilIdle()
    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.errorMessage).isNotNull()
}
```

### Pattern D: Navigation side effects

```kotlin
@Test
fun `OpenAddScales navigates to AddEditScales route`() = runTest {
    viewModel.handleIntent(SettingsIntent.OpenAddScales)
    advanceUntilIdle()
    verify { navigationService.navigateTo(AppRoute.AccountSettings.AddEditScales) }
}
```

### Pattern E: Dialog/toast side effects

```kotlin
@Test
fun `DeleteEntry shows confirmation dialog`() = runTest {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(fakeEntry))
    advanceUntilIdle()
    assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Confirm::class.java)
}
```

### Pattern F: Flow subscriptions from init block

```kotlin
@Test
fun `init subscribes to active account flow and updates state`() = runTest {
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

    accountFlow.value = fakeAccount2
    advanceUntilIdle()
    assertThat(viewModel.state.value.account).isEqualTo(fakeAccount2)
}
```

### Pattern G: @AssistedInject ViewModels

```kotlin
private fun createViewModel(month: String = "2026-03"): HistoryDetailViewModel {
    val vm = HistoryDetailViewModel(
        entryService = entryService,
        month = month,  // @Assisted parameter
    )
    vm.navigationService = navigationService
    vm.dialogQueueService = dialogQueueService
    return vm
}
```

### Pattern H: StateFlow emissions with Turbine

Use only when verifying intermediate state transitions (loading -> loaded):

```kotlin
@Test
fun `Refresh emits loading then loaded states`() = runTest {
    coEvery { entryService.syncOperations() } returns Unit
    viewModel.state.test {
        val initial = awaitItem()
        assertThat(initial.isRefreshing).isFalse()
        viewModel.handleIntent({Feature}Intent.Refresh)
        assertThat(awaitItem().isRefreshing).isTrue()
        assertThat(awaitItem().isRefreshing).isFalse()
        cancelAndIgnoreRemainingEvents()
    }
}
```

> **Strong preference**: Use `viewModel.state.value` for all final-state assertions. Reserve Turbine for cases where you must verify intermediate state transitions. StateFlow is conflated, so Turbine may miss rapid emissions.

## Three test categories (per official docs)

| Category | What to test | Example |
|---|---|---|
| **Success path** | Valid inputs -> correct state | Correct data -> score increases |
| **Error path** | Invalid inputs / failures -> error state | API throws -> error message shown |
| **Boundary cases** | Initial state, max limits, empty data | First load, empty list |

## ViewModel vs Reducer — test separation

| What to test | Where | Why |
|---|---|---|
| Pure state transitions (`state.copy(...)`) | **ReducerTest** | Pure function, no mocks |
| Side effects (navigation, dialogs, API calls) | **ViewModelTest** | Requires mocks + coroutines |
| Async operations (`viewModelScope.launch`) | **ViewModelTest** | Requires `MainDispatcherRule` |
| Flow subscriptions (init block) | **ViewModelTest** | Requires stubbed flows |

> **Do NOT re-test reducer logic in ViewModel tests.** Focus on: correct intents dispatched, side effects triggered, flow subscriptions work.

## Test naming convention

Per official docs: `thingUnderTest_condition_expectedResult`

```kotlin
// Good
fun `handleIntent LoadSettings sets isLoading then populates account`()
fun `initial state has correct defaults`()

// Bad
fun `test load`()
fun `settings work`()
```

## ViewModel-specific success criteria

- [ ] `MainDispatcherRule` used
- [ ] All constructor dependencies mocked
- [ ] `BaseViewModel` services (`navigationService`, `dialogQueueService`) set on the ViewModel
- [ ] Initial state tested (boundary case)
- [ ] Init block flow subscriptions tested (stub flows before `createViewModel()`)
- [ ] Side effects tested: navigation, dialogs, toasts, service calls
- [ ] `advanceUntilIdle()` used after `handleIntent()` for async side effects
- [ ] Three test categories covered: success, error, boundary
- [ ] Reducer logic NOT re-tested (tested separately in ReducerTest)
- [ ] `@Assisted` parameters handled in `createViewModel()` helper
- [ ] `viewModel.state.value` used for final-state assertions

## Coroutine/Flow testing checklist

- [ ] Single `TestCoroutineScheduler` shared across all dispatchers
- [ ] `backgroundScope` used for non-completing hot flows
- [ ] StateFlow assertions use `.value` (not Turbine) unless testing intermediate emissions
- [ ] `SharingStarted.WhileSubscribed`/`Lazily` flows have active collector before assertion
- [ ] Dispatcher/scope injected via constructor (no hardcoded `Dispatchers.IO`)
