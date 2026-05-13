# Compose — State Management

Rules covering state ownership, hoisting, and side-effect handling. Sourced from the official [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state) docs and the [side-effects guide](https://developer.android.com/jetpack/compose/side-effects).

---

## P0 — Crash / data-loss risks

**`runBlocking { … }` in UI / composable / ViewModel.** Blocks the main thread; ANRs.

```kotlin
// BAD
@Composable fun ProfileScreen(repo: UserRepo) {
    val user = runBlocking { repo.load() }   // blocks main thread
}
```

Fix: launch in a coroutine scope tied to the ViewModel, expose as `StateFlow` / `State`, collect with `collectAsStateWithLifecycle()`.

**Blocking call on `Dispatchers.Main`.** Same blast radius as `runBlocking` but in a coroutine. Move IO to `Dispatchers.IO`, CPU work to `Dispatchers.Default`.

---

## P1 — State hoisting violations

**Business logic inside a leaf `@Composable`.** Composables should be idempotent and side-effect-free during composition. Network calls, DB queries, repository lookups, navigation decisions belong in the ViewModel.

```kotlin
// BAD
@Composable fun ProductCard(productId: String) {
    val product = ProductRepo.get(productId)    // lookup inside composable
    Text(product.name)
}

// GOOD
@Composable fun ProductCard(product: Product) { Text(product.name) }
// Caller hoists: val product by vm.product.collectAsStateWithLifecycle()
```

**State owned at a level that can't read/write it.** If two siblings need to share state, hoist to the common parent. If the caller needs to control or observe it, take it as a parameter.

```kotlin
// BAD — caller can't read the toggle state
@Composable fun MyToggle() {
    var on by remember { mutableStateOf(false) }
    Switch(checked = on, onCheckedChange = { on = it })
}

// GOOD — state hoisted
@Composable fun MyToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange)
}
```

**ViewModel holds `Context` / `Activity` / `View` / `@Composable` references.** Leaks the Activity across configuration changes; crashes when the held reference goes stale.

Use `Application` context if needed (or inject the few system services you need explicitly).

---

## P1 — Coroutine scope discipline

**`GlobalScope.launch` in a composable or ViewModel.** Scope outlives the screen; work continues after the user navigates away, often causing crashes when it tries to update destroyed state.

Use:
- `viewModelScope` for VM-owned work.
- `rememberCoroutineScope()` inside a composable for event-handler scopes.
- `LaunchedEffect(key)` for keyed side effects tied to composition lifecycle.

**Coroutine launched from `remember { … }` block.** `remember` runs during composition; coroutines launched there are not tied to the composition lifecycle and won't cancel.

```kotlin
// BAD
val job = remember { scope.launch { … } }

// GOOD — keyed and lifecycle-aware
LaunchedEffect(key) { … }
```

---

## P1 — Wrong side-effect API

Pick the right tool — each handles a different concern:

| Need | Use |
|---|---|
| Run a suspending side effect tied to composition lifecycle | `LaunchedEffect(keys)` |
| Subscribe + cleanup (listener, callback, sensor) | `DisposableEffect(keys)` |
| Publish state into a non-Compose system on every recomposition | `SideEffect { … }` |
| Capture latest value of a state for use in a long-running effect | `rememberUpdatedState(value)` |
| Imperative scope you start from an event handler | `rememberCoroutineScope()` |
| One-shot snapshot of state changes into a Flow | `snapshotFlow { … }` |

Common mistakes:

- Using `SideEffect` for suspending work — it's for synchronous side effects only.
- Subscribing in `LaunchedEffect` without `DisposableEffect`'s `onDispose { … }` cleanup.
- Collecting a `Flow` with `LaunchedEffect` instead of `collectAsStateWithLifecycle()`.

---

## P1 — Lifecycle-unaware `Flow` collection

**`flow.collectAsState()` instead of `flow.collectAsStateWithLifecycle()`** on Android.

`collectAsState()` keeps collecting when the app is in the background, draining battery and risking inconsistent state. `collectAsStateWithLifecycle()` (from `androidx.lifecycle:lifecycle-runtime-compose`) pauses collection when the lifecycle is `STOPPED`.

---

## P2 — State holder shape

**`remember { mutableStateOf(...) }` for state that should outlive process death** — use `rememberSaveable` for primitive / `Parcelable` state.

**Multiple separate `mutableStateOf` fields** when they're always mutated together — pack them into a `data class` and use one `mutableStateOf`.

**ViewModel exposing `MutableStateFlow` publicly.** Expose as `StateFlow` (read-only), keep the mutable version `private`.

```kotlin
// BAD
val user = MutableStateFlow<User?>(null)

// GOOD
private val _user = MutableStateFlow<User?>(null)
val user: StateFlow<User?> = _user.asStateFlow()
```

---

## P2 — Compose lifecycle and config changes

- State stored only in `remember { }` is lost on configuration change. Use `rememberSaveable` or hoist into the ViewModel.
- `BackHandler` outside the screen scope leaks across screens. Place at the top of the screen composable.

---

## Nit

- `var state by remember { mutableStateOf(0) }` is preferred over `val state = remember { mutableStateOf(0) }` for readability when you read/write it as a value.
- Name `StateFlow` properties without `Flow` suffix (`user`, not `userFlow`) — the type already conveys it.
