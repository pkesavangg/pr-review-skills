# Compose State & Recomposition — MeApp Android

## State sources of truth

- **ViewModel state** → collected in the screen composable via `collectAsStateWithLifecycle()`
- **Local UI state** (not business logic) → `remember { mutableStateOf(...) }` inside the composable
- Never put business logic in `remember` blocks

```kotlin
// Screen composable — correct pattern
@Composable
fun FooScreen(viewModel: FooViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FooContent(state = state, onIntent = viewModel::handleIntent)
}

// Stateless content composable — easier to preview and test
@Composable
private fun FooContent(state: FooState, onIntent: (FooIntent) -> Unit) { ... }
```

## Stability

- Data classes passed to Compose must have **stable** fields. `List<T>` is unstable — use `ImmutableList<T>` from `kotlinx.collections.immutable` (already a dep).
- `ImmutableList` emits as `@Stable` and prevents needless recomposition of large lists.

```kotlin
data class FooState(
    val items: ImmutableList<Foo> = persistentListOf(),  // ✅ stable
    val isLoading: Boolean = false,
)
```

## derivedStateOf

Use when a derived value is expensive and its inputs change more often than the derived result:

```kotlin
val filteredItems by remember(state.items) {
    derivedStateOf { state.items.filter { it.isActive } }
}
```

## Keys in LazyColumn

Always provide a stable, unique key to prevent position-based recomposition:

```kotlin
LazyColumn {
    items(items = state.items, key = { it.id }) { item ->
        FooItem(item)
    }
}
```

## State hoisting

- Hoist state up to the **lowest common ancestor** that needs it.
- Pass lambdas down, not the ViewModel directly.
- Screens pass `onIntent: (FooIntent) -> Unit`, content composables receive specific lambdas.

## Patterns to avoid

- `LocalContext.current` inside a composable body is fine, but don't pass `Context` to a ViewModel — inject `@ApplicationContext` via Hilt instead.
- Never call `viewModel.state.value` inside a composable body without `collectAsStateWithLifecycle()` — it won't trigger recomposition.
- Avoid `mutableStateListOf` for business-logic collections — keep them in ViewModel state.
