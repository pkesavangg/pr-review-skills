# Android Testing Infrastructure


## Quick Start — The 3 Commands You Need

Run these from the root of the Android project (`/Android`):

```bash
# 1. Run all unit tests across all 7 modules
./gradlew testDebugUnitTest

# 2. Generate JaCoCo HTML + XML coverage reports
./gradlew jacocoTestReport

# 3. Confirm the project still builds cleanly
./gradlew assembleDebug
```
## Overview

This document covers the unit test setup for the Android app — dependencies, project structure, how to run tests, and how to generate coverage reports.

The stack uses **MockK** (mocking), **Turbine** (Flow testing), **kotlinx-coroutines-test** (coroutine/dispatcher control), **Truth** (assertions), and **MockWebServer** (API response parsing), all configured for JUnit 4.

---

## Dependencies

All versions are managed in `gradle/libs.versions.toml`.

| Library | Version | Purpose |
|---|---|---|
| `junit:junit` | 4.13.2 | Test runner and lifecycle annotations |
| `io.mockk:mockk` | 1.13.17 | Kotlin-first mocking library |
| `app.cash.turbine:turbine` | 1.2.0 | Testing Kotlin Flow emissions |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 | Test dispatcher for coroutine-based code |
| `com.google.truth:truth` | 1.4.4 | Fluent assertion library |
| `com.squareup.okhttp3:mockwebserver` | 4.12.0 | Local HTTP server for API response parsing tests |

Declared in `app/build.gradle.kts`:

```kotlin
// Unit test dependencies
testImplementation(libs.junit.jupiter)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
testImplementation(libs.truth)
testImplementation(libs.mockwebserver)

// Compose UI test dependencies (instrumented)
androidTestImplementation(libs.androidx.ui.test.junit4)
debugImplementation(libs.androidx.ui.test.manifest)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.androidx.junit)
```

---

## Test Directory Structure

Unit tests mirror the source package structure exactly:

```
app/src/
├── main/java/com/dmdbrands/gurus/weight/
│   ├── core/
│   ├── data/
│   ├── domain/
│   └── features/
│       └── settings/
│           └── viewmodel/
│               └── SettingsReducer.kt          ← production code
│
└── test/java/com/dmdbrands/gurus/weight/       ← unit tests (JVM)
    ├── core/
    │   └── rules/
    │       └── MainDispatcherRule.kt            ← shared test utility
    └── features/
        └── settings/
            └── viewmodel/
                └── SettingsReducerTest.kt       ← smoke test
```

Instrumented tests (requiring a device/emulator) live under `src/androidTest/`:

```
app/src/androidTest/java/com/dmdbrands/gurus/weight/
└── MainBottomNavTest.kt    ← Compose UI tests (needs device/emulator)
```

**Rule:** pure logic (reducers, use cases, mappers, utilities) → `src/test/`. Anything that needs an Android context or Compose → `src/androidTest/`.

---

## Shared Test Utilities

### `MainDispatcherRule`

**Location:** `app/src/test/java/com/dmdbrands/gurus/weight/core/rules/MainDispatcherRule.kt`

Replaces `Dispatchers.Main` with a `TestDispatcher` for the duration of each test, which allows ViewModels and coroutine-based classes to be tested on the JVM without hanging or crashing.

```kotlin
// Use in any test class that involves ViewModels or suspend functions
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

The rule automatically:
1. Installs a `UnconfinedTestDispatcher` as `Dispatchers.Main` before each test
2. Resets `Dispatchers.Main` after each test finishes

---

## Running Tests

All commands run from the `Android/` directory.

### Run all unit tests (debug variant)

```bash
./gradlew testDebugUnitTest
```

### Run unit tests for a specific module

```bash
# Main app module only
./gradlew :app:testDebugUnitTest

