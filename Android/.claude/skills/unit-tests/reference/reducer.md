# Reducer Test Patterns

These patterns apply when testing classes named `*Reducer.kt` that implement `IReducer`.

Reducers are **pure functions** — the simplest class to test. No mocks, no coroutines, no `MainDispatcherRule`, no `runTest`. Just create state -> call `reduce()` -> assert new state.

## What to read

- The Reducer source file (e.g., `SettingsReducer.kt`) — contains State, Intent, and `reduce()` function
- The `IReducer` interface at `domain/interfaces/IReducer.kt` — defines `reduce(state: State, intent: Intent): State?`
- The State data class — know all fields and defaults
- The Intent sealed interface — know all variants

## Test structure

```kotlin
package com.dmdbrands.gurus.weight.features.{feature}.viewmodel

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class {Feature}ReducerTest {

    private lateinit var reducer: {Feature}Reducer

    @Before
    fun setUp() {
        reducer = {Feature}Reducer()
    }

    // -------------------------------------------------------------------------
    // {IntentName}
    // -------------------------------------------------------------------------

    @Test
    fun `SetError sets errorMessage and clears isLoading`() {
        val state = {Feature}State(isLoading = true, errorMessage = null)
        val result = reducer.reduce(state, {Feature}Intent.SetError("Network failure"))
        assertThat(result?.errorMessage).isEqualTo("Network failure")
        assertThat(result?.isLoading).isFalse()
    }
}
```

> **No `@OptIn`, no `runTest`, no `MainDispatcherRule`** — reducers are synchronous pure functions.

## Test patterns

### Pattern A: State mutation — verify ALL changed fields

Assert every changed field AND verify unchanged fields remain:

```kotlin
@Test
fun `SetHistoryItems stores items and clears loading and error`() {
    val state = HistoryState(isLoading = true, errorMessage = "stale error")
    val items = listOf(mockk<HistoryMonth>(relaxed = true))

    val result = reducer.reduce(state, HistoryIntent.SetHistoryItems(items))

    assertThat(result?.historyItems).containsExactlyElementsIn(items).inOrder()
    assertThat(result?.isLoading).isFalse()
    assertThat(result?.errorMessage).isNull()
}
```

### Pattern B: Side-effect-only intents — must return null

Intents handled only in the ViewModel (navigation, dialogs, API calls) must return `null`:

```kotlin
@Test
fun `Logout returns null — handled as side effect in ViewModel`() {
    assertThat(reducer.reduce(SettingsState(), SettingsIntent.Logout)).isNull()
}
```

> **Why test null returns?** Documents intent behavior and catches regressions if someone accidentally adds state changes.

### Pattern C: Data-carrying intents — verify payload mapping

```kotlin
@Test
fun `UpdateThemeMode sets currentThemeMode from intent value`() {
    val result = reducer.reduce(
        SettingsState(currentThemeMode = "System Settings"),
        SettingsIntent.UpdateThemeMode("Dark"),
    )
    assertThat(result?.currentThemeMode).isEqualTo("Dark")
}
```

### Pattern D: Boolean toggle intents

```kotlin
@Test
fun `SetLoading true sets isLoading to true`() {
    val result = reducer.reduce(HistoryState(isLoading = false), HistoryIntent.SetLoading(true))
    assertThat(result?.isLoading).isTrue()
}

@Test
fun `SetLoading false sets isLoading to false`() {
    val result = reducer.reduce(HistoryState(isLoading = true), HistoryIntent.SetLoading(false))
    assertThat(result?.isLoading).isFalse()
}
```

### Pattern E: State preservation

Verify intents don't wipe unrelated fields:

```kotlin
@Test
fun `SetError preserves existing account data`() {
    val fakeAccount = Account(id = "acc-1" /* ... */)
    val state = SettingsState(account = fakeAccount, isLoading = true)

    val result = reducer.reduce(state, SettingsIntent.SetError("failed"))

    assertThat(result?.account).isEqualTo(fakeAccount)  // preserved
    assertThat(result?.errorMessage).isEqualTo("failed") // changed
}
```

### Pattern F: Complex model fixtures with mockk

When testing state structure, not model internals:

```kotlin
private val itemA: HistoryMonth = mockk(relaxed = true)

@Test
fun `Retry sets isLoading without clearing existing items`() {
    val state = HistoryState(historyItems = listOf(itemA), isLoading = false)
    val result = reducer.reduce(state, HistoryIntent.Retry)
    assertThat(result?.isLoading).isTrue()
    assertThat(result?.historyItems).containsExactly(itemA)
}
```

## Coverage checklist

1. **One test per `when` branch** in `reduce()` — each Intent variant
2. **State mutation intents**: assert changed fields AND one preserved field
3. **Side-effect intents**: assert returns `null`
4. **Data-carrying intents**: assert payload maps to correct state field
5. **Boolean toggles**: test both `true` and `false`
6. **`else` branch** (if present): test with an unhandled intent

## Reducer-specific success criteria

- [ ] No `MainDispatcherRule`, no `runTest`, no mocks needed (pure function)
- [ ] One test per `when` branch in `reduce()`
- [ ] State mutation intents: assert changed fields AND at least one preserved field
- [ ] Side-effect-only intents: assert returns `null`
- [ ] Data-carrying intents: assert payload maps to correct state field
- [ ] Boolean toggles: test both `true` and `false`
- [ ] `else` / default branch tested
