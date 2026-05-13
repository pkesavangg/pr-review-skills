# Swift Concurrency — generic footguns

Rules flagging dangerous patterns in any Swift codebase using `async/await`, `Task`, `@MainActor`, `actor`, or `@Sendable`. Severity uses the orchestrator's taxonomy (`P0` / `P1` / `P2` / `Nit`).

These rules stay **project-agnostic** — no assumption about specific event-bus types, DI containers, or service-registration patterns. If the repo's `CLAUDE.md` documents a conflicting convention, prefer the repo's convention and skip the rule.

`swiftui-pro` flags SwiftUI-API misuse and force-unwraps but does **not** cover these concurrency rules. Apply them on top of swiftui-pro's findings, de-duping by `file:line`.

---

## P0 — `nonisolated` method on `@MainActor` type touching mutable state

A method or computed property marked `nonisolated` on a `@MainActor` type whose body reads or writes mutable instance state. The compiler doesn't always catch — `nonisolated` is an explicit opt-out of actor isolation, so correctness is on the author.

```swift
@MainActor final class ViewModel {
    var items: [Item] = []

    nonisolated func reset() {
        items = []   // data race — items is main-actor isolated, this method isn't
    }
}
```

**Sniff.** For each `@MainActor` type in the diff, scan its body for `nonisolated func` or `nonisolated var`. Acceptable: `nonisolated let` (immutable), `nonisolated init`, `nonisolated` returning a constant or a `Sendable` `let` property.

**Fix.** Drop `nonisolated`, make the property a `Sendable let`, or refactor the mutation through an async wrapper that hops to MainActor.

---

## P1 — `Task.detached` capturing self strongly

`Task.detached { ... self.x ... }` without `[weak self]` outlives the parent actor's lifetime and retains the parent. `Task.detached` is rarely the right choice — `Task { ... }` inherits the enclosing actor context and binds to its lifetime.

```swift
@MainActor final class ViewModel {
    func upload() {
        Task.detached {
            await self.process()   // strong self in detached task
        }
    }
}
```

**Sniff.**
```bash
rg -nB1 'Task\.detached' --type swift | rg -A5 'self\.|self\b'
```

**Fix.** Prefer `Task { ... }` (inherits actor + cancels with parent). If detachment is genuinely required, capture `[weak self]`.

---

## P1 — `DispatchQueue.main.async` inside an async function

```swift
func reload() async {
    let data = try await api.fetch()
    DispatchQueue.main.async {           // mixing eras
        self.items = data
    }
}
```

In an `async` context you're already in a structured-concurrency world. Mixing in GCD breaks cancellation propagation and re-introduces the legacy retain semantics structured concurrency was meant to eliminate.

**Sniff.** Find `async` function signatures, then within their bodies scan for `DispatchQueue.main.async` or `DispatchQueue.global(`.

**Fix.** `await MainActor.run { ... }` or hoist `@MainActor` to the function/enclosing type.

---

## P1 — `@Sendable` closure capturing a non-Sendable `class`

```swift
class Cache { var entries: [String: Data] = [:] }
let cache = Cache()
Task { @Sendable in
    cache.entries["k"] = data   // Sendable closure captures non-Sendable Cache by reference
}
```

The `@Sendable` annotation is a *claim* the compiler partly checks; capturing a mutable, non-Sendable reference type smuggles a race past the type system.

**Sniff.** For each `@Sendable` closure in the diff, list captured identifiers. Cross-check each captured type's declaration in the worktree — if it's a `class` without `Sendable` conformance and not wrapped in an `actor`, flag.

**Fix.** Make the captured type an `actor`, conform it to `Sendable` (`final class Cache: @unchecked Sendable` only as a last resort with a documented invariant), or capture a value-typed snapshot taken before the closure starts.

---

## P2 — `actor` declared with no shared mutable state

```swift
actor PriceFormatter {
    func format(_ amount: Decimal) -> String {   // actor with no instance state
        amount.formatted(.currency(code: "USD"))
    }
}
```

Actor isolation imposes runtime cost (re-entrancy, hop-to-actor) and forces every caller into `await`. With no mutable state to protect, `final class: Sendable` or `struct` is cheaper and easier to call.

**Sniff.** New `actor X { ... }` declarations in the diff with zero `var` instance properties (only `func` / `let`).

**Fix.** `final class PriceFormatter: Sendable` or `struct PriceFormatter`.

---

## P2 — `.sink {}.store(in: &cancellables)` in new code where `for await` fits

If the producer is an `AsyncSequence`, or a Combine `Publisher` with `.values` available, `for await` cancels with its enclosing task and avoids the cancellable-bag lifetime gotchas.

```swift
// new code
service.events
    .receive(on: DispatchQueue.main)
    .sink { event in self.handle(event) }
    .store(in: &cancellables)

// preferred
.task {
    for await event in service.events.values {
        handle(event)
    }
}
```

Exception: bridging from a third-party Combine API that doesn't expose `.values`, or projects that have a documented Combine-first convention in `CLAUDE.md`.

**Sniff.** Count `.sink(` + `.store(in: ` pairs added in DIFF.

**Fix.** `for await ... in publisher.values` inside a `Task` cancelled in `deinit`, or under SwiftUI's `.task` modifier which cancels on disappear.

---

## Nit — `@MainActor` on a service with no UI state

A non-UI service annotated `@MainActor`. Pushes every async call onto main and throttles the UI for no reason.

```swift
@MainActor final class HTTPClient { ... }   // no UI state, no reason for @MainActor
```

**Sniff.** `@MainActor` annotation on a type whose name matches `*Service`, `*Client`, `*Repository`, `*Store`, `*Manager`, AND whose body has no `@Published`, `ObservableObject`, `@State`, `@Observable`, or other UI-state markers.

**Fix.** Drop `@MainActor`. Let the call sites hop to main when they bind into UI (`.receive(on: DispatchQueue.main)`, or `await MainActor.run` at the publish boundary).

---

## Output

For each finding, emit one line in the orchestrator's findings list:

```
[<file>:<line>] <P0/P1/P2/Nit> — Concurrency — <one-line rule> · <one-sentence fix>
```

The orchestrator handles de-duplication against swiftui-pro and prior reviewer comments (Step 4a.4) before posting.
