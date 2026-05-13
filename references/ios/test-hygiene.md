# Test Hygiene — flake patterns & DI bypass

Generic test-quality rules for any iOS test target. Focus is on flake sources and DI-container bypass — both project-agnostic. Project-specific helpers (`makeSUT()`, `TestDependencyContainer.reset()`, etc.) are intentionally out of scope; projects vary in how they name those.

---

## P1 — `Thread.sleep` / arbitrary `Task.sleep` in tests

Wall-clock waits are flaky on slow CI runners and a textbook source of intermittent failures. They also slow the whole suite.

```swift
@Test func reload_finishes() async throws {
    sut.reload()
    Thread.sleep(forTimeInterval: 0.2)   // flake
    #expect(sut.state == .loaded)
}
```

**Sniff.** In changed test files, find `Thread.sleep(`, `Task.sleep(nanoseconds:`, `Task.sleep(for:`.

Exceptions:

- Testing time-based behaviour under test (debounce, throttle) where the project genuinely has no `Clock` injection seam — flag, but mention "consider injecting a `Clock` so the test can advance time deterministically".
- `Task.sleep(for: .zero)` to yield — flag as **Nit**, suggest `Task.yield()` instead.

**Fix.** Poll via a `waitUntil(timeout:)` helper that uses `Task.yield()`, or inject a `Clock` and advance it manually with `ContinuousClock` / `SuspendingClock` test doubles.

---

## P1 — Production singleton (`*.shared`) accessed directly in a test

```swift
@Test func login_succeeds() async {
    let sut = LoginViewModel()
    await sut.login("u@x.com", password: "x")
    #expect(AnalyticsService.shared.lastEvent == .login)   // live singleton
}
```

Sharing live singletons across tests guarantees order-dependent flakiness and leaks state between cases. The whole point of a DI container is to provide a seam for test substitution — calling `.shared` directly defeats that.

**Sniff.** `+` lines in test files (under any path matching `*Tests/`) containing `<Identifier>.shared`. Cross-check the type: if its declaration lives under a production source dir (not `Mocks/`, not `TestHelpers/`, not `Preview Content/`), flag.

**Fix.** Route through the project's DI container. Inject a mock or stub via the existing override mechanism — name varies by project:

- `TestDependencyContainer.shared.register(.mock(of: AnalyticsService.self, ...))`
- `Container.shared.register { MockAnalyticsService() as AnalyticsServiceProtocol }`
- `Resolver.test { ... }`
- `withDependencies { $0.analytics = .mock } operation: { ... }`

If the project has no DI seam for this service, that's the bigger bug — flag the missing seam as a separate **P2**: "no override seam for `AnalyticsService` — adding tests against it will require either DI or a protocol".

---

## P1 — Disk-backed test store where the SDK supports in-memory

```swift
let container = try ModelContainer(for: Account.self)   // writes to disk between runs
```

Disk-backed stores in tests leak between runs and require teardown that's easy to forget. Every modern persistence SDK has an in-memory mode:

| SDK | In-memory flag |
|---|---|
| SwiftData | `ModelConfiguration(isStoredInMemoryOnly: true)` |
| Core Data | `NSPersistentStoreDescription.type = NSInMemoryStoreType` |
| Realm | `Realm.Configuration(inMemoryIdentifier: UUID().uuidString)` |
| GRDB | `DatabaseQueue()` (no path → in-memory) |

**Sniff.** Find `ModelContainer(for: ` calls in test files lacking `isStoredInMemoryOnly: true`. Same idea for Core Data / Realm / GRDB equivalents — match on the type name and check the configuration argument.

**Fix.** Pass the in-memory flag your SDK offers.

---

## P2 — `as!` force-cast in mock pop / dequeue logic

```swift
func dequeue() -> Account {
    return (queue.removeFirst() as! Account)   // crashes on type mismatch
}
```

A type mismatch deep in a mock surfaces as a `Swift.Bridge` crash inside the test framework, which is much harder to debug than a `fatalError` with a clear message.

**Sniff.** `as!` in any file under `*Tests/` or matching `Mock*`.

**Fix.**

```swift
guard let value = queue.removeFirst() as? Account else {
    fatalError("MockAccountQueue.dequeue: expected Account, got \(type(of: queue.first!))")
}
return value
```

---

## P2 — New test framework mixed with the existing one in the same target

Don't mix Swift Testing (`@Test` + `#expect`) and XCTest (`XCTestCase` + `XCTAssert`) in the same target without a clear reason — one framework's failures may run separately or report differently in CI dashboards.

**Sniff.** Determine the project's convention first:

```bash
rg -c '^import Testing|@Test\b' <test-target>/
rg -c '^import XCTest|XCTestCase|XCTAssert' <test-target>/
```

Whichever has the larger count is the project's convention. Flag new files in the changed set that diverge.

**Fix.** Match the project's convention. If a deliberate migration is in progress, it should be called out in the PR description.

---

## Nit — Test naming: behaviour over method-name

```swift
@Test func testLogin() { ... }              // what about login?
@Test func test_login() { ... }             // same — describes the SUT, not the case
@Test func loginWithValidCredentials_succeeds() { ... }   // preferred
```

Behaviour-focused names make failure dashboards readable and steer authors away from grab-bag tests that assert multiple unrelated behaviours.

**Sniff.** New `@Test func` (or `func test`) declarations matching `^test[A-Z]` or `^test_` followed by what's plainly a method name.

**Fix.** Rename as `<scenario>_<expected-outcome>` — e.g. `login_invalidPassword_emitsError`, `cache_eviction_keepsMRU`.

---

## Output

For each finding, emit one line:

```
[<file>:<line>] <severity> — Tests — <one-line rule> · <one-sentence fix>
```

The orchestrator handles de-duplication against swiftui-pro and prior reviewer comments (Step 4a.4) before posting.
