---
name: android-service-test-writer
description: Generates comprehensive MockK unit tests for an Android service class following the MeApp testing pattern. Use when given a service file path and asked to write unit tests for it.
---

<objective>
Read an Android service class and generate a complete, passing unit test file following the MeApp testing conventions:
- MockK for all mocking (no Mockito)
- Truth for assertions
- runTest for suspend functions
- Turbine for Flow testing
- MainDispatcherRule for coroutine dispatcher control
- Tests grouped by method with clear section comments
- 30–50 tests covering all public methods, edge cases, error paths, and dialog callbacks
- Target: 85%+ JaCoCo line coverage
</objective>

<quick_start>
1. Ask the user for the service file path if not provided in the arguments.
2. Read the service file.
3. Read BaseService.kt to understand inherited methods (isNetworkAvailable, dialogQueueService, appNavigationService).
4. Identify all public methods, their dependencies, and flow of control.
5. Generate the test file into `app/src/test/java/...` mirroring the source package.
6. Run `./gradlew :app:testDebugUnitTest --tests "*.{ServiceName}Test"` to verify.
7. Run `./gradlew :app:jacocoTestReport` and check coverage is above 85%.
</quick_start>

<process>
## Step 1: Read the source files

Read these files in parallel:
- The service file the user provided
- `app/src/main/java/com/dmdbrands/gurus/weight/core/service/BaseService.kt` — inherited behaviour
- The primary repository interface(s) the service depends on
- Any DataStore classes used (mock with `mockk(relaxed = true)`)

Key things to note while reading:
- Constructor parameters → these become your mocks
- Which methods call `isNetworkAvailable()` → mock `connectivityObserver.getCurrentNetworkState()`
- Which methods call `getCurrentGoal()` or combine flows → set up those flows in a helper
- Which methods are `suspend` → use `runTest`
- Which methods return `Flow<T>` → use Turbine `.test { }` block
- Which methods enqueue dialogs with lambdas → test callbacks using `slot<DialogModel>()` + `capture()`
- Private fields like `isShowingAlert` that gate behaviour → test the observable side effect (dialog enqueued or not)
- Exception `catch` blocks → write a test that triggers the exception to cover those lines

## Step 2: Plan the test groups

For each public method, list:
- Happy path(s)
- Sad/error path (exception → returns null, early return, no side effect)
- Network routing (online vs offline) if applicable
- Gating conditions (alert already shown, setup in progress, account null, etc.)
- Side effects to verify (DataStore writes, dialog enqueue, navigation)
- **Dialog callbacks** — if the method enqueues a `DialogModel.Confirm`, plan tests for `onConfirm`, `onCancel`, `onDismiss`
- **Exception catch blocks** — plan one test per try/catch that triggers the exception path

Target: minimum 4 tests per non-trivial method; 2 per simple pure function group.

## Step 3: Write the test file

### Required imports for dialog callback + advanceUntilIdle tests

```kotlin
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
```

