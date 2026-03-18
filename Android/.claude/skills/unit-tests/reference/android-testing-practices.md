# Official Android Testing Best Practices

These guidelines are derived from official Android developer documentation (sources: [developer.android.com/kotlin/coroutines/test](https://developer.android.com/kotlin/coroutines/test), [developer.android.com/kotlin/flow/test](https://developer.android.com/kotlin/flow/test), [developer.android.com/training/testing/local-tests](https://developer.android.com/training/testing/local-tests), [developer.android.com/training/testing/fundamentals/test-doubles](https://developer.android.com/training/testing/fundamentals/test-doubles)), [MockK docs](https://mockk.io/), and [Turbine docs](https://github.com/cashapp/turbine).

## What to unit test (priority order per Google)

1. **ViewModels / Presenters** — state management, business logic
2. **Repositories** — data retrieval, transformation, error handling
3. **Services / Use cases** — business rules in isolation
4. **Utility classes** — string manipulation, math, helpers

**Do NOT unit test**: Framework/library correctness (Room SQL queries, Retrofit serialization) — those belong in instrumented tests. Our mock-based approach tests that the *repository calls the right DAO/API methods with the right arguments*, not that Room or Retrofit work correctly.

## Test doubles — fakes vs mocks (official recommendation)

Per [official Android docs](https://developer.android.com/training/testing/fundamentals/test-doubles): **fakes are preferred**, mocks are second choice.

| Test Double | Definition | When to Use |
|---|---|---|
| **Fake** | Working implementation unsuitable for production (e.g., in-memory DB) | Preferred for data layer — lightweight, no framework |
| **Mock** | Programmed behavior with interaction verification | When you need to verify method calls, argument values, call counts |
| **Stub** | Programmed behavior without interaction expectations | Simple return values for dependencies |
| **Dummy** | Passed around but unused | Required constructor params you don't care about |
| **Spy** | Wrapper over real object that tracks interactions | Usually avoided — adds complexity. Fakes or mocks preferred |

> **MeApp convention**: We use **MockK mocks** (not fakes) for all dependencies. This is a deliberate choice — mocks let us verify interaction contracts (which methods called, with what args, how many times) which is critical for services that orchestrate multiple repositories. Fakes would be appropriate for data-layer-only tests or integration tests.

## TestDispatcher types — when to use each

Per [official Android docs](https://developer.android.com/kotlin/coroutines/test): **`StandardTestDispatcher` is recommended** for production-like behavior. `UnconfinedTestDispatcher` is simpler but less realistic.

| Dispatcher | Behavior | Use When | Official Recommendation |
|---|---|---|---|
| `StandardTestDispatcher` | Queues coroutines; you must call `advanceUntilIdle()` | **Official default** — `runTest` uses this. Production-like behavior, precise control | Preferred for reliable tests |
| `UnconfinedTestDispatcher` | Executes coroutines eagerly on current thread | Simpler tests without complex concurrency; quick prototyping | Simpler but less production-like |

> **MeApp convention**: Our `MainDispatcherRule` uses `UnconfinedTestDispatcher()` for simplicity — less boilerplate since coroutines start eagerly. This is a deliberate trade-off. When injecting `ioDispatcher` into services, pass `mainDispatcherRule.dispatcher` so all coroutines run on the test thread.

**Caution with `UnconfinedTestDispatcher`**: It starts coroutines eagerly but does NOT run them to completion if they suspend. If a coroutine hits `delay()` or another suspension point, other coroutines resume first. Use `StandardTestDispatcher` when testing interleaved execution.

**Caution with `StandardTestDispatcher`** (the `runTest` default): Forgetting to call `advanceUntilIdle()` is the #1 pitfall — `launch` blocks won't execute without it. Always yield before assertions.

## TestCoroutineScheduler sharing — CRITICAL rule

Per [official coroutine testing docs](https://developer.android.com/kotlin/coroutines/test): **only ONE `TestCoroutineScheduler` per test**. All `TestDispatcher` instances must share the same scheduler.

```kotlin
// ❌ WRONG — creates two independent schedulers that don't coordinate
val dispatcher1 = StandardTestDispatcher()       // scheduler #1
val dispatcher2 = StandardTestDispatcher()       // scheduler #2

// ✅ CORRECT — share a single scheduler
val dispatcher1 = StandardTestDispatcher(testScheduler)
val dispatcher2 = StandardTestDispatcher(testScheduler)
```

> **When `MainDispatcherRule` is active**: Any newly-created `TestDispatcher` automatically uses the scheduler from the replaced `Main` dispatcher. So in our tests, `StandardTestDispatcher()` without explicit scheduler is fine — it inherits from Main.

## Dispatcher injection — official recommendation

Per [official coroutine testing docs](https://developer.android.com/kotlin/coroutines/test): **always inject `CoroutineDispatcher`** as a constructor parameter rather than hardcoding `Dispatchers.IO` or `Dispatchers.Default`.

```kotlin
// ✅ Production code — injectable dispatcher with default
class GoalService @Inject constructor(
    private val goalRepository: IGoalRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        CoroutineScope(ioDispatcher).launch { /* ... */ }
    }
}

// ✅ Test code — pass test dispatcher
private fun createService() = GoalService(
    goalRepository = goalRepository,
    ioDispatcher = mainDispatcherRule.dispatcher,
)
```

> This eliminates `Thread.sleep()` entirely — `advanceUntilIdle()` controls all dispatched coroutines deterministically.

## CoroutineScope injection — when the class manages coroutines

When a class creates multiple coroutines (not just uses `suspend`), inject the full `CoroutineScope`:

```kotlin
// ✅ Production code — injectable scope
class UserState(
    private val userRepository: IUserRepository,
    private val scope: CoroutineScope,
) {
    fun registerUser(name: String) {
        scope.launch { userRepository.register(name) }
    }
}

// ✅ Test code — inject TestScope directly
@Test
fun `registerUser calls repository`() = runTest {
    val userState = UserState(userRepository, scope = this)  // TestScope
    userState.registerUser("Alice")
    advanceUntilIdle()
    coVerify { userRepository.register("Alice") }
}
```

> For coroutines that should auto-cancel at test end, inject `backgroundScope` instead of `this` (TestScope). `backgroundScope` is automatically cancelled before `runTest` finishes.

## Virtual time control — three methods

| Method | What it does | When to use |
|---|---|---|
| `advanceUntilIdle()` | Runs ALL queued coroutines until nothing remains | **Default choice** — after `launch`, `handleIntent`, any async trigger |
| `advanceTimeBy(ms)` | Advances virtual clock by N ms, runs coroutines scheduled before that point | Testing `delay()`-based logic, debounce, throttle |
| `runCurrent()` | Runs only coroutines scheduled at the **current** virtual time | Fine-grained control — rarely needed |

```kotlin
// advanceTimeBy example — testing debounce
@Test
fun `debounced search waits 500ms`() = runTest {
    service.onSearchTextChanged("query")
    advanceTimeBy(499)
    coVerify(exactly = 0) { repository.search(any()) }  // not yet
    advanceTimeBy(1)
    coVerify(exactly = 1) { repository.search("query") }  // fires at 500ms
}

// runCurrent — only runs what's due at virtual time 0
@Test
fun `runCurrent only processes immediate tasks`() = runTest {
    launch { /* runs immediately */ }
    launch { delay(100); /* runs later */ }
    runCurrent()  // only the first launch executes
}
```

## Flow testing — official patterns

**Testing output flows** (exposed by the class under test):

| Method | Use Case |
|---|---|
| `flow.test { awaitItem(); awaitComplete() }` | Finite flow (flowOf) — Turbine |
| `flow.test { awaitItem(); cancelAndIgnoreRemainingEvents() }` | Infinite flow (Room/StateFlow) — Turbine |
| `stateFlow.value` | Direct StateFlow value assertion (no Turbine needed) |
| `flow.first()` | Assert only the first emission (no Turbine needed) |

**StateFlow conflation awareness**: StateFlow is **conflated** — rapid emissions may skip intermediate values. Only the latest value is guaranteed.

```kotlin
// ❌ WRONG — intermediate values may be lost to conflation
stateFlow.test {
    assertThat(awaitItem()).isEqualTo(0)     // initial
    assertThat(awaitItem()).isEqualTo(1)     // may never arrive
    assertThat(awaitItem()).isEqualTo(2)     // may never arrive
    assertThat(awaitItem()).isEqualTo(3)     // only this is guaranteed
}

// ✅ CORRECT — assert on .value for current state
assertThat(viewModel.state.value.score).isEqualTo(3)

// ✅ CORRECT — use expectMostRecentItem() for final state
stateFlow.test {
    skipItems(1)  // initial
    service.triggerUpdates()
    val latest = expectMostRecentItem()
    assertThat(latest.score).isEqualTo(3)
}
```

> **Rule**: Use `stateFlow.value` for final-state assertions. Use Turbine `.test {}` only when verifying intermediate emissions or the sequence of state transitions.

**Activating lazy SharedFlows** (`SharingStarted.WhileSubscribed` / `Lazily`):
```kotlin
// Must create a collector BEFORE asserting — otherwise flow never activates
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
    viewModel.uiState.collect {}
}
assertThat(viewModel.uiState.value).isEqualTo(expected)
```

**`backgroundScope` for non-completing flows**: Regular `launch` inside `runTest` will make the test hang forever if the flow never completes. Always use `backgroundScope` — it's auto-cancelled when the test finishes.

```kotlin
// ❌ WRONG — test hangs because toList() never returns on infinite flow
launch { repository.observeUpdates().toList(values) }

// ✅ CORRECT — backgroundScope auto-cancels at test end
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
    repository.observeUpdates().toList(values)
}
```

**All Turbine events must be consumed** before `test {}` exits, or the test fails. Always end with `awaitComplete()` or `cancelAndIgnoreRemainingEvents()`.

**Testing multiple flows simultaneously** — use `turbineScope`:

When a test needs to observe multiple flows at once (e.g., a ViewModel's state + a shared event flow), use `turbineScope {}` with named turbines instead of nested `.test {}` blocks:

```kotlin
@Test
fun `action updates state and emits event`() = runTest {
    turbineScope {
        val stateTurbine = viewModel.state.testIn(this)
        val eventTurbine = viewModel.events.testIn(this)

        // Consume initial state
        assertThat(stateTurbine.awaitItem().isLoading).isFalse()

        viewModel.handleIntent(SomeIntent.DoAction)
        advanceUntilIdle()

        // Assert state change
        assertThat(stateTurbine.awaitItem().isLoading).isTrue()

        // Assert event emission
        assertThat(eventTurbine.awaitItem()).isEqualTo(SomeEvent.ActionCompleted)

        stateTurbine.cancelAndIgnoreRemainingEvents()
        eventTurbine.cancelAndIgnoreRemainingEvents()
    }
}
```

> **Rule**: Use `turbineScope` + `testIn(this)` when testing 2+ flows. Each turbine must be individually cancelled. Never nest `.test {}` blocks — they create independent scopes that don't coordinate.

**Hot flow vs cold flow testing**:

| Flow Type | Examples | Test Approach |
|---|---|---|
| **Cold flow** | `flow { emit() }`, `flowOf()` | `toList()` or Turbine `.test {}` directly |
| **Hot flow** | `StateFlow`, `SharedFlow`, `stateIn` | Requires active collector; use `backgroundScope` for infinite flows |

## Room DAO testing — mock vs in-memory DB

| Approach | When | Speed | Our Convention |
|---|---|---|---|
| **Mock the DAO** | Testing Repository/Service that *uses* a DAO | Fast (JVM) | **Default for MeApp** |
| **In-memory Room DB** | Testing DAO queries/migrations directly | Slower (instrumented) | Only for DAO-specific tests |

> For MeApp repository tests: always mock the DAO with MockK. We test that the repository calls the right DAO methods with correct arguments — we don't test that Room SQL works correctly (that's Room's responsibility).

## Retrofit API testing — mock vs MockWebServer

| Approach | When | Speed | Our Convention |
|---|---|---|---|
| **Mock the API interface** | Testing Repository that *calls* APIs | Fast (JVM) | **Default for MeApp** |
| **MockWebServer** | Testing serialization, HTTP headers, URL paths | Slower | Only for API definition tests |

> For MeApp repository tests: mock the Retrofit API interface with MockK. We verify the repository sends correct request objects and handles responses/errors properly — we don't test JSON serialization (that's Retrofit/Moshi's responsibility).

## MockK coroutine API — complete reference

The coroutine-aware API mirrors the regular API. **Always use the `co*` variant for suspend functions.**

| Regular (non-suspend) | Coroutine (suspend) | Purpose |
|---|---|---|
| `every { }` | `coEvery { }` | Stub behavior |
| `verify { }` | `coVerify { }` | Verify calls |
| `just Runs` | `coJustRun { }` | Stub Unit-returning functions |
| `answers { }` | `coAnswers { }` | Custom answer (can call suspend fns inside) |
| `verifyOrder { }` | `coVerifyOrder { }` | Ordered verification |
| `verifySequence { }` | `coVerifySequence { }` | Strict sequence (no other calls between) |
| `verifyAll { }` | `coVerifyAll { }` | Unordered exhaustive verification |
| — | `coJustAwait { }` | Stub suspend function that never returns (for timeout testing) |

```kotlin
// coJustRun — cleaner than coEvery { } just Runs
coJustRun { api.deleteGoal(any()) }

// coAnswers — custom answer with suspend support
coEvery { api.fetchGoal(any()) } coAnswers {
    delay(100)  // suspend call is legal inside coAnswers
    Goal(goalWeight = firstArg())
}

// returnsMany — different values on successive calls
coEvery { api.fetchGoal(any()) } returnsMany listOf(goal1, goal2, goal3)
```

## Anti-patterns to avoid (from official docs)

| Anti-Pattern | Why It's Bad | Do Instead |
|---|---|---|
| **Testing private methods directly** | Brittle, breaks encapsulation | Test through public API |
| **`relaxed = true` everywhere** | Hides errors by returning defaults silently | Use strict mocks; `relaxUnitFun = true` for DAOs |
| **`relaxed = true` with generic returns** | `ClassCastException` at runtime on `List<T>`, `Map<K,V>` etc. | Always stub generic-returning methods explicitly, even on relaxed mocks |
| **`unitTests.returnDefaultValues = true`** in Gradle | Masks failures by returning null/zero | Let it fail to catch missing stubs |
| **Not clearing mocks** | Test pollution — stubs leak between tests | `clearAllMocks()` in `@After` |
| **Using real dispatchers** | Flaky, timing-dependent failures | Inject test dispatcher |
| **Over-mocking** | When everything is mocked, you test nothing | Use real domain objects for data classes |
| **Ignoring error/edge cases** | Null, empty, zero, negative, max values | Test every branch including catch blocks |
| **`@Ignore` / `@Disabled` tests** | Rot and become permanently broken | Fix or delete them |
| **Multiple TestCoroutineSchedulers** | Dispatchers don't coordinate, virtual time diverges | Share `testScheduler` across all TestDispatchers |
| **`toList()` on infinite hot flows** | Test hangs forever | Use `backgroundScope` + continuous collection |
| **Asserting intermediate StateFlow values** | Conflation may skip them | Assert on `.value` or use `expectMostRecentItem()` |
| **Using `spyk` with suspend functions** | Gives unexpected results per MockK docs | Use `mockk` with explicit stubs instead of `spyk` for suspend fns |
| **Mocking inline functions** | MockK cannot mock Kotlin `inline` fns (fails fast since 1.14.6) | Extract behind a non-inline interface |
| **`mockkStatic` with JaCoCo enabled** | "class redefinition failed" error from instrumentation conflict | Wrap static call behind an interface and mock that instead |

## Truth assertion tips

Prefer whole-object comparison over field-by-field when possible:

```kotlin
// ✅ Preferred — concise, catches unexpected field changes
assertThat(result).isEqualTo(fakeAccount)

// ✅ Use field-level when you only care about specific fields
assertThat(result.id).isEqualTo("acc-1")
assertThat(result.isLoggedIn).isTrue()

// ✅ Collection assertions
assertThat(accounts).containsExactly(fakeAccount, fakeAccount2)
assertThat(accounts).hasSize(2)
assertThat(accounts).isEmpty()

// ✅ Type checking
assertThat(error).isInstanceOf(HttpException::class.java)
```

## MockK advanced patterns reference

```kotlin
// Mock Kotlin object (singleton)
mockkObject(GoalHelper)
every { GoalHelper.createGoal(any(), any(), any(), any(), any()) } throws RuntimeException("test")
// ... test ...
unmockkObject(GoalHelper)  // always clean up in @After

// Mock static/top-level function
mockkStatic(::topLevelFunction)
every { topLevelFunction(any()) } returns "mocked"

// Argument capture for complex verification
val slot = slot<ProfileUpdateRequest>()
coEvery { userAPI.updateProfile(capture(slot)) } returns fakeResponse
// ... act ...
assertThat(slot.captured.firstName).isEqualTo("Jane")

// Ordered verification (calls must happen in this order)
coVerifyOrder {
    accountDao.deactivateAllAccounts()
    accountDao.logoutAccount("acc-1")
    userDataStore.clearAccountTokens("acc-1")
}
```
