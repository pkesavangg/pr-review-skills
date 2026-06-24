# Troubleshooting

| Error | Fix |
|---|---|
| `no answer found for: {Mock}.method()` | Add stub: `coEvery { mock.method() } returns value` or use `mockk(relaxed = true)` |
| `UncompletedCoroutinesError` | Add `cancelAndIgnoreRemainingEvents()` in Turbine `.test { }` block |
| `advanceUntilIdle opt-in` | Add `@OptIn(ExperimentalCoroutinesApi::class)` on the class |
| Coverage still 0% | Run `./gradlew cleanTestDebugUnitTest :app:jacocoTestReport` |
| `Unresolved reference: Runs` | Import `io.mockk.Runs` AND `io.mockk.just` (both needed) |
| `Unresolved reference: httpException` | Import `com.dmdbrands.gurus.weight.core.helpers.httpException` from shared TestHelpers |
| `assertThrows` not catching suspend exception | Wrap in `runBlocking`: `assertThrows(X::class.java) { runBlocking { ... } }` |
| `Unresolved reference: HttpException` | Import `retrofit2.HttpException` |
| `Unresolved reference: Response` | Import `retrofit2.Response` |
| `Unresolved reference: runBlocking` | Import `kotlinx.coroutines.runBlocking` |
| `Unresolved reference: flow` | Import `kotlinx.coroutines.flow.flow` |
| `Type mismatch: expected X got Unit` | Check if the mock returns the right type; suspend funs need `coEvery` not `every` |
| Init block flow not collected | Stub the flow in `@Before` BEFORE calling `createService()` |
| Test passes but coverage doesn't increase | Ensure JaCoCo XML is being regenerated: run `jacocoTestReport` after `testDebugUnitTest` |
| `Thread.sleep` in tests | Replace with `advanceUntilIdle()` — requires injecting `ioDispatcher` if service creates its own `CoroutineScope(Dispatchers.IO)` |
| Hilt `CoroutineDispatcher cannot be provided` after adding `ioDispatcher` | A class injects the concrete service type instead of the interface — change to use `IFooService` |
| Turbine test hangs/times out | Flow not emitting — check stubs. Add `cancelAndIgnoreRemainingEvents()` for infinite flows |
| Phantom "missed branch" in fully-tested method | Likely Kotlin compiler artifact — upgrade JaCoCo to 0.8.14+ (see false positives section) |
| `every` used for suspend function — silently fails | Use `coEvery` for suspend functions, `every` for non-suspend only |
| Mock returns default instead of stubbed value | Verify `relaxed = true` isn't hiding a missing stub — prefer strict mocks or `relaxUnitFun = true` |
| `coVerifyOrder` fails unexpectedly | Ensure ALL calls in the order block actually happen — missing calls cause order verification to fail |
| StateFlow/SharedFlow test hangs | For `SharingStarted.WhileSubscribed`, launch a collector in `backgroundScope` before asserting |
| Turbine test fails with "Unconsumed events" | Must consume ALL emissions — end with `awaitComplete()` or `cancelAndIgnoreRemainingEvents()` |
| `mockkObject` leaks between tests | Always call `unmockkObject(ObjectName)` in `@After` when using `mockkObject` |
| Multiple schedulers cause desync | All `TestDispatcher` instances in one test must share `testScheduler` — never create independent schedulers |
| `toList()` hangs on infinite flow | Use `backgroundScope.launch { flow.toList(values) }` — auto-cancels at test end |
| Turbine misses StateFlow emissions | StateFlow is conflated — use `.value` for current state or `expectMostRecentItem()` for latest |
| `runCurrent()` doesn't run delayed coroutines | `runCurrent()` only runs coroutines at current virtual time — use `advanceTimeBy()` or `advanceUntilIdle()` for delayed work |
| `UnconfinedTestDispatcher` doesn't complete after suspend | Eager start ≠ eager completion — coroutine pauses at suspension points, other coroutines resume |
| `stateIn` flow never activates in test | `WhileSubscribed`/`Lazily` flows need a collector — add `backgroundScope.launch { flow.collect {} }` |
| Nested `.test {}` blocks don't coordinate | Use `turbineScope {}` + `testIn(this)` for multiple flows — each turbine must be cancelled individually |
| `runTest` times out after 60 seconds | Default `runTest` timeout is 60s. Check for uncompleted coroutines, missing `advanceUntilIdle()`, or infinite loops. Use `runTest(timeout = 10.seconds)` to fail faster during debugging |
| `Turbine can only collect flows within a TurbineContext` | `testIn()` called outside `turbineScope {}` or `.test {}` — wrap with `turbineScope { }` (required since Turbine 1.0.0) |
| `class redefinition failed` with `mockkStatic` | JaCoCo instrumentation conflicts with MockK's class rewriting — wrap the static call behind an interface and mock that instead |
| `ClassCastException` from relaxed mock with generic return | `relaxed = true` returns nested mocks for generics (`List<T>`, `Map<K,V>`) — explicitly stub generic-returning methods even on relaxed mocks |
| `spyk` gives wrong results for suspend functions | MockK docs warn spies + suspend = unexpected behavior — use `mockk()` with explicit stubs instead of `spyk` for suspend functions |

## Instrumented test errors (androidTest/)

| Error | Fix |
|---|---|
| `No connected devices` | Start an emulator: `emulator -avd <name>` or use Android Studio device manager |
| `IllegalStateException: Cannot access database on main thread` | Ensure `allowMainThreadQueries()` is set — `BaseDaoTest` handles this |
| `SQLiteConstraintException: FOREIGN KEY constraint failed` | Insert parent entities before children (FK order) — use `insertFullAccount()` |
| `SQLiteConstraintException: UNIQUE constraint failed` | Use unique values (especially `email`) for each inserted entity |
| `ComposeNotIdleException` | Add `composeTestRule.waitForIdle()` or `waitUntil { }` for async operations |
| `AssertionError: Expected node count: 1 but found: 0` | Check text spelling, use `substring = true`, or `useUnmergedTree = true`. Debug with `onRoot().printToLog("TAG")` |
| `AssertionError: Expected node count: 1 but found: 2+` | Use `onAllNodesWithText()` and pick specific node, or add `Modifier.testTag()` for uniqueness |
| `IllegalStateException: No compose views found` | Ensure `debugImplementation(libs.androidx.ui.test.manifest)` is in `build.gradle.kts` |
| `No Activity set` | Use `createComposeRule()` (needs `ui-test-manifest`) or `createAndroidComposeRule<Activity>()` |
| `Theme tokens not resolving / crash in theme` | Wrap content in `MeAppTheme { }` in the `setContent()` helper |
| `Test passes locally but fails on CI` | Check emulator API level matches local. Use `@Suppress("DEPRECATION")` for API-specific behavior |
| `connectedDebugAndroidTest task hangs` | Emulator may be stuck — restart emulator or use `./gradlew --stop` then retry |
| `Turbine not available in androidTest` | Known version conflict with compose-ui-test — use `flow.first()` instead of Turbine in androidTest |
| `Room DB schema mismatch` | Run `./gradlew clean` then rebuild — may need to clear emulator app data |
| `Test order dependency` | Each test gets a fresh DB (BaseDaoTest creates new in @Before). If still failing, check for leaked state in companion objects |
