# Android unit tests — framework, isolation and shape

Project-tuned rules for Android unit tests (`app/src/test/**`, module `src/test/**`).

## Read the repo's testing guide first

Look for `docs/common/TESTING_STANDARD.md` (meApp) or an `Android/docs/` testing guide, and the test dependencies in `app/build.gradle.kts`. Where the guide disagrees with a rule here, the guide wins. No guide → only the JUnit 4 rule fires (it is a build fact, not a preference).

---

## P1 — JUnit 4 test in a JUnit Jupiter module (silently never runs)

meApp runs unit tests on the JUnit Platform with no Vintage engine. A JUnit 4 `@Test` compiles, is skipped, and reports nothing: the class looks covered and isn't.

```kotlin
import org.junit.Test          // JUnit 4
import org.junit.Before
import org.junit.Assert.assertEquals
```

**Sniff.** `+` lines `import org.junit.(Test|Before|After|Assert|Rule|runner)` in `src/test/**`. Before flagging, confirm the module has no `junit-vintage-engine` dependency (`rg "vintage" Android/**/build.gradle*`); if Vintage is present, skip.

**Fix.** `import org.junit.jupiter.api.Test`, `@BeforeEach`/`@AfterEach`, `org.junit.jupiter.api.Assertions.*` (or Truth's `assertThat`).

---

## P1 — Real time or main dispatcher in a coroutine test

`Thread.sleep`, a real `delay`, or a ViewModel launched on `Dispatchers.Main` with no test dispatcher makes the test flaky on CI and slow everywhere.

**Sniff.** In changed test files: `Thread.sleep(`, `runBlocking {` around ViewModel code, or a ViewModel test with no `Dispatchers.setMain(` / `MainDispatcherRule` / `StandardTestDispatcher` / `UnconfinedTestDispatcher`.

**Fix.** `runTest { … }` with a test dispatcher set as Main, advance with `advanceUntilIdle()`, and collect flows with Turbine (`flow.test { awaitItem() }`).

---

## P2 — Reducer tested only through the ViewModel

Reducers are pure `(State, Intent) -> State?` functions. Testing them through a ViewModel adds coroutines and mocks to a check that needs neither.

**Sniff.** A PR that adds or changes an `IReducer` implementation with no direct test calling `reduce(` on it.

**Fix.** Test the reducer directly: given a state and an intent, assert the returned state.

---

## P2 — UI copy asserted with a literal

Copy lives in the feature `Strings` object (`LoginStrings.Title`). A literal in a test breaks on every wording change and catches nothing.

**Sniff.** `assertEquals("<sentence>", …)` / `assertThat(…).isEqualTo("<sentence>")` on a message, title or label.

**Fix.** Compare with the production constant: `assertThat(state.error).isEqualTo(LoginStrings.InvalidEmail)`.

---

## Nit — `// Arrange` / `// Act` / `// Assert` labels, or a ticket id in a test name

**Sniff.** `+` lines `//\s*(Arrange|Act|Assert|Given|When|Then)\b`; `[A-Z]{2,6}-\d+` in a backtick test name or `@DisplayName`.

**Fix.** Separate the three parts with a blank line. Name by behaviour: `` `login success emits LoggedIn` ``, `` `login API failure shows error` ``.
