---
name: feature-slice
description: Scaffold a new MVI feature slice for the meApp Android app — State, Intent, IReducer, BaseIntentViewModel, and the Compose screen — using the closest existing feature as the pattern. Use when the user says "add a feature", "scaffold this screen/flow", "new MVI slice", "create a settings section", or when a task needs a full feature skeleton.
---

Scaffold an MVI feature slice using the nearest existing feature under `features/` as the template.

The feature or slice is: $ARGUMENTS

## Instructions

### 1 — Find the closest existing pattern
```bash
rg -l "BaseIntentViewModel" Android/app/src/main -g '*.kt' | head
```
Read the most similar feature's `…/model/` (State + Intent + Reducer + ViewModel) and its Composable. Match its structure exactly.

### 2 — Create the slice (immutable, unidirectional)
Under `features/<feature>/`:
- **State** — immutable `data class`; update only via `.copy()`.
- **Intent** — `sealed interface`/`sealed class`; one variant per user action.
- **Reducer** — `IReducer<State, Intent>`, a **pure** `(State, Intent) -> State?` (return `null` for no-op).
- **ViewModel** — extends `BaseIntentViewModel<State, Intent>`; inject services via Hilt constructor (`@HiltViewModel`); side effects (network/DB) live here, never in the reducer.
- **Screen** — `@Composable`; collects state, sends intents; uses `MeAppTheme` tokens; prefers shared composables from `features/common/`.

### 3 — Apply project conventions
- Interfaces prefixed `I`. Static text → feature `strings/` `Strings` object (see `/add-strings`).
- Every `AppInput` sets `imeAction` + `onImeAction` with a `FocusRequester`.
- Add `@PreviewTheme` previews wrapped in `MeAppTheme { ... }`.
- Add stable `testTag`s via the central component params (never `.testTag()` on a call-site modifier chain).

### 4 — Wire it up (follow-ups)
- `/wire-service` — register any new `I*`-interface in Hilt (`core/di/`).
- `/wire-navigation` — add the `AppRoute` entry + Navigation3 wiring.
- `/add-strings` — feature strings.
- `/unit-tests` (or agent `reducer-test-scaffolder`) — reducer + ViewModel tests.

### 5 — Verify
`/verify-tests` then `/verify-on-emulator`.