# A sub-module (replace with actual module name)
./gradlew :notification:testDebugUnitTest
```

### Run a single test class

```bash
./gradlew :app:testDebugUnitTest --tests "com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsReducerTest"
```

### Run a single test method

```bash
./gradlew :app:testDebugUnitTest --tests "com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsReducerTest.LoadSettings sets isLoading to true"
```

### Run with continuous mode (re-runs on file change)

```bash
./gradlew :app:testDebugUnitTest --continuous
```

### Run instrumented tests (requires connected device or emulator)

```bash
./gradlew :app:connectedAndroidTest
```

---

## Test Results

After running tests, HTML and XML results are written to:

```
app/build/reports/tests/testDebugUnitTest/index.html   ← open in browser
app/build/test-results/testDebugUnitTest/*.xml          ← CI-parseable XML
```

---

## Code Coverage (JaCoCo)

Coverage is configured in `app/build.gradle.kts`. It requires the debug build type and is gated behind `enableUnitTestCoverage = true`.

### Generate the coverage report

```bash
# This also runs the unit tests first (jacocoTestReport depends on testDebugUnitTest)
./gradlew :app:jacocoTestReport
```

### View the reports

| Format | Path |
|---|---|
| HTML (browser) | `app/build/reports/jacoco/jacocoTestReport/html/index.html` |
| XML (CI/SonarQube) | `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` |

Open the HTML report by navigating to the path above in any browser.

### What is excluded from coverage

The JaCoCo configuration excludes generated and framework code that is not meaningful to measure:

| Exclusion | Reason |
|---|---|
| `**/R.class`, `**/R$*.class` | Android resource identifiers — generated |
| `**/BuildConfig.*` | Build-time constants — generated |
| `**/Manifest*.*` | Android manifest — generated |
| `**/hilt_aggregated_deps/**` | Hilt DI graph — generated |
| `**/*_HiltComponents*`, `**/*_HiltModules*` | Hilt DI — generated |
| `**/Hilt_*` | Hilt activity/fragment injectors — generated |
| `**/*_MembersInjector*`, `**/*_Factory*` | Hilt/Dagger factories — generated |
| `**/*_Impl*` | Room DAO implementations — generated |
| `**/*ComposableSingletons*` | Compose compiler output — generated |
| `**/*OuterClass*` | Protobuf generated classes |

---

## Writing Tests

### Plain unit test (no coroutines, no mocks)

Use this pattern for reducers, mappers, validators, and any pure function.

```kotlin
class MyReducerTest {

    private lateinit var reducer: MyReducer

    @Before
    fun setUp() {
        reducer = MyReducer()
    }

    @Test
    fun `some intent updates state correctly`() {
        val state = MyState()

        val result = reducer.reduce(state, MyIntent.DoSomething)

        assertThat(result?.someField).isEqualTo(expectedValue)
    }
}
```

### Test with coroutines and a ViewModel

```kotlin
class MyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val myService: IMyService = mockk()
    private lateinit var viewModel: MyViewModel

    @Before
    fun setUp() {
        viewModel = MyViewModel(myService)
    }

    @Test
    fun `loading data updates state`() = runTest {
        coEvery { myService.getData() } returns listOf(item1, item2)

        viewModel.handleIntent(MyIntent.Load)

        assertThat(viewModel.state.value.items).hasSize(2)
    }
}
```

### Testing a Flow with Turbine

```kotlin
@Test
fun `flow emits expected values`() = runTest {
    val flow = myRepository.observeItems()

    flow.test {
        val first = awaitItem()
        assertThat(first).isNotEmpty()
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Mocking with MockK

```kotlin
// Create a mock
val myRepo: IMyRepository = mockk()

// Stub a suspend function
coEvery { myRepo.fetch() } returns Result.success(data)

// Stub a regular function
every { myRepo.getCached() } returns cachedData

// Verify a call happened
coVerify { myRepo.fetch() }

// Verify a call did NOT happen
verify(exactly = 0) { myRepo.save(any()) }
```

---

## Smoke Test

`SettingsReducerTest` is the project's primary smoke test. It verifies the test infrastructure is correctly wired up and covers all reducer branches in `SettingsReducer`.

**Location:** `app/src/test/java/com/dmdbrands/gurus/weight/features/settings/viewmodel/SettingsReducerTest.kt`

Run it directly:

```bash
./gradlew :app:testDebugUnitTest --tests "*.SettingsReducerTest"
```

Expected output: **12 tests, 0 failures, 0 errors.**

---

## Types of Android Tests

### 1. Unit Tests (`src/test/`)
Runs on the **JVM on your machine**. No Android device needed.

- Tests pure logic — reducers, ViewModels, repositories
- Fast (milliseconds per test)
- Uses MockK to fake Android dependencies
- What JaCoCo measures for coverage

### 2. Instrumented Tests (`src/androidTest/`)
Runs on a **real Android device or emulator**. Requires `AndroidJUnitRunner`.

- Tests things that need real Android context — UI, navigation, database
- Slow (seconds per test)
- Compose UI tests and Espresso tests live here
- **Does NOT count toward JaCoCo coverage**

### 3. Integration Tests
Not a separate folder — a concept. Tests multiple layers working together (e.g. Repository + API + parsing). MockWebServer tests are integration-level but still run as unit tests on the JVM.

| Type | Runs on | Speed | Folder | Counts for JaCoCo |
|---|---|---|---|---|
| Unit test | JVM | Fast | `src/test/` | ✅ Yes |
| Instrumented test | Device/Emulator | Slow | `src/androidTest/` | ❌ No |
| Integration test | JVM | Fast | `src/test/` | ✅ Yes |

---

## API Response Parsing Tests (MockWebServer)

MockWebServer spins up a real local HTTP server, letting Retrofit actually parse JSON — the same as production. This catches issues MockK cannot.

### When to use MockK vs MockWebServer

| | MockK | MockWebServer |
|---|---|---|
| Tests business logic | ✅ | ✅ |
| Tests JSON field parsing | ❌ | ✅ |
| Tests HTTP error codes | ❌ | ✅ |
| Needs a device | ❌ | ❌ |

### Example

```kotlin
class MyApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: IMyAPI

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()

        api = retrofit.create(IMyAPI::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `endpoint returns correctly parsed response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{ "id": "123", "name": "John" }""")
        )

        val response = api.getUser()

        assertThat(response.id).isEqualTo("123")
        assertThat(response.name).isEqualTo("John")
    }
}
```

> If a JSON field name mismatches the data class, Gson silently returns `null` and the assertion fails — that's exactly the bug MockWebServer catches. Fix with `@SerializedName("field_name")`.

---

## Room / Database Testing

**Do NOT test Room DAO files directly.** Room DAO implementations (`*_Impl`) are already excluded from JaCoCo coverage and testing them adds no coverage value.

Instead, test the **repository that wraps the DAO** by mocking the DAO with MockK:

```kotlin
class EntryRepositoryTest {

    private val entryDao: EntryDao = mockk()
    private val repository = EntryRepository(entryDao)

    @Test
    fun `getEntries returns mapped domain models`() = runTest {
        coEvery { entryDao.getAll() } returns listOf(fakeEntryEntity)

        val result = repository.getEntries()

        assertThat(result).hasSize(1)
    }
}
```

---

## Recommended Test Writing Order (for coverage targets)

Writing tests in this order gives the fastest path to 80% coverage:

| Priority | Layer | Why |
|---|---|---|
| 1 | **Reducers** | Pure functions, zero mocks, fastest coverage gain |
| 2 | **ViewModels** | Biggest bang for buck — covers all business logic + state |
| 3 | **Repositories** | Medium complexity — mock DAO/API, no device needed |
| 4 | **Services** | Most complex — leave for last |
| Skip | **Room DAOs** | Excluded from JaCoCo, no coverage value |
| Skip (for now) | **Compose UI tests** | Instrumented — don't count toward JaCoCo |