Add `@OptIn(ExperimentalCoroutinesApi::class)` on the test class when using `advanceUntilIdle`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class {ServiceName}Test {
    ...
}
```

### Full file structure

```kotlin
package com.dmdbrands.gurus.weight.core.service   // mirror source package

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
// ... other imports as needed
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class {ServiceName}Test {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    // ... other mocks

    private lateinit var service: {ServiceName}

    // --- Test fixtures ---
    // Declare fakeAccount, fakeModel etc. here as val

    @Before
    fun setUp() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = true, unAvailable = false)
        every { connectivityObserver.observe() } returns flowOf(NetworkState(available = true, unAvailable = false))
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        setupCombinedFlows()

        service = {ServiceName}(/* all constructor args */)
    }

    /** Helper to wire combined flows (e.g. getCurrentGoal()). */
    private fun setupCombinedFlows(goal: SomeModel? = fakeModel) {
        every { accountRepository.getActiveAccountWeightUnitFlow() } returns flowOf(WeightUnit.LB)
        every { accountRepository.getActiveAccountWeightlessFlow() } returns flowOf(Weightless(false, 0f))
        every { repository.getCurrentItem() } returns flowOf(goal)
    }

    // -------------------------------------------------------------------------
    // {methodName} — description
    // -------------------------------------------------------------------------

    @Test
    fun `{methodName} {expected behaviour} when {condition}`() = runTest {
        // arrange
        // act
        // assert
    }
}
```

### Mocking rules

| Dependency type | Mock style | Why |
|---|---|---|
| Repository interfaces | `mockk()` strict | Catch unexpected calls |
| `IConnectivityObserver` | `mockk()` strict | Always stub explicitly |
| `GoalAlertDataStore` / other DataStores | `mockk(relaxed = true)` | Avoid stubbing every write |
| `IDialogQueueService` | `mockk(relaxed = true)` | enqueue() called with lambdas |
| `IAppNavigationService` | `mockk(relaxed = true)` | navigateTo() is fire-and-forget |
| `IDeviceService` | `mockk(relaxed = true)` | Boolean returns default to false |

### Suspend function stubs
```kotlin
coEvery { repo.fetchData() } returns result               // happy path
coEvery { repo.fetchData() } throws RuntimeException("err")  // error path
coEvery { dataStore.write(any()) } just Runs              // Unit-returning suspend function
```

### Flow stubs
```kotlin
every { repo.observeItems() } returns flowOf(item1, item2)
every { repo.observeItems() } returns flowOf(null)
```

### Turbine Flow assertions
```kotlin
service.getItemFlow().test {
    val first = awaitItem()
    assertThat(first).isNotNull()
    cancelAndIgnoreRemainingEvents()
}
```

### Network routing test pattern
```kotlin
@Test
fun `method calls online API when network available`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = true, unAvailable = false)
    coEvery { repo.updateOnline(any()) } returns fakeResult

    service.method(args)

    coVerify { repo.updateOnline(any()) }
    coVerify(exactly = 0) { repo.updateOffline(any()) }
}
```

### Gating condition test pattern
```kotlin
@Test
fun `method does nothing when alert already shown`() = runTest {
    coEvery { dataStore.hasShownAlert(any()) } returns true

    service.method()

    verify(exactly = 0) { dialogQueueService.enqueue(any()) }
}
```

### Dialog callback test pattern

When a method calls `dialogQueueService.enqueue(DialogModel.Confirm(...))`, the lambdas
passed as `onConfirm`/`onCancel`/`onDismiss` are NOT covered unless you capture and invoke them.

Use `slot<DialogModel>()` + `capture()` to capture the dialog, then invoke the callback:

```kotlin
/** Helper: trigger the method that shows the dialog and return the captured Confirm dialog. */
private suspend fun captureMyDialog(): DialogModel.Confirm {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    // ... set up mocks to trigger the dialog ...
    service.methodThatShowsDialog()
    return dialogSlot.captured as DialogModel.Confirm
}

@Test
fun `dialog onConfirm resets flag and calls repository`() = runTest {
    val dialog = captureMyDialog()

    dialog.onConfirm?.invoke()
    advanceUntilIdle()   // wait for CoroutineScope(Dispatchers.Main).launch { } to complete

    coVerify { dataStore.setAlertShown(fakeAccount.id, false) }
    coVerify { repo.updateSetting(any()) }
}

@Test
fun `dialog onCancel resets flag without calling repository`() = runTest {
    val dialog = captureMyDialog()

    dialog.onCancel?.invoke()
    advanceUntilIdle()

    coVerify { dataStore.setAlertShown(fakeAccount.id, false) }
    coVerify(exactly = 0) { repo.updateSetting(any()) }
}

@Test
fun `dialog onDismiss resets flag without calling repository`() = runTest {
    val dialog = captureMyDialog()

    dialog.onDismiss?.invoke()
    advanceUntilIdle()

    coVerify { dataStore.setAlertShown(fakeAccount.id, false) }
    coVerify(exactly = 0) { repo.updateSetting(any()) }
}
```

**Why `advanceUntilIdle()`?** Callbacks typically launch a new coroutine:
```kotlin
onConfirm = {
    CoroutineScope(Dispatchers.Main).launch {
        handleGoalMet(true)   // runs asynchronously
    }
}
```
`advanceUntilIdle()` drains all pending coroutines on the test dispatcher before you verify.
Requires `@OptIn(ExperimentalCoroutinesApi::class)` on the test class.

### Exception catch block test pattern

JaCoCo counts `catch` block lines as **uncovered** unless a test actually triggers the exception.
Each try/catch in the service needs one test that throws to cover those lines:

```kotlin
@Test
fun `method handles exception gracefully`() = runTest {
    // Make a dependency throw inside the try block
    coEvery { dataStore.hasShownAlert(any()) } throws RuntimeException("DataStore error")

    // Should NOT crash — exception is caught
    service.method()

    // Verify no unintended side effects occurred
    verify(exactly = 0) { dialogQueueService.enqueue(any()) }
}
```

For catching exceptions inside callbacks:
```kotlin
@Test
fun `callback handles exception gracefully`() = runTest {
    val dialog = captureMyDialog()
    // Make the call inside the callback throw
    coEvery { dataStore.setAlertShown(any(), false) } throws RuntimeException("write error")

    // Should NOT crash — exception is caught inside the private handler
    dialog.onConfirm?.invoke()
    advanceUntilIdle()
}
```

## Step 4: Place the file

```
Source:  app/src/main/java/com/dmdbrands/gurus/weight/core/service/FooService.kt
Test:    app/src/test/java/com/dmdbrands/gurus/weight/core/service/FooServiceTest.kt
```

## Step 5: Run tests and check coverage

```bash
# Run just this service's tests
./gradlew :app:testDebugUnitTest --tests "*.{ServiceName}Test"

