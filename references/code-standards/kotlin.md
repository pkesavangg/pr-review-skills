# Kotlin Code Standards — using the language's own constructs

The *language-idiom* lens for Kotlin, separate from the Compose-API lens (`vendored/compose-expert`, `compose/*`) and the BLE/SDK lens (`sdk/*`). The question here is not "does this work?" but **"is this expressed with the construct Kotlin provides for it?"** — a closed set of values should be an `enum class`, a closed set of *shapes* should be a `sealed interface`, data should be a `data class`, an unchanging binding should be a `val`, and names should follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

Applies to **any** changed `.kt`/`.kts` file — app code and SDK code alike. Severity uses the orchestrator's taxonomy. **Flag on `+`/modified lines only.** If the repo's `CLAUDE.md`, `detekt.yml`, or an architecture doc states a different convention, prefer it and skip the conflicting rule.

`!!` (not-null assertion) and unchecked casts are **P0** and are already owned by `vendored/compose-expert` (app) and `sdk/ble-core-and-concurrency.md` (SDK) — don't re-flag them here.

---

## P1 — Stringly-typed value where an `enum class` belongs

A `String` parameter that only ever carries a handful of values throws away every compile-time guarantee. The typo compiles, the invented value compiles, and the bug appears as a `when` branch that silently never matches.

```kotlin
// nothing prevents "imperail", "Imperial", or an entirely new value
fun setUnitSystem(unit: String) { … }
fun track(event: String, category: String) { … }

if (profile.status == "active") { … }   // one typo from always-false
```

**Sniff.** On `+` lines: a `String`/`Int` parameter or property whose call sites only pass literals from a small fixed set; a `String` compared with `==` against 2+ hardcoded alternatives; the same literal used as a *value* in ≥3 places; an `Int` flag with documented meanings (`0 = idle, 1 = pairing`).

**Fix.** Declare the vocabulary. An `enum class` keeps the serialized form under your control while making every invalid value a compile error.

```kotlin
enum class UnitSystem(val wireValue: String) {
    IMPERIAL("imperial"),
    METRIC("metric");
}

fun setUnitSystem(unit: UnitSystem) { … }
setUnitSystem(UnitSystem.IMPERAIL)   // ✗ compile error
```

**Do NOT flag** genuinely free-form text (user copy, log messages, server-controlled identifiers) or a `String` passed straight through to an external API.

---

## P1 — `when` over an enum / sealed type closed with `else ->`

Kotlin makes an exhaustive `when` a compile error to leave incomplete — but only when there's no `else`. An `else -> ` branch turns "the compiler will tell me every place to update" into "a new variant silently falls into the fallback".

```kotlin
fun label(unit: UnitSystem): String = when (unit) {
    UnitSystem.IMPERIAL -> "lb"
    UnitSystem.METRIC   -> "kg"
    else -> ""              // add a variant → "" forever, no warning
}
```

**Sniff.** A `when` on an enum/sealed-typed subject with an `else ->` returning a neutral value (`""`, `null`, `0`, `Unit`) or doing nothing, on `+` lines.

**Fix.** Drop the `else` and list the variants — the `when` is used as an expression, so the compiler now enforces exhaustiveness. Group genuinely-shared behaviour explicitly (`UnitSystem.IMPERIAL, UnitSystem.METRIC -> …`) rather than hiding it in a catch-all.

**Do NOT flag** a `when` over an open-ended input (a raw server string, arbitrary user text) where a real fallback is the intended behaviour.

---

## P1 — `sealed interface`/`sealed class` not used for a closed set of variants

When a value is "one of N shapes, each carrying different data", an `enum class` can't hold the per-variant payload and a bag of nullable fields loses the invariant entirely. That's what `sealed` is for: exhaustive `when`, per-variant data, and impossible states made unrepresentable.

