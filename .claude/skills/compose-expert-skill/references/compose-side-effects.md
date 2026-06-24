# Compose Side Effects — MeApp Android

## The three side-effect APIs

| API | When to use |
|---|---|
| `LaunchedEffect(key)` | One-shot or key-changing async work tied to composition |
| `DisposableEffect(key)` | Work with explicit cleanup (listeners, callbacks) |
| `SideEffect` | Sync non-Compose state after every successful recomposition |

## LaunchedEffect

```kotlin
// ✅ Correct — runs once on composition, restarts when viewModel changes
LaunchedEffect(viewModel) {
    viewModel.uiEvents.collect { event ->
        when (event) {
            is FooEvent.NavigateBack -> navigationService.navigateBack()
            is FooEvent.ShowToast -> dialogQueueService.showToast(...)
        }
    }
}

// ✅ Runs once (key = Unit)
LaunchedEffect(Unit) {
    viewModel.loadInitialData()
}
```

## DisposableEffect

```kotlin
// ✅ Register/unregister a listener
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

## ❌ Patterns to avoid

```kotlin
// ❌ Side effect in composition body — executes on every recomposition
@Composable
fun BadExample(viewModel: FooViewModel) {
    viewModel.loadData()  // ← WRONG: runs every recomposition
    ...
}

// ✅ Use LaunchedEffect instead
@Composable
fun GoodExample(viewModel: FooViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    ...
}

// ❌ Triggering navigation from inside composition
@Composable
fun BadNav(state: FooState) {
    if (state.isDone) {
        navController.navigate(...)  // ← WRONG: navigation is a side effect
    }
}

// ✅ Handle navigation event in LaunchedEffect
@Composable
fun GoodNav(viewModel: FooViewModel) {
    LaunchedEffect(viewModel.navEvents) {
        viewModel.navEvents.collect { ... }
    }
}
```

## Callbacks from ViewModel to UI

Keep one-shot events (navigation, toasts) in MeApp via `dialogQueueService`/`navigationService` called from the ViewModel directly — no `Channel`/`SharedFlow` to the UI needed for the common cases. This is the established pattern in this codebase.
