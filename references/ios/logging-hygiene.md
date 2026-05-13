# Logging Hygiene — over-logging & swallowed errors

Generic logging-placement rules. Applies to any project that has a logger wrapper of any shape — `logger.log(...)`, `os.Logger`, custom `LoggerService`, `Timber.d`, etc. These rules focus on *where* logging happens, not the API.

The cross-cutting block at [`review-pr.md` § 4a.3](../../.claude/commands/review-pr.md) already covers raw `print` / `NSLog` / `Log.d` (no wrapper). These rules catch the harder class: log calls in places that fire orders of magnitude more often than the author intended.

---

## P0 — Logging inside `var body: some View`

SwiftUI re-runs `body` on every state change. A `log(...)` call directly inside `body` floods logs and obscures real signal — easily 100×–1000× the call rate the author expected.

```swift
var body: some View {
    logger.log(level: .info, tag: "Login", message: "rendering")   // every render
    return VStack { ... }
}
```

**Sniff.** For each changed `.swift` file, find `var body:` declarations and scan the lexical body (until matching closing brace) for any call matching `\b(log|logger\.|Logger\(\)|os_log)\b\s*\(`. Account for nested computed views (`@ViewBuilder` helper properties) — same rule applies inside them.

**Kotlin/Compose analogue.** `@Composable fun MyScreen() { Log.d(...) }` re-runs every recomposition — same bug. If applying this file on the Compose path, treat any logging inside a `@Composable` body the same way.

**Fix.** Move to `.onAppear { ... }` / `.task { ... }`, an `.onChange(of:)` keyed on a discrete event, or a ViewModel method called from the view.

---

## P1 — Logging inside `.onChange(of:)` for a high-frequency value

```swift
.onChange(of: form.email.value) { _, newValue in
    logger.log(level: .info, message: "email changed to \(newValue)")   // per keystroke
}
```

`.onChange` fires on every value change. For a `TextField` binding, that's per keystroke. If debounced telemetry is genuinely wanted, debounce explicitly.

**Sniff.** Scan `+` lines for `.onChange(of: ` whose closure body calls `log(`. Exempt closures that include `debounce`, `throttle`, a `Timer` setup, or a guard that fires only on terminal states.

**Kotlin/Compose analogue.** `LaunchedEffect(textFieldValue)` whose body logs every change.

**Fix.** Move logging to a discrete event (`onSubmit`, `onCommit`, a button tap), or debounce upstream of the `.onChange`.

---

## P1 — Logging inside `for await` on a hot stream

```swift
for await tick in clock.values {
    logger.log(level: .info, message: "tick \(tick)")   // every tick
}
```

**Sniff.** For each `for await ... in` added in DIFF, scan the loop body for `log(`. Inspect the producer (right-hand side):

- `.publisher.values` on a `@Published` text-field property → flag
- Timer / `AsyncTimerSequence` / sensor stream → flag
- `AsyncStream` constructed from a `NotificationCenter` observer on a frequent notification → flag

**Fix.** Sample upstream (`.throttle(for:)`, `.removeDuplicates`), gate the log on a meaningful condition, or move the log to a derived discrete event.

---

## P1 — Empty `catch { }` swallowing errors

```swift
do {
    try saveAccount(account)
} catch {
    // silent — no log, no rethrow, no recovery
}
```

Exception cases are narrow: cleanup paths where failure is genuinely best-effort, and explicit `catch is CancellationError` handlers in async code.

**Sniff.** Find `} catch ` (or `} catch let ... `) blocks where the body is whitespace-only or comment-only. Filter out:

- `try?` / `try!` (not a catch block)
- Bodies that explicitly mention `CancellationError` or `Cancellation`
- Bodies adjacent to a comment containing `cleanup`, `best-effort`, `ignore` (signal of intent)

**Fix.** At minimum, log the error:

```swift
} catch {
    logger.log(level: .error, tag: tag, message: "saveAccount failed",
               data: ["error": String(describing: error)])
}
```

If the failure mode is recoverable, recover. If it's terminal for the user flow, surface it.

---

## P1 — Logging inside Combine `.handleEvents(receiveOutput:)` on a hot publisher

```swift
publisher
    .handleEvents(receiveOutput: { value in
        logger.log(level: .info, message: "got \(value)")   // if publisher emits frequently
    })
    .sink { ... }
```

**Sniff.** Find `.handleEvents(receiveOutput:` followed by a `log(` call. Inspect the chain root — if it's a `@Published` field, a `Timer`, a `NotificationCenter` publisher on a frequent notification, or a known stream, flag.

**Fix.** Apply `.throttle` / `.removeDuplicates` upstream, or move the log into the discrete `.sink { ... }` block downstream of any filtering.

---

## Nit — Two+ back-to-back `log()` calls with no semantic distinction

```swift
logger.log(level: .info, message: "starting upload")
logger.log(level: .info, message: "with \(items.count) items")   // fragmented
```

**Sniff.** In the diff, find two `log(` calls within 5 lines of each other on the same code path, same level, no branching between them.

**Fix.** Consolidate to one structured entry:

```swift
logger.log(level: .info, message: "starting upload",
           data: ["count": "\(items.count)"])
```

---

## Output

For each finding, emit one line:

```
[<file>:<line>] <severity> — Logging — <one-line rule> · <one-sentence fix>
```

The orchestrator handles de-duplication against swiftui-pro and prior reviewer comments (Step 4a.4) before posting.
