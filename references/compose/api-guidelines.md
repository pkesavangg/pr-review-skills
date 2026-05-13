# Compose — API Guidelines

Rules from the official [Jetpack Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md). These are conventions Google enforces inside `androidx.compose.*` — applying them to product code keeps reviewers, IDE tooling, and future Compose APIs consistent with each other.

---

## P1 — Naming

**Composable functions that emit UI use `PascalCase` and return `Unit`.**

```kotlin
// GOOD
@Composable fun UserAvatar(user: User, modifier: Modifier = Modifier) { … }

// BAD — camelCase, returns nothing should be Unit
@Composable fun userAvatar(user: User): Nothing? { … }
```

**Composable functions that *return* a value use `camelCase`** (they are factories, not UI emitters).

```kotlin
@Composable
fun rememberMyState(initial: Int): MyState { … }
```

Look for: a `@Composable` named in `camelCase` that emits UI — rename. Or a `@Composable` named in `PascalCase` that returns a value — rename or split.

---

## P1 — Required parameters before optional

Standard Kotlin convention, but especially load-bearing in Compose because of trailing-lambda calling conventions.

```kotlin
// GOOD
@Composable
fun ActionRow(
    title: String,                              // required
    modifier: Modifier = Modifier,              // optional, first
    subtitle: String? = null,
    onAction: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) { … }
```

Trailing `@Composable () -> Unit` lambdas (`content`, `trailingContent`, etc.) come last so callers can use the trailing-lambda syntax.

---

## P1 — Slot APIs

When a composable contains a substantive block of UI the caller customises, **take a `@Composable` lambda parameter**, don't accept rendered `View`s or strings + styling parameters.

```kotlin
// GOOD — slot
@Composable
fun Banner(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) { … }

// BAD — string-based; caller can't put an icon or rich content
@Composable
fun Banner(text: String, modifier: Modifier = Modifier) { … }
```

A slot can be `nullable` if it's optional with a sensible default-omission:

```kotlin
@Composable
fun TopBar(
    title: @Composable () -> Unit,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) { … }
```

---

## P1 — State hoisting

A reusable composable should **not** own state that the caller might need to read or modify. Take `value` + `onValueChange`-style pairs.

```kotlin
// BAD — caller can't read or reset
@Composable
fun NameField() {
    var name by remember { mutableStateOf("") }
    OutlinedTextField(name, onValueChange = { name = it })
}

// GOOD
@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier)
}
```

Exception: state that's truly local and never observed (e.g., a transient hover/press flag the caller never reads) can stay internal.

---

## P2 — `CompositionLocal` sparingly

`CompositionLocal` is implicit data flow — powerful for theming (e.g., `LocalContentColor`, `MaterialTheme.colorScheme`) but dangerous for app-level data (auth state, user preferences). Implicit data flow makes a composable's behavior depend on something not visible at the call site, hurting testability.

Reviewer heuristic: `CompositionLocal` is appropriate for **cross-cutting style/context** (color scheme, text style, content alpha). It is **not** appropriate for **business data** (current user, feature flags, repository instances) — pass those explicitly.

---

## P2 — Defaults belong on parameters, not internals

Defaults visible at the call site (parameter defaults) are better than defaults hidden inside the composable. Helps IDE tooling, documentation, and Compose previews.

```kotlin
// GOOD
@Composable
fun Card(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,                       // visible in IDE param hints
    colors: CardColors = CardDefaults.cardColors(),
) { … }

// BAD — magic numbers buried inside
@Composable
fun Card(modifier: Modifier = Modifier) {
    Surface(modifier, elevation = 2.dp, color = MaterialTheme.colorScheme.surface) { … }
}
```

---

## P2 — `*Defaults` object for component defaults

Material follows the pattern `<Component>Defaults` (e.g., `ButtonDefaults.buttonColors()`, `CardDefaults.cardElevation()`). For custom design system components, mirror it:

```kotlin
object MyChipDefaults {
    @Composable fun chipColors(...): ChipColors = …
    val MinSize: Dp = 32.dp
}
```

Makes per-parameter customization discoverable without overloading the main composable signature.

---

## P2 — `remember*` factory functions

Composables that return holders to be remembered across recompositions use the `remember*` prefix:

```kotlin
@Composable
fun rememberPagerState(initialPage: Int = 0): PagerState { … }
```

The body internally uses `remember { … }` and `rememberSaveable { … }` as appropriate. Callers do not call `remember` again at the call site.

---

## P2 — Receiver scopes for nested DSLs

When a composable provides a scoped DSL (e.g., `Row { Spacer + Box }`), it should expose that as a receiver scope on the content lambda — not as parameters.

```kotlin
// GOOD
@Composable
fun MyContainer(content: @Composable MyContainerScope.() -> Unit) { … }

interface MyContainerScope {
    fun Modifier.align(alignment: Alignment): Modifier
}
```

Callers get IDE completion for scope-only extensions; outside the scope, those extensions are not in scope.

---

## Nit

- Optional `onClick: (() -> Unit)? = null` — prefer the non-nullable `onClick: () -> Unit = {}` if it's truly optional with a no-op default.
- Boolean flags in composable parameter lists named in the affirmative (`enabled: Boolean = true`, not `disabled: Boolean = false`).
- File name matches the top-level composable: `UserAvatar` lives in `UserAvatar.kt`.