```kotlin
// every field nullable — "loading with an error and data" is representable but meaningless
data class ScreenState(
    val isLoading: Boolean,
    val data: List<Reading>?,
    val errorMessage: String?,
)
```

**Sniff.** On `+` lines: a `data class` where multiple properties are nullable and mutually exclusive; a `Boolean` + nullable-payload pair (`isLoading` + `data`); an `enum` variant that needed extra fields so the fields were hoisted onto the enclosing class; a `type: String` discriminator field read in a `when`.

**Fix.**

```kotlin
sealed interface ScreenState {
    data object Loading : ScreenState
    data class Ready(val readings: List<Reading>) : ScreenState
    data class Failed(val reason: FailureReason) : ScreenState
}
```

Every consumer now gets an exhaustive `when`, and the impossible combinations can't be constructed.

**Do NOT flag** a genuinely-flat state object where the fields are independent rather than mutually exclusive.

---

## P2 — Wrong class kind: `data class`, `value class`, `object`, or plain `class`

Kotlin has four containers with four meanings, and the choice is documentation:

- **`data class`** — a value carrying data. You get `equals`/`hashCode`/`toString`/`copy`, which is exactly what a model, DTO, or UI state needs.
- **`@JvmInline value class`** — a single wrapped primitive that must not be confused with other primitives (`WeightKg`, `DeviceId`). Zero runtime allocation, full type safety.
- **`object`** — a stateless singleton namespace (helpers, constant tables).
- **`class`** — real identity or lifecycle-bound state (a repository, a manager, a `ViewModel`).

