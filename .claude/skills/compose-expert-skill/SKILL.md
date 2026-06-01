---
name: compose-expert-skill
description: Write, review, or improve Jetpack Compose + Kotlin code for the MeApp Android project following MVI architecture, Hilt DI, and project-specific conventions. Use when building new features, reviewing Compose UI, writing ViewModels or reducers, wiring DI, or improving test coverage.
---

# MeApp Android / Compose Expert Skill

## Operating Rules

- Always follow the conventions in `Android/CLAUDE.md` — they take precedence over general Compose best practices
- Never use `!!` (banned by detekt `UnsafeCallOnNullableType`)
- Never use `Log` directly — always use `AppLog`
- Never hardcode colors, typography, or spacing — always use `MeAppTheme.colorScheme`, `.typography`, `.spacing`
- All previews must use `@PreviewTheme` and wrap content in `MeAppTheme { ... }`
- Static strings go in feature-specific `strings/` subfolder as PascalCase `Strings` objects
- Prefer shared composables from `features/common/` before Material3 defaults
- All API methods must be `suspend` functions

## Task Workflow

### Implement a new feature
1. Read the task description and identify affected layers (domain → data → features)
2. Run the Topic Router for each layer touched
3. Follow the layer order: domain model → data (DAO/repo/service) → ViewModel → Composable
4. Add `@PreviewTheme` previews for every new composable

### Review Compose code
1. Check for banned patterns (`!!`, raw `Log`, hardcoded values) — flag as P0
2. Run the Topic Router for every topic relevant to the diff
3. Check accessibility: every interactive element needs a `contentDescription` or `semantics` block

### Write or update tests
1. Use MockK + Truth + Turbine + `runTest` + `MainDispatcherRule`
2. Consult `references/testing-patterns.md`
3. Aim for ≥80% line coverage (≥85% for auth/account paths)

### Fix a bug
1. Identify root cause — prefer fixing the invariant over working around it
2. Never use `--no-verify` to bypass detekt/lefthook unless explicitly asked
3. Add a regression test

## Topic Router

| Topic | Reference |
|-------|-----------|
| MVI (ViewModel + Reducer + State/Intent) | `references/mvi-pattern.md` |
| Hilt dependency injection | `references/hilt-di.md` |
| Compose state & recomposition | `references/compose-state.md` |
| Compose side effects | `references/compose-side-effects.md` |
| Compose modifiers & layout | `references/compose-modifiers.md` |
| Compose accessibility | `references/compose-accessibility.md` |
| Room (DAO / Entity / migration) | `references/room-patterns.md` |
| Kotlin Flow & coroutines | `references/flow-coroutines.md` |
| Unit testing (MockK / Turbine) | `references/testing-patterns.md` |
| MeApp theme tokens | `references/meapp-theme.md` |

## Hard Stops

Stop and ask the user before proceeding if:
- A Room database version bump is needed (migration risk)
- A public interface (`IBabyProfileService`, `IAccountRepository`, etc.) signature changes
- A proto field number would be reused or a field deleted
- Force-push or `--no-verify` is being considered
