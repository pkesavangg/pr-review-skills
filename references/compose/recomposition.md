# Compose — Recomposition

Rules covering recomposition correctness and performance. Sourced from the official [Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md), the [Compose performance docs](https://developer.android.com/jetpack/compose/performance), and Slack's [compose-lints](https://slackhq.github.io/compose-lints/).

Apply each rule when reviewing `*.kt` files containing `@Composable` functions.

---

## P0 — Recomposition loops

**Composable triggers its own re-execution every frame.** Happens when state mutated during composition is read during the same composition.

```kotlin
// BAD — every recomposition flips the state, which schedules another recomposition
@Composable fun Loop() {
    var n by remember { mutableStateOf(0) }
    n++                       // mutation inside composition
    Text("$n")
}
```

Fix: hoist mutation into a side-effect block (`LaunchedEffect`, event handler, `derivedStateOf`).

**Unstable key on `LaunchedEffect` / `DisposableEffect`.** A new lambda, list literal, or `Any()` passed as the key re-launches the effect every recomposition.

```kotlin
// BAD — { user.id } captured by reference; new lambda each recomposition
LaunchedEffect(key1 = { user.id }) { … }

// GOOD — primitive value is stable
LaunchedEffect(user.id) { … }
```

---

## P1 — Effect key mistakes

**`LaunchedEffect(Unit)` or `LaunchedEffect(true)` when the effect actually depends on state.** Effect won't re-run when the state changes.

```kotlin
// BAD — won't refetch when `query` changes
@Composable fun Search(query: String) {
    LaunchedEffect(Unit) { fetch(query) }
}

// GOOD
@Composable fun Search(query: String) {
    LaunchedEffect(query) { fetch(query) }
}
```

**Expensive computation in composable body without `remember`.** Re-runs on every recomposition.

```kotlin
// BAD
@Composable fun List(items: List<Item>) {
    val sorted = items.sortedBy { it.priority }   // sorts every recomposition
    LazyColumn { items(sorted) { … } }
}

// GOOD
@Composable fun List(items: List<Item>) {
    val sorted = remember(items) { items.sortedBy { it.priority } }
    LazyColumn { items(sorted) { … } }
}
```

**Derived state computed without `derivedStateOf`.** Triggers recomposition on each underlying-state change even when the derived value didn't change.

```kotlin
// BAD — recomposes on every scroll pixel
val showFab = listState.firstVisibleItemIndex == 0

// GOOD — only recomposes when `showFab` actually flips
val showFab by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}
```

---

## P2 — Skippability and lambda stability

**Unstable parameters break skippability.** A composable receives a `List<T>` / `Map<K,V>` / `Set<T>` parameter — these are not `@Stable` in Kotlin's standard library. Compose can't skip recomposition when the *contents* haven't changed.

Mitigations:
- Use `ImmutableList<T>` (kotlinx.collections.immutable).
- Or wrap in a `@Immutable` data class.
- Or mark the holder type `@Stable` if you can guarantee equality reflects observable state.

**Lambda recreated each recomposition.** A lambda literal in the call site is a fresh instance every recomposition, defeating skippability of the child.

```kotlin
// BAD — new lambda each recomposition
MyChild(onClick = { vm.save() })

// GOOD — stable reference
MyChild(onClick = vm::save)
```

**`Modifier` parameter passed by value but mutated downstream.** Modifiers are immutable; chaining returns a new instance. This is fine — but be aware that `Modifier.fillMaxWidth().padding(8.dp)` inside the composable body creates a fresh chain each recomposition. If the modifier is hoisted as a parameter, prefer:

```kotlin
@Composable
fun MyView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(8.dp))   // chained once; cheap
}
```

---

## P2 — Stability annotations

Use `@Stable` / `@Immutable` deliberately:

- `@Immutable` — class fields never change after construction. Strongest guarantee.
- `@Stable` — equality reflects observable state; mutation notifies the framework. Use for `MutableState`-backed holders.

Don't annotate types that don't meet the contract — it silently breaks Compose's skipping logic.

---

## Nit

- Composable function returning `Unit` named in `camelCase` instead of `PascalCase`.
- `key()` block missing inside a `forEach` over a list where item identity matters for animation/state preservation.
