# Compose — Modifier Conventions

Rules covering the `Modifier` parameter contract. Sourced from the [Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#modifier-parameter) and the [Compose modifiers documentation](https://developer.android.com/jetpack/compose/modifiers).

The `Modifier` parameter is the most-violated convention in real Compose code; misuse breaks user expectations of how to compose layouts.

---

## P1 — Modifier parameter contract

**Every `@Composable` that emits UI must accept a `Modifier`** as a parameter named exactly `modifier`, **defaulting to `Modifier`**, and it must be the **first optional parameter** (placed after required parameters, before all other optional parameters and the trailing `content` lambda).

```kotlin
// CANONICAL
@Composable
fun UserAvatar(
    user: User,                                  // required
    modifier: Modifier = Modifier,               // first optional, default Modifier
    size: Dp = 40.dp,                            // other optional params follow
    onClick: (() -> Unit)? = null,
) { … }
```

Common violations:

```kotlin
// BAD — Modifier last
@Composable fun Avatar(user: User, size: Dp = 40.dp, modifier: Modifier = Modifier) { … }

// BAD — wrong default
@Composable fun Avatar(user: User, modifier: Modifier = Modifier.fillMaxWidth()) { … }

// BAD — wrong parameter name
@Composable fun Avatar(user: User, mod: Modifier = Modifier) { … }
```

Default must be the empty `Modifier` — callers add what they need. Pre-applying `fillMaxWidth()` or `padding()` makes the composable impossible to size correctly.

---

## P1 — Modifier forwarding

**The passed `modifier` must apply to the root layout** that the composable emits. Not consumed by an inner element, not split, not dropped.

```kotlin
// BAD — modifier ignored
@Composable fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.padding(16.dp)) {              // caller's modifier never used
        content()
    }
}

// BAD — modifier applied to wrong element
@Composable fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.padding(16.dp)) {
        Surface(modifier) {                      // padding wraps Surface, not Card
            content()
        }
    }
}

// GOOD — modifier forwarded to root
@Composable fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(16.dp)) {
        content()
    }
}
```

When the composable wants its own modifier semantics (always padded, always full width), chain *after* the caller's modifier — never before.

```kotlin
// GOOD — caller's modifier first, then internal additions
Box(modifier.padding(16.dp).clip(RoundedCornerShape(8.dp))) { … }
```

Rationale: caller's intent takes precedence; internal styling is the floor, not the ceiling.

---

## P2 — Internal vs hoisted modifiers

If a composable needs an *internal* modifier (e.g., for an inner sub-element), name it specifically — don't reuse `modifier`.

```kotlin
@Composable
fun Card(
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        Header(modifier = headerModifier)
        content()
    }
}
```

---

## P2 — Don't create modifiers in hot paths

`Modifier.padding(8.dp)` allocates. Inside a `@Composable` body that recomposes frequently, hoist with `remember` if the modifier expression is expensive (rare in practice) — but more often, just keep the chain flat and let Compose handle it.

Avoid this:

```kotlin
// BAD — recomputes the modifier on every recomposition AND mutates state
val mod = remember(state) { Modifier.size(if (state) 40.dp else 60.dp) }
```

Prefer:

```kotlin
val size by animateDpAsState(if (state) 40.dp else 60.dp)
Box(modifier.size(size))
```

---

## P2 — Order matters

`Modifier` order is left-to-right; later modifiers wrap earlier ones in the layout tree. Background applied before clip vs after clip produces visibly different results.

Reviewer heuristics:

- `clip` before `background` → background clipped to shape (usually desired).
- `padding` before `clickable` → ripple respects padding (often undesired).
- `padding` after `clickable` → ripple includes the padding area (usually desired).

Flag obviously-wrong orderings with a P2 / Nit and a suggested fix.

---

## P2 — `then` and modifier composition

Composables that conditionally apply a modifier should use `Modifier.then(...)`, not `if/else` returning different `Modifier` trees (which breaks recomposition stability).

```kotlin
// BAD — branches return different types, hurts skippability
val mod = if (selected) Modifier.background(...) else Modifier

// GOOD
val mod = Modifier.then(
    if (selected) Modifier.background(...) else Modifier
)
```

For truly conditional logic in a chain, the `Modifier` `.thenIf {}` extension is conventional but not built-in — teams often define their own.

---

## Nit

- `modifier: Modifier = Modifier` is the only acceptable default — never `Modifier.something()`.
- A composable that emits no layout (only invokes other composables, no `Box`/`Column`/`Row`/`Layout`) does **not** need a `modifier` parameter; passing one would have nowhere to go.
