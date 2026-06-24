# Brainstorm: Testing Large ViewModels with Infinite Init Coroutines

**Date:** 2026-03-16
**Status:** Ready for implementation
**Tickets:** MA-3440, MA-3441, MA-3442

## What We're Building

A testing strategy for 3 large ViewModels that cause OOM errors during unit testing:

| ViewModel | Lines | Infinite Coroutines in Init | Key Issue |
|-----------|-------|----------------------------|-----------|
| WifiScaleSetupViewModel | 1191 | 6 (inc. `while(!isDestroyed)` poll loop) | Polling loop + 5 `.collect()` |
| BtWifiScaleSetupViewModel | 1014 | 6 `.collect()` + timeout jobs | Cascading launches |
| ScaleDetailsViewModel | ~800 | 3 `.collect()` + cascading launches | Device change triggers nested launches |

All three use `@AssistedInject` and extend `ScaleSetupViewmodel` (or `BaseIntentViewModel`) with hardware (BLE/WiFi) dependencies.

## Why OOM Happens

With `UnconfinedTestDispatcher` (our current default), coroutines execute eagerly and synchronously. When init blocks launch infinite `.collect()` subscriptions and polling loops, they spin immediately and never yield, causing unbounded memory allocation.

## Approach: StandardTestDispatcher + Lazy Init

### 1. Use StandardTestDispatcher

```kotlin
val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
```

- Coroutines are **queued**, not eagerly executed
- Test controls execution with `advanceUntilIdle()` or `advanceTimeBy()`
- Infinite loops don't spin — they just queue work

### 2. Lazy ViewModel Creation

Don't create the ViewModel in `@BeforeEach`. Use a `createViewModel()` factory per-test:

```kotlin
@BeforeEach
fun setUp() {
    MockKAnnotations.init(this)
    // Stub flows but DON'T create viewModel yet
}

private fun createViewModel(): WifiScaleSetupViewModel {
    return WifiScaleSetupViewModel(sku, ...).initTestDependencies(...)
}

@Test
fun `some test`() = runTest {
    // Stub specific flows for this test
    every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
    viewModel = createViewModel()
    advanceUntilIdle() // Run init coroutines to completion (or partial)
    // Assert...
}
```

### 3. Time-Controlled Advancement

For the `while(!isDestroyed)` polling loop in WifiScaleSetupVM:

```kotlin
// Advance past one poll cycle (1500ms delay)
testScheduler.advanceTimeBy(1600)
// Assert state after one poll
```

For tests that don't need init subscriptions to run:
```kotlin
// Don't call advanceUntilIdle() — init coroutines stay queued
viewModel = createViewModel()
viewModel.handleIntent(SomeIntent.PureState(value))
// Assert state directly (reducer runs synchronously via super.handleIntent)
```

## Key Decisions

1. **Parameterized MainDispatcherRule** — use existing `MainDispatcherRule(StandardTestDispatcher())`, no new class
2. **Lazy init** — factory method per-test, not in `@BeforeEach`
3. **Selective advancement** — `advanceTimeBy()` for polling, `advanceUntilIdle()` only when safe
4. **Focus on intent handlers** — test state transitions and side effects, not init subscriptions (those are integration-level)
5. **All flows stubbed with `MutableStateFlow`** — never `flowOf()` for infinite collectors

## Open Questions

None — ready for implementation.
