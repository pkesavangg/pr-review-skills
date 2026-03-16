# Reducer Test Patterns

These patterns apply when testing classes named `*Reducer.kt` that implement `IReducer`.

Reducers are **pure functions** — the simplest class to test. No mocks, no coroutines, no `MainDispatcherRule`, no `runTest`. Just create state → call `reduce()` → assert new state.

## Step 11.1: What to read for reducers

- The Reducer source file (e.g., `SettingsReducer.kt`) — contains State, Intent, and `reduce()` function
- The `IReducer` interface at `domain/interfaces/IReducer.kt` — defines `reduce(state: State, intent: Intent): State?`
- The State data class — know all fields and defaults
- The Intent sealed interface — know all variants

## Step 11.2: Reducer test structure

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

## Step 11.3: Reducer test patterns

### Pattern A: State mutation intents — verify ALL changed fields

For intents that modify state, assert every field that should change AND verify unchanged fields remain:

```kotlin
@Test
fun `SetHistoryItems stores items and clears loading and error`() {
    val state = HistoryState(isLoading = true, errorMessage = "stale error")
    val items = listOf(mockk<HistoryMonth>(relaxed = true), mockk<HistoryMonth>(relaxed = true))

    val result = reducer.reduce(state, HistoryIntent.SetHistoryItems(items))

    // Changed fields
    assertThat(result?.historyItems).containsExactlyElementsIn(items).inOrder()
    assertThat(result?.isLoading).isFalse()
    assertThat(result?.errorMessage).isNull()
}
```

### Pattern B: Side-effect-only intents — must return null

Intents handled only in the ViewModel (navigation, dialogs, API calls) must return `null` from the reducer:

```kotlin
@Test
fun `Logout returns null — handled as side effect in ViewModel`() {
    val state = SettingsState()

    val result = reducer.reduce(state, SettingsIntent.Logout)

    assertThat(result).isNull()
}

@Test
fun `OpenAddScales returns null — navigation side effect only`() {
    val state = SettingsState()

    assertThat(reducer.reduce(state, SettingsIntent.OpenAddScales)).isNull()
}
```

> **Why test null returns?** It documents intent behavior and catches regressions if someone accidentally adds state changes to a side-effect-only intent.

### Pattern C: Data-carrying intents — verify payload mapping

```kotlin
@Test
fun `UpdateThemeMode sets currentThemeMode from intent value`() {
    val state = SettingsState(currentThemeMode = "System Settings")

    val result = reducer.reduce(state, SettingsIntent.UpdateThemeMode("Dark"))

    assertThat(result?.currentThemeMode).isEqualTo("Dark")
}

@Test
fun `SetAccount stores account from intent`() {
    val fakeAccount = Account(id = "acc-1", firstName = "John", /* ... */)
    val state = SettingsState(account = null)

    val result = reducer.reduce(state, SettingsIntent.SetAccount(fakeAccount))

    assertThat(result?.account).isEqualTo(fakeAccount)
}
```

### Pattern D: Boolean toggle intents

```kotlin
@Test
fun `SetLoading true sets isLoading to true`() {
    val state = HistoryState(isLoading = false)

    val result = reducer.reduce(state, HistoryIntent.SetLoading(true))

    assertThat(result?.isLoading).isTrue()
}

@Test
fun `SetLoading false sets isLoading to false`() {
    val state = HistoryState(isLoading = true)

    val result = reducer.reduce(state, HistoryIntent.SetLoading(false))

    assertThat(result?.isLoading).isFalse()
}
```

### Pattern E: Initial/default state preservation

Verify that intents don't accidentally wipe unrelated state fields:

```kotlin
@Test
fun `SetError preserves existing account data`() {
    val fakeAccount = Account(id = "acc-1", /* ... */)
    val state = SettingsState(account = fakeAccount, isLoading = true)

    val result = reducer.reduce(state, SettingsIntent.SetError("failed"))

    assertThat(result?.account).isEqualTo(fakeAccount)  // preserved
    assertThat(result?.errorMessage).isEqualTo("failed") // changed
}
```

### Pattern F: Using mockk for complex model fixtures

When the intent or state contains complex domain objects you don't need to fully construct:

```kotlin
private val itemA: HistoryMonth = mockk(relaxed = true)
private val itemB: HistoryMonth = mockk(relaxed = true)

@Test
fun `Retry sets isLoading without clearing existing items`() {
    val state = HistoryState(historyItems = listOf(itemA), isLoading = false)

    val result = reducer.reduce(state, HistoryIntent.Retry)

    assertThat(result?.isLoading).isTrue()
    assertThat(result?.historyItems).containsExactly(itemA)  // preserved
}
```

> Use `mockk(relaxed = true)` for complex models in reducer tests when only testing state structure, not model internals.

## Step 11.4: Reducer coverage checklist

Every reducer test file must cover:
1. **One test per `when` branch** in `reduce()` — each Intent variant
2. **State mutation intents**: assert changed fields AND one preserved field
3. **Side-effect intents**: assert returns `null`
4. **Data-carrying intents**: assert payload maps to correct state field
5. **Boolean toggles**: test both `true` and `false`
6. **`else` branch** (if present): test with an unhandled intent → returns `null` or `state.copy()`

## Reducer-specific success criteria

- [ ] No `MainDispatcherRule`, no `runTest`, no mocks needed (pure function)
- [ ] One test per `when` branch in `reduce()`
- [ ] State mutation intents: assert changed fields AND at least one preserved field
- [ ] Side-effect-only intents: assert returns `null`
- [ ] Data-carrying intents: assert payload maps to correct state field
- [ ] Boolean toggles: test both `true` and `false`
- [ ] `else` / default branch tested
