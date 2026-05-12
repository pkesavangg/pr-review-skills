---
name: debug-issue
description: Investigate a bug before coding a fix. Use when the user says "debug this", "find the root cause", "why is this failing", or when a ticket is ambiguous and needs technical narrowing before implementation.
---

Debug an issue methodically and produce a root-cause-oriented implementation path with a structured Root Cause Note.

The issue is: $ARGUMENTS

## Instructions

### 1 — Define the Failure Shape

Extract from the user or ticket:
- **Expected behavior:** what should happen
- **Actual behavior:** what is observed
- **Affected feature/module:** Dashboard, Entry, ScaleSetup, etc.
- **Failure category:** UI rendering | Service/business logic | API/network | Persistence (SwiftData) | Dependency Injection | Concurrency (async/await/actor) | Environment/config

Examples of failure shapes:
- **UI issue:** Screen not rendering, button tap has no effect, text/colors wrong
- **Service issue:** Data not syncing, cache stale, business logic returning wrong value
- **API issue:** Network timeout, 4xx/5xx error, response parsing failure
- **SwiftData issue:** Data not persisting, query returns empty, migration failure, actor crossing error
- **Concurrency issue:** race condition, actor isolation violation, task cancellation
- **DI issue:** Dependency not found at runtime (fatalError), wrong instance injected

### 2 — Inspect the Execution Path (Max 8 Reads)

To avoid over-reading, form a hypothesis after 3 reads, then target the next 5 reads to validate it. Read the likely path from entry point to side effect:
- screen/store (`Features/{Feature}/Stores/`) → service → repository/API → shared infra
- For UI issues: view hierarchy → store property → store update trigger
- For API issues: Endpoint case → HTTPClient.send → error handling
- For SwiftData issues: ModelContext → query → CRUD operation → migration
- For DI issues: @Injector usage → ServiceRegistry registration → DependencyContainer

**Stop reading when you can form a hypothesis.** Don't read every related file.

### 3 — Look for Existing Signals

Check (in order of likelihood):
- **Logging:** `LoggerService.info/error/success` calls in execution path. Server logs may be available via admin dashboard if API-related.
- **Tests:** related tests in `meAppTests/Features/...` or `meAppTests/Support/` — may show expected behavior
- **Mocks:** existing mocks in `meAppTests/Support/Mocks/` — reveal layer boundaries
- **Recent patterns:** nearby code using same service/API/storage layer
- **Known pitfalls:** check CLAUDE.md Gotchas section if touching SwiftData, DI, or Keychain

For **SwiftData-specific** issues, check:
- SwiftData `@Model` on screen — are models being crossed actor boundary unsafely? Feature code should read `AccountSnapshot` / `DeviceSnapshot` / `EntrySnapshot`, not the `@Model`. A `@Model` reaching a feature store is a strong signal the snapshot boundary was violated somewhere.
- `ModelContext` — is CRUD happening on @MainActor?
- Recent migrations in `AccountMigrationService` — could be incomplete

For **`EXC_BAD_ACCESS` / `_KKMDBackingData` / PAC-failure crashes in stack traces:**
- Strong fingerprint for an `@Model` property read on a thread that doesn't own its `ModelContext`. Historical production crashes (v5.0 Build 19) showed `account.accessToken.getter` and `account.expiresAt.getter` on background threads — the fix was the snapshot migration. If you see this pattern in a new crash, look for a recently added `Task { ... await someService.fetchFoo() ... foo.someField ... }` where `foo` is still an `@Model`.

For **concurrency-specific** issues, check:
- `@MainActor` annotations on Stores and Services
- `@unchecked Sendable` types (historically `DeviceDiscoveryEvent`; now properly `Sendable` after the `DeviceSnapshot` migration — if you see one, question whether it should still be unchecked)
- `Task { ... }` or `async let` — are results escaping to wrong actor?
- Any `let foo = someModel; Task { use(foo) }` where `someModel` is an `@Model` — capture `foo.toSnapshot()` instead.

### 4 — Produce a Root Cause Note

ALWAYS use this exact template before proceeding to fix:

```
## Root Cause Note

**Issue:** [1-line restatement of the user's problem]

**Category:** [UI | Service | API | SwiftData | Concurrency | DI | Environment]

**Most Likely Cause:**
[Paragraph: the single most probable cause and why. Reference specific file:line or code pattern.]

**Competing Hypotheses:**
- [Alternative 1 if still uncertain, and how to rule it out]
- [Alternative 2 if still uncertain, and how to rule it out]

**Files to Change:**
- [Path]: [specific change — add method, fix logic, add migration, etc.]
- [Path]: [specific change]

**Verification:**
[How to test the fix — run unit test, build, manual steps, or API call pattern]

**Next Skill:** [/fix-bug | /add-endpoint | /wire-service | /storage-change | /swift-concurrency | /swiftdata | /wire-navigation | /review-regression]
```

### 5 — Recommend Next Step

| Root cause category | Next skill |
|----------------|-----------|
| Isolated bug (1–3 files, logic error) | `/fix-bug` |
| Missing endpoint or API call | `/add-endpoint` |
| DI misconfiguration or missing service | `/wire-service` |
| SwiftData model, query, or persistence issue | `/storage-change` then `/swiftdata` if actor crossing |
| Concurrency (race, actor isolation, task cancellation) | `/swift-concurrency` |
| Navigation not wired or route missing | `/wire-navigation` |
| Full feature implementation needed | `/create-prd` or `/feature-slice` |
| Regression risk in existing code | `/review-regression` |
| Accessibility failure | `/review-accessibility` |