# Generate JaCoCo HTML + XML report
./gradlew :app:jacocoTestReport
```

**Check test count** from XML result:
```bash
head -3 app/build/test-results/testDebugUnitTest/TEST-com.dmdbrands.gurus.weight.core.service.{ServiceName}Test.xml
# Expected: failures="0" errors="0"
```

**Check GoalService coverage** from XML:
```bash
python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml')
for cls in tree.getroot().iter('class'):
    if '{ServiceName}' in cls.get('name', '') and '\$' not in cls.get('name', ''):
        for c in cls.findall('counter'):
            if c.get('type') == 'LINE':
                missed, covered = int(c.get('missed')), int(c.get('covered'))
                print(f'{covered}/{missed+covered} lines = {round(covered/(missed+covered)*100)}%')
"
```

**Open HTML report** in browser:
```
file:///Users/rengadevi/work/meApp/Android/app/build/reports/jacoco/jacocoTestReport/html/com.dmdbrands.gurus.weight.core.service/{ServiceName}.kt.html
```

If coverage is below 85%:
1. Open the HTML report — red lines show exactly what is uncovered
2. Add exception tests to cover `catch` blocks
3. Add callback tests (`captureMyDialog()`) to cover lambda bodies
4. Check for dead-code branches (private methods never called with certain args) — these are acceptable to leave uncovered

**Troubleshooting compilation failures:**
- `Unresolved reference: io.mockk.Runs` → import `io.mockk.Runs` and `io.mockk.just`
- `This declaration needs opt-in` (advanceUntilIdle) → add `@OptIn(ExperimentalCoroutinesApi::class)` to the class
- DataStore class needs Android Context → mock the class directly with `mockk(relaxed = true)`
- Combined Flow test fails → ensure `setupCombinedFlows()` is called in `@Before`

**Troubleshooting runtime failures:**
- `io.mockk.MockKException: no answer found` → add a stub, or change mock to `mockk(relaxed = true)`
- `kotlinx.coroutines.test.UncompletedCoroutinesError` → add `cancelAndIgnoreRemainingEvents()` in Turbine block
- Coverage still 0% after tests pass → tests ran from cache; run `./gradlew :app:cleanTestDebugUnitTest :app:jacocoTestReport`

## JDK 17+ ByteBuddy agent warning

If tests print `WARNING: A Java agent has been loaded dynamically`, do NOT suppress it with `-XX:+EnableDynamicAgentLoading`. That hides the problem.

**The real fix** — pre-load ByteBuddy as a proper `-javaagent` at JVM startup. Add this to `app/build.gradle.kts` outside the `android {}` block, alongside the `jacocoTestReport` task:

```kotlin
// Pre-load ByteBuddy agent at JVM startup so MockK doesn't load it dynamically.
// Fixes "A Java agent has been loaded dynamically" warning on JDK 17+.
tasks.withType<Test> {
  doFirst {
    val agentJar = classpath.find { it.name.contains("byte-buddy-agent") }
    if (agentJar != null) {
      jvmArgs("-javaagent:$agentJar")
    }
  }
}
```

`doFirst` runs just before the test task, finds `byte-buddy-agent.jar` from MockK's transitive
dependencies on the test classpath, and registers it as a proper agent — no suppression, actually fixed.
</process>

<success_criteria>
android-service-test-writer is complete when:
- [ ] Test file compiles with no errors
- [ ] `./gradlew :app:testDebugUnitTest --tests "*.{ServiceName}Test"` reports BUILD SUCCESSFUL
- [ ] XML result shows `failures="0" errors="0"`
- [ ] All public methods have at least one test
- [ ] Suspend functions use `runTest`
- [ ] Flow methods use Turbine `.test { }` block
- [ ] Network routing (online/offline) is tested where applicable
- [ ] Exception `catch` blocks are covered by at least one test each
- [ ] Dialog callbacks (onConfirm/onCancel/onDismiss) are tested using `slot<DialogModel>()` + `capture()`
- [ ] JaCoCo line coverage for the service is 85% or above
- [ ] No Mockito imports — MockK only
</success_criteria>
