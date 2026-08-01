# Swift Code Standards — using the language's own constructs

The *language-idiom* lens for Swift, separate from the SwiftUI-API lens (`vendored/swiftui-pro`) and the concurrency/logging lenses (`ios/*`). The question here is not "does this work?" but **"is this expressed with the construct Swift provides for it?"** — a closed set of values should be an `enum`, a domain quantity should be a type rather than a bare `Double`, a value should be a `struct`, a never-mutated binding should be `let`, and a name should read like the [Swift API Design Guidelines](https://www.swift.org/documentation/api-design-guidelines/) say it should.

These rules apply to **any** changed `.swift` file — app code and SDK code alike. Severity uses the orchestrator's taxonomy. **Flag on `+`/modified lines only.** If the repo's `CLAUDE.md`, `.swiftlint.yml`, or an architecture doc states a different convention, prefer it and skip the conflicting rule.

Force-unwrap (`!`), force-cast (`as!`), and `try!` are **P0** and are already owned by `vendored/swiftui-pro` (app) and `sdk/ble-core-and-concurrency.md` (SDK) — don't re-flag them here.

---

## P1 — Stringly-typed value where an `enum` belongs

A `String` parameter that only ever holds one of a handful of values gives up every guarantee the compiler could have made. The typo compiles, the renamed value compiles, the invented value compiles — and the bug lands as a branch that silently never runs.

```swift
// nothing stops "imperail", "Imperial", or "stone"
func setUnitSystem(_ unit: String) { … }
func track(event: String, category: String) { … }

if profile.status == "active" { … }        // one typo away from always-false
```

**Sniff.** On `+` lines: a `String` (or `Int`) parameter/property whose call sites only ever pass literals from a small fixed set; a `String` compared with `==` against 2+ hardcoded alternatives; the same string literal used as a *value* in ≥3 places; a raw `Int` flag with documented meanings (`0 = idle, 1 = pairing`).

**Fix.** Model the vocabulary. A `String`-backed `enum` keeps the wire/serialized form identical while making every invalid value a compile error.

```swift
enum UnitSystem: String, CaseIterable, Codable {
    case imperial, metric
}

func setUnitSystem(_ unit: UnitSystem) { … }
setUnitSystem(.imperail)   // ✗ compile error, caught before it ships
```

**Do NOT flag** genuinely free-form text (user copy, log messages, identifiers from a server you don't control) or a `String` that is passed straight through to an external API.

---

## P1 — Bare numeric primitive for a domain quantity

`Double` says "a number". It doesn't say *kilograms*, *°C*, or *seconds*, so nothing stops a caller passing pounds where kilograms are expected, or milliseconds where seconds are. Mobile/health code is exactly where this bites: a silent unit mix-up is a wrong reading, not a crash.

```swift
func setTargetTemperature(_ value: Double)          // °C? °F? tenths of a degree?
func recordWeight(_ weight: Double, for user: UUID) // kg or lb?
```

**Sniff.** On `+` lines: a public/internal function or property using `Double`/`Int`/`Float` for a physical quantity, a duration, a currency, or an identifier — recognizable from the parameter name (`temperature`, `weight`, `duration`, `timeout`, `id`).

**Fix.** Wrap it in a type that carries the unit. A `struct` with one stored property costs nothing at runtime and makes the mistake unrepresentable.

```swift
struct Celsius: Hashable, Comparable { let value: Double }

func setTargetTemperature(_ target: Celsius)
setTargetTemperature(Celsius(value: 96))
```

For durations and dates, Swift already ships the right types — use `Duration` / `TimeInterval` / `Date`, not a bare `Int` of "seconds".

**In an SDK's public surface this is stricter and is owned elsewhere** — see [`sdk/public-api-contract.md`](../sdk/public-api-contract.md); post one finding, not two.

---

## P1 — `switch` over an internal enum closed with `default:`

`default:` disables the one thing enums buy you: the compiler telling every call site when a case is added. Add `.stone` to `UnitSystem` and every `default`-terminated switch keeps compiling while quietly doing the wrong thing.

```swift
switch unit {
case .imperial: return "lb"
case .metric:   return "kg"
default:        return ""        // new case → "" forever, no warning
}
```

**Sniff.** A `switch` on a project-owned `enum` with a `default:` (or `@unknown default:` on a non-frozen *imported* enum is fine — that's the correct form) returning a neutral value, on `+` lines.

**Fix.** List the cases. If a group genuinely shares behaviour, name them: `case .metric, .imperial:` — explicit, and still a compile error when a third arrives. Keep `@unknown default:` only for enums declared in a framework you don't own.

---

## P2 — `class` where a `struct` fits, or a non-`final` `class`

Reference semantics are a contract: shared identity, mutation visible to every holder, and a retain-cycle surface. A model that is just data should be a `struct` — value semantics remove a whole class of "who mutated this?" bugs and make it `Sendable` for free. And a `class` that nobody subclasses should be `final`: it documents intent and lets the compiler devirtualize.

```swift
class BabyProfile {           // pure data, no identity, no inheritance
    var name: String
    var birthday: Date
}
```

**Sniff.** On `+` lines: a `class` with only stored properties and no inheritance, no `deinit`, no identity requirement, and not an `ObservableObject`/`NSObject` subclass → should be a `struct`. A non-`final` `class` with no subclass in the module → should be `final`.

**Fix.** `struct BabyProfile { … }`, or `final class` when reference semantics are genuinely required (observation, shared mutable state, Objective-C interop, a deliberate identity).

**Do NOT flag** `ObservableObject` view models, `NSObject` subclasses required by a delegate protocol, or a type explicitly documented as reference-semantic.

---

## P2 — Naming that departs from the Swift API Design Guidelines

Swift naming is unusually prescriptive, and the guidelines are the shared standard — a name that ignores them costs every reader a translation step.

| Kind | Convention | Good | Bad |
|---|---|---|---|
| Method with a side effect | imperative verb phrase | `refreshDevices()`, `connect(to:)` | `deviceRefresh()`, `doConnect()` |
| Method returning a value | noun phrase, **no** `get` prefix | `distance(to:)`, `sortedReadings()` | `getDistance()`, `retrieveReadings()` |
| Mutating / non-mutating pair | `-ed`/`-ing` for the non-mutating one | `sort()` / `sorted()` | `sort()` / `sortImmutable()` |
| Boolean property or method | reads as an assertion | `isConnected`, `hasPendingWrite`, `canRetry` | `connected2`, `checkConnection` |
| Type / protocol | `UpperCamelCase`; protocols name a capability (`-able`/`-ing`) or a thing | `TemperatureReadable`, `KettleDevice` | `IKettleDevice`, `KettleDeviceProtocolClass` |
| Enum case | `lowerCamelCase` | `case pairing` | `case Pairing`, `case PAIRING` |
| Constant | `lowerCamelCase` (Swift has no SCREAMING_CASE) | `static let maxRetryCount = 3` | `static let MAX_RETRY_COUNT = 3` |
| Argument labels | make the call read as a phrase | `move(from: a, to: b)` | `move(a, b)`, `moveWithFromAndTo(...)` |

Don't abbreviate (`btn`, `mgr`, `tmp`, `idx`), don't repeat the type in the name (`nameString`, `deviceArray`), and don't prefix protocols with `I` — that's a C#/Java habit, not Swift. Where a repo has an established prefix convention for a library (e.g. a `GG` module prefix on public SDK types), that convention wins.

**Sniff.** On `+` lines: a `get`-prefixed accessor; a boolean without an `is`/`has`/`can`/`should` reading; an `I`-prefixed or `…ProtocolClass` protocol; `SCREAMING_SNAKE_CASE` constants; abbreviations; a type name repeated in a property name; an argument-label-free multi-parameter call that reads as a list of anonymous values.

**Fix.** Rename — Xcode's refactor handles the call sites. When the correct name is longer, take the longer name; clarity at the point of use is the guidelines' stated priority.

---

## P2 — `var` for a binding that is never mutated

`let` is a fact the reader gets for free; `var` is a promise of change the code doesn't keep, and it silently permits a later mutation nobody reviews. It also blocks `Sendable`/value-semantics reasoning.

**Sniff.** `var` on a `+` line with no reassignment or mutating call on that binding in scope; a `var` stored property on a `struct` that is only set in `init`.

**Fix.** `let`. For a `struct` property set only at construction, `let` also makes the type immutable-by-default, which is usually what the model wants.

---

## P2 — Magic literal instead of a named constant

An inline `0.85`, `44`, `3`, or `"user_profile_v2"` is a decision with no recorded reason, repeated wherever it's needed and impossible to change confidently.

**Sniff.** Numeric literals other than `0`/`1`/`-1` in expressions on `+` lines (timeouts, retry counts, thresholds, layout values that aren't design-token-backed), or the same literal in ≥3 places.

**Fix.** A named `static let` on the owning type, or a small `enum` used purely as a namespace (which can't be instantiated by accident):

```swift
private enum RetryPolicy {
    static let maxAttempts = 3
    static let backoff: Duration = .milliseconds(250)
}
```

**Do NOT flag** values a design-token system already owns (flag those under the token rule instead), or numbers that are self-evidently the data.

---

## P2 — Missing access control on a new type or member

Everything defaults to `internal`. In an app that's usually harmless; in a framework/SDK target it silently widens what you must keep working, and in any target it hides the author's intent about what's an implementation detail.

**Sniff.** A new `class`/`struct`/`enum`/`func`/`var` on `+` lines with no access modifier in a file whose siblings annotate theirs; or a `public` member added to a library target without a corresponding doc/changelog entry.

**Fix.** Mark implementation details `private`/`fileprivate`, module surface `internal` (explicitly, if the file's convention is to be explicit), and library surface `public` — for an SDK, only after the public-API contract check in [`sdk/public-api-contract.md`](../sdk/public-api-contract.md) is satisfied.

---

## Nit — `protocol` not used where a seam is obviously needed

A concrete dependency hardcoded inside a type (a `URLSession`, a `CBCentralManager`, a store) makes the type untestable without the real thing. Swift's answer is a small protocol plus injection — not a subclass or a `#if DEBUG` branch.

**Sniff.** A new type that constructs a concrete network/BLE/persistence dependency inline and has no injection point, on `+` lines.

**Fix.** Extract the minimum protocol the type actually uses and inject it (default argument keeps call sites unchanged). For BLE specifically the seam already exists — `BluetoothPeripheralProtocol`, see [`sdk/ble-core-and-concurrency.md`](../sdk/ble-core-and-concurrency.md); cite that rather than inventing a new one. **Do NOT** flag a protocol added for its own sake: a one-implementation protocol with no test or substitution need is indirection, not design.