The finding is a mismatch: a plain `class` used as data (so `==` compares references and a test's `assertEquals` fails for no visible reason), a `data class` used where identity matters, or a `class` instantiated fresh at every call just to reach stateless functions.

```kotlin
class Reading(val kg: Double, val timestamp: Instant)     // == compares references
fun recordWeight(kg: Double)                              // pounds compile just as well
```

**Sniff.** On `+` lines: a `class` with only `val` properties and no behaviour → `data class`; a function taking a bare `Double`/`Long`/`String` for a domain quantity or identifier → `value class`; a `class` with only stateless functions, `new`ed at each call → `object`; a `data class` for a type with lifecycle/identity.

**Fix.**

```kotlin
data class Reading(val kg: WeightKg, val timestamp: Instant)

@JvmInline value class WeightKg(val value: Double)
fun recordWeight(weight: WeightKg)
recordWeight(WeightKg(72.4))
```

---

## P2 — Naming that departs from the Kotlin coding conventions

| Kind | Convention | Good | Bad |
|---|---|---|---|
| Function | `camelCase`, verb-first | `refreshDevices()`, `connectTo(device)` | `deviceRefresh()`, `doWork()`, `handle()` |
| Function returning a boolean | reads as an assertion | `isConnected()`, `hasPendingWrite()` | `connectionCheck()`, `checkConn()` |
| Property | `camelCase` noun; boolean `is`/`has`/`can` | `isConnected`, `retryCount` | `mIsConnected`, `flag2` |
| Backing property | `_name` private, `name` public | `private val _state` / `val state` | `stateMutable` / `state` |
| Class / interface / enum | `PascalCase` | `KettleRepository`, `UnitSystem` | `IKettleRepository`, `kettle_repository` |
| Enum constant | `SCREAMING_SNAKE_CASE` (or `PascalCase` — pick one per module) | `IMPERIAL`, `METRIC` | `imperial`, `Imperial_System` |
| Compile-time constant | `const val` in `SCREAMING_SNAKE_CASE` | `const val MAX_RETRIES = 3` | `val maxRetries = 3` at top level |
| Composable function | `PascalCase`, noun (it's a UI element) | `KettleStatusCard()` | `kettleStatusCard()`, `drawStatus()` |
| File | matches the single top-level class; otherwise `PascalCase` describing contents | `KettleRepository.kt`, `BleExtensions.kt` | `utils2.kt`, `kettle_repo.kt` |

No Java-isms: no `m`/`s` field prefixes, no `I`-prefixed interfaces, no `get`/`set` prefixes on Kotlin properties, no Hungarian notation.

**Sniff.** On `+` lines: `m`-prefixed or `I`-prefixed identifiers; `get`-prefixed functions that should be properties; a `camelCase` `@Composable`; a top-level `val` of a compile-time constant that isn't `const`; `SCREAMING_SNAKE` locals; abbreviations (`btn`, `mgr`, `tmp`); a type name repeated in a property name (`deviceList: List<Device>` → `devices`).

**Fix.** Rename via the IDE so call sites move with it. Composables in particular: a `@Composable` that emits UI is named like the thing it emits.

---

## P2 — `var` where `val` suffices, or a publicly mutable property

`val` is a fact the reader can rely on. A `var` — especially a public one, or a `MutableStateFlow`/`MutableList` exposed directly — hands mutation rights to every caller and makes the owning class's invariants unenforceable.

```kotlin
class DeviceViewModel {
    val devices = MutableStateFlow<List<Device>>(emptyList())   // any caller can emit
    var retryCount = 0                                          // public, unguarded
}
```

**Sniff.** On `+` lines: a `var` never reassigned in scope; a public `MutableStateFlow` / `MutableLiveData` / `MutableList` / `var` property on a class that owns state.

**Fix.** `val` for unchanging bindings; for state, keep the mutable side private and expose the read-only projection — the pattern the rest of the codebase already uses.

```kotlin
private val _devices = MutableStateFlow<List<Device>>(emptyList())
val devices: StateFlow<List<Device>> = _devices.asStateFlow()
```

Related: [`compose/state-management.md`](../compose/state-management.md) — post one finding, not two.

---

## P2 — Magic number / literal with no named constant

An inline `3`, `250L`, `0.85f`, or `"user_profile_v2"` is an unreviewable decision, invisible when it needs to change everywhere.

**Sniff.** Numeric literals other than `0`/`1`/`-1` in expressions on `+` lines (timeouts, retry counts, thresholds, byte offsets), or the same literal in ≥3 places.

**Fix.** A `private const val` at file scope, or a `companion object` constant on the owning class — with the reason in the name:

```kotlin
private const val CONNECT_RETRY_LIMIT = 3
private const val GATT_SETTLE_MS = 250L
```

Use `const val` (compile-time inlined), not a plain `val`, for literals. **Do NOT flag** values a design-token/dimension resource already owns, or numbers that self-evidently *are* the data.

---

## P2 — Missing visibility modifier on a new declaration

Kotlin defaults to `public`. In a library module that silently widens the surface you must keep working; in any module it hides the author's intent about what's an implementation detail.

**Sniff.** A new top-level or member declaration on `+` lines with no visibility modifier, in a file whose siblings annotate theirs; or a new `public` member added to a library target with no changelog/doc entry.

**Fix.** `private`/`internal` for implementation details, `internal` for module surface, `public` for library API — and for an SDK, only after the contract check in [`sdk/public-api-contract.md`](../sdk/public-api-contract.md) passes.

---

## Nit — Not using the standard library where it reads better

Kotlin's stdlib replaces most hand-rolled control flow: `?.let`/`?:` instead of a nullable `if` ladder, `require`/`check` instead of a manual throw, `firstOrNull` instead of a loop-and-break, `takeIf`, `groupBy`, `associateWith`, `buildList`, string templates instead of concatenation, `apply`/`also` for configuration blocks.

**Sniff.** On `+` lines: a manual null-check pyramid; `if (x == null) throw IllegalArgumentException(...)`; an index loop over a collection to find one element; `"a " + b + " c"` concatenation.

**Fix.** Use the stdlib equivalent — but only where it genuinely reads better. A chain of five scope functions is worse than the loop it replaced; don't flag readable imperative code just because a functional form exists.
