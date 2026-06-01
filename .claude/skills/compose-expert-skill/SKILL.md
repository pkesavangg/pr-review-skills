---
name: compose-expert-skill
description: >
  Write, review, or improve Jetpack Compose + Kotlin code for the MeApp Android project
  following MVI architecture, Hilt DI, and project-specific conventions from CLAUDE.md.
  Use when: building new screens or features, reviewing Compose UI, writing ViewModels
  or Reducers, wiring Hilt DI, writing unit tests, or working with Room.
  Skip when: iOS/Swift code, non-Android files, purely backend/server changes.
allowed-tools: Read, Bash(./gradlew *), Bash(git status), Bash(git diff *), Bash(find *), Bash(grep *)
---

# MeApp Android / Compose Expert Skill

## Quick Start

```kotlin
// New feature scaffold — copy and adapt
// 1. FooState.kt (immutable)
data class FooState(val items: ImmutableList<Foo> = persistentListOf(), val isLoading: Boolean = false)

// 2. FooReducer.kt (pure)
class FooReducer : IReducer<FooState, FooIntent> {
    override fun reduce(state: FooState, intent: FooIntent) = when (intent) {
        is FooIntent.SetItems -> state.copy(items = intent.items)
        else -> state.copy()
    }
}

// 3. FooViewModel.kt
@HiltViewModel
class FooViewModel @Inject constructor(private val fooService: IFooService)
    : BaseIntentViewModel<FooState, FooIntent>(FooReducer()) {
    override fun provideInitialState() = FooState()
    override fun onDependenciesReady() { /* observeAll() here, not in init{} */ }
}

// 4. FooScreen.kt
@Composable
fun FooScreen(viewModel: FooViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FooContent(state = state, onIntent = viewModel::handleIntent)
}
```

For detailed guidance, consult the Topic Router below.

## Operating Rules

- Always follow `Android/CLAUDE.md` — it overrides general Compose best practices
- Never use `!!` — banned by detekt; use `?: return`, `?.let`, or `requireNotNull`
- Never use `Log` directly — always use `AppLog`
- Never hardcode colors / typography / spacing — use `MeAppTheme.colorScheme`, `.typography`, `.spacing`
- All previews: `@PreviewTheme` + `MeAppTheme { ... }`
- Strings: feature-specific `strings/` folder, PascalCase objects (e.g. `LoginStrings.Title`)
- Prefer `features/common/` shared composables over raw Material3

## Task Workflows

### Implement a new feature
1. Identify layers affected: domain model → data (DAO/repo/service) → ViewModel → Composable
2. Run the Topic Router for each layer
3. Add `@PreviewTheme` previews for every new composable

### Review Compose code
1. Check banned patterns (`!!`, raw `Log`, hardcoded values) → P0
2. Run Topic Router for each topic in the diff
3. Check accessibility: every interactive element needs `contentDescription` or `semantics`

### Write / update tests
1. Stack: MockK + Truth + Turbine + `runTest` + `MainDispatcherRule`
2. See [`references/testing-patterns.md`](references/testing-patterns.md)
3. Target: ≥80% line (≥85% auth/account paths)

### Fix a bug
1. Identify root cause — fix the invariant, not just the symptom
2. Add a regression test before marking done

## Topic Router

| Topic | Reference |
|-------|-----------|
| MVI (ViewModel + Reducer + State/Intent) | [`references/mvi-pattern.md`](references/mvi-pattern.md) |
| Hilt dependency injection | [`references/hilt-di.md`](references/hilt-di.md) |
| Compose state & recomposition | [`references/compose-state.md`](references/compose-state.md) |
| Compose side effects (LaunchedEffect, etc.) | [`references/compose-side-effects.md`](references/compose-side-effects.md) |
| Compose modifiers & layout | [`references/compose-modifiers.md`](references/compose-modifiers.md) |
| Compose accessibility (TalkBack, semantics) | [`references/compose-accessibility.md`](references/compose-accessibility.md) |
| Room (DAO / Entity / migration) | [`references/room-patterns.md`](references/room-patterns.md) |
| Kotlin Flow & coroutines | [`references/flow-coroutines.md`](references/flow-coroutines.md) |
| Unit testing (MockK / Turbine) | [`references/testing-patterns.md`](references/testing-patterns.md) |
| MeApp theme tokens | [`references/meapp-theme.md`](references/meapp-theme.md) |

## Hard Stops

Pause and confirm with the user before:
- Bumping `AppDatabase.version` (needs a Migration object)
- Changing a public interface signature (`IFooService`, `IFooRepository`, etc.)
- Reusing or deleting a proto field number
- Using `--no-verify` or `git push --force`
