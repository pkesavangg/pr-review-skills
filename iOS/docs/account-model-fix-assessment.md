# Account Model — Fix Assessment

Analysis of the 8 weaknesses identified in `account-model-deep-dive.md`.
For each issue: what changes, how many files, and what breaks.

---

## Verdict at a Glance

| # | Issue | Files | Regression Risk | Verdict |
|---|---|---|---|---|
| 4 | Silent nil child model mutations | `AccountService.swift` | **None** | Fix now |
| 7 | Token hydration coupling | `AccountService.swift` | **Low** | Fix now |
| 2 | `updatePublishedState()` called 37 times | `AccountService.swift` | **Medium** | Fix after test audit |
| 3 | Sequential sync API calls | `AccountService.swift` | **Medium** | Fix after test audit |
| 1 | All SwiftData on main thread | 10+ files | **Very high** | Accept |
| 5 | Account is a god object | 15+ files | **Very high** | Accept |
| 6 | No conflict resolution | iOS + Server | **N/A** | Accept |
| 8 | SwiftDataWorker doesn't cover Account | — | **N/A** | Accept |

**Only 1 production file changes across all viable fixes: `AccountService.swift`.**

---

## Fixes to Apply

---

### Fix 4 — Silent Nil Child Model Mutations

**Problem:** Every child model mutation uses optional chaining. If the relationship is `nil` (corruption, schema migration gap), the write silently does nothing — no error, no log.

```swift
// Current — silent no-op if goalSettings is nil
localAccount.goalSettings?.goalType = goal.type
localAccount.goalSettings?.goalWeight = goal.goalWeight
```

**Change:** Add a `guard let` before each child model mutation block and log a warning when the relationship is unexpectedly absent.

```swift
// After fix — failure is observable
guard let goalSettings = localAccount.goalSettings else {
    logger.log(level: .warning, tag: tag,
               message: "goalSettings is nil for accountId=\(localAccount.accountId), skipping mutation")
    return
}
goalSettings.goalType = goal.type
goalSettings.goalWeight = goal.goalWeight
```

| | |
|---|---|
| **Files changed** | `AccountService.swift` |
| **Sites** | 35 optional-chaining mutation sites across `createGoal`, `updateBodyComp`, `updateDashboardType`, `updateIntegrations`, `updateNotifications`, `updateWeightless`, `updateStreak`, `syncUnsyncedAccounts` |
| **Behaviour change** | None — guard + log only. If the relationship was nil before, it still returns early. Now you can see it in logs |
| **Regression risk** | **None** |
| **Tests broken** | 0 |

---

### Fix 7 — Token Hydration Coupling

**Problem:** Every fetch path must manually call `hydrateTokensInAccount()` or `account.accessToken` will be `nil`. There are 4 call sites today. A future contributor adding a fifth fetch path will silently break token access.

```swift
// Current — manual hydration at every call site
func fetchAccount(byId id: String) async throws -> Account? {
    guard let account = try await localRepo.fetchAccount(byId: id) else { return nil }
    hydrateTokensInAccount(account)   // ← must remember this
    return account
}

func fetchAllAccounts() async throws -> [Account] {
    let accounts = try await localRepo.fetchAllAccounts()
    for account in accounts {
        hydrateTokensInAccount(account)   // ← must remember this
    }
    return accounts
}
```

**Change:** Extract two private helpers that always hydrate, then route all internal fetches through them.

```swift
// After fix — hydration is automatic, impossible to forget
private func fetchAccountHydrated(byId id: String) async throws -> Account? {
    guard let account = try await localRepo.fetchAccount(byId: id) else { return nil }
    hydrateTokensInAccount(account)
    return account
}

private func fetchAllAccountsHydrated() async throws -> [Account] {
    let accounts = try await localRepo.fetchAllAccounts()
    accounts.forEach { hydrateTokensInAccount($0) }
    return accounts
}

// Public method now delegates to the hydrating helper
func fetchAccount(byId id: String) async throws -> Account? {
    try await fetchAccountHydrated(byId: id)
}
```

The 4 raw call sites in `init`, `fetchAccount(byId:)`, `fetchAllAccounts()`, and `updatePublishedState()` are replaced with calls to the hydrating helpers.

| | |
|---|---|
| **Files changed** | `AccountService.swift` |
| **Sites** | 4 call sites consolidated into 2 private helpers |
| **Behaviour change** | None — hydration already happened at these 4 sites |
| **Regression risk** | **Low** |
| **Tests broken** | 0 — `MockAccountService` does not exercise the real hydration path |

---

### Fix 2 — `updatePublishedState()` Called 37 Times

**Problem:** Every mutating method ends with a full `fetchAllAccounts()` table scan. Several methods call it twice (once in the success path, once in the offline fallback). The `init()` startup sequence calls it twice in series.

**Current call count: 37 across the file.**

Breakdown of redundant calls:

| Location | Calls | Problem |
|---|---|---|
| `init()` startup sequence | 2 | Second call immediately follows the first |
| `updateStreak()` | 2 | Called in both success and offline paths — one is always the final call |
| `updateWeightless()` | 2 | Same pattern as streak |
| `updateNotifications()` | 2 | Same pattern |
| `updateDashboardType()` | 2 | Same pattern |
| `updateIntegrations()` | 2 | Same pattern |
| `deleteHealthIntegration()` | 2 | Same pattern |
| `updateDashboardMetrics/Progress` | 2 | Success + offline, only one executes |

**Change:** Remove the duplicate call from offline fallback branches — state is already updated in the success path. Consolidate the double call in `init()`. Target: reduce from 37 to ~22 calls, eliminating only those where two calls are guaranteed to fire back-to-back in the same execution path.

```swift
// Before — updatePublishedState called in both branches
do {
    let response = try await apiRepo.patchStreak(...)
    localAccount.update(from: response)
    try await updateAccountClearingTokens(localAccount)
    try await updatePublishedState()   // ← success path
    return localAccount
} catch {
    if HTTPError.isNetworkError(error) {
        localAccount.streaksSettings?.isStreakOn = isStreakOn
        localAccount.isSynced = false
        try await updateAccountClearingTokens(localAccount)
        try await updatePublishedState()   // ← offline path
        return localAccount
    }
    throw error
}

// After — one call per method exit, not per branch
do {
    let response = try await apiRepo.patchStreak(...)
    localAccount.update(from: response)
    try await updateAccountClearingTokens(localAccount)
} catch {
    guard HTTPError.isNetworkError(error) else { throw error }
    localAccount.streaksSettings?.isStreakOn = isStreakOn
    localAccount.isSynced = false
    try await updateAccountClearingTokens(localAccount)
}
try await updatePublishedState()   // ← single call at the end
return localAccount
```

| | |
|---|---|
| **Files changed** | `AccountService.swift` |
| **Sites** | ~15 redundant call sites removed |
| **Behaviour change** | None for the happy path. Offline path publishes state exactly once instead of once per branch |
| **Regression risk** | **Medium** — removing a call that a test flow checks will show stale `@Published` state |
| **Tests broken** | Audit `AccountServiceTests.swift` before and after. Tests that assert `activeAccount` state after an offline-path call must still pass |

---

### Fix 3 — Sequential API Calls in `syncUnsyncedAccounts()`

**Problem:** When offline data is present, `syncUnsyncedAccounts()` fires up to 10 network calls in strict series. Each `try await` blocks the next.

```
updateProfile()          → waits for response
updateBodyComp()         → waits for response
updateNotifications()    → waits for response
updateDashboardType()    → waits for response
updateDashboardMetrics() → waits for response
updateStreak()           → waits for response
updateWeightless()       → waits for response
createGoal()             → waits for response
updateIntegrations()     → waits for response / deleteHealthIntegration()
```

**Dependency analysis:** Not all calls are independent.

| Call | Can run in parallel? | Reason |
|---|---|---|
| `updateProfile` + `updateBodyComp` | **No** — sequential | Both write to `weightSettings` (weightUnit, height, activityLevel). Parallel writes risk overwriting each other |
| `updateNotifications` | **Yes** | Writes only to `notificationSettings` |
| `updateDashboardType` | **Yes** | Writes only to `dashboardSettings.dashboardType` |
| `updateDashboardMetrics` | **Yes** | Writes only to `dashboardSettings.dashboardMetrics` |
| `updateStreak` | **Yes** | Writes only to `streaksSettings` |
| `updateWeightless` | **Yes** | Writes only to `weightlessSettings` |
| `createGoal` | **Yes** | Writes only to `goalSettings` |
| `updateIntegrations` | **Yes** | Writes only to `integrationSettings` |

**Change:** Keep `updateProfile` → `updateBodyComp` sequential. Group the remaining 7 independent calls into a `withThrowingTaskGroup`.

```swift
// After fix — parallel independent calls
try await updateProfile(profile)
try await updateBodyComp(bodyComp)

try await withThrowingTaskGroup(of: Void.self) { group in
    if shouldSyncNotifications {
        group.addTask { try await self.updateNotifications(notifications: notifications) }
    }
    if shouldSyncDashboardType {
        group.addTask { try await self.updateDashboardType(type: dashboardType) }
    }
    if shouldSyncDashboardMetrics {
        group.addTask { try await self.updateDashboardMetrics(metrics: metrics) }
    }
    if shouldSyncStreak {
        group.addTask { try await self.updateStreak(isStreakOn: isStreakOn, streakTimestamp: streakTimestamp) }
    }
    if shouldSyncWeightless {
        group.addTask { try await self.updateWeightless(...) }
    }
    if shouldSyncGoal {
        group.addTask { try await self.createGoal(goal) }
    }
    if shouldSyncIntegrations {
        group.addTask { try await self.updateIntegrations(integrationType: .healthKit) }
    }
    try await group.waitForAll()
}
```

**Important side effect to handle:** Each individual update method internally calls `updatePublishedState()`. With 7 concurrent tasks all calling it simultaneously, you get 7 concurrent mutations on `@Published` properties on the `@MainActor`. The parallel calls must be stripped of their internal `updatePublishedState()` call when invoked from `syncUnsyncedAccounts()`, with a single call at the end of the sync. This requires either an internal overload or a flag parameter.

| | |
|---|---|
| **Files changed** | `AccountService.swift` |
| **Sites** | `syncUnsyncedAccounts()` method body (lines 828–999) |
| **Behaviour change** | 7 network calls fire concurrently instead of sequentially. Total sync time drops from sum-of-calls to max-of-calls |
| **Regression risk** | **Medium** — concurrent `@Published` mutations must be handled. State consistency after partial failure in the task group must be verified |
| **Tests broken** | `AccountServiceTests.swift` sync tests — ordering-dependent assertions will need updating |

---

## Issues to Accept (Do Not Fix)

---

### Weakness 1 — All SwiftData on Main Thread

Moving `AccountRepository` to a background `@ModelActor` would require every child model relationship to be extracted into a `Sendable` value type before crossing the actor boundary — the same pattern `SwiftDataWorker` uses for `Entry`.

**Scope if attempted:**
- `AccountRepository.swift` — rewrite as `@ModelActor`
- `AccountService.swift` — all 35+ child model accesses become `async` extractions
- `Account.swift` + all 7 child model files — add `Sendable` value-type mirrors
- All feature stores — update async call sites

The app supports a maximum of 3 accounts. A `FetchDescriptor<Account>` over 3 records takes under 0.5ms. The jank risk is theoretical. **Not worth the overhaul.**

---

### Weakness 5 — Account is a God Object

Splitting `AccountService` and extracting `toAccountDTO()` / `update(from:)` into separate mapper types would touch:
- `Account.swift`, all 7 child model files
- `AccountService.swift` (complete restructure)
- All 6+ feature stores that call service methods
- `MockAccountService.swift` and all other test mocks
- All protocol definitions

The `// swiftlint:disable type_body_length` suppression is already in place with a documented justification. This is a known, accepted trade-off — not a bug. **Not worth the risk.**

---

### Weakness 6 — No Conflict Resolution

Detecting and resolving conflicts between offline local writes and concurrent server changes requires the backend to return last-modified timestamps and the client to compare before overwriting. This is a server contract change, not an iOS-only fix. **Out of scope.**

---

### Weakness 8 — SwiftDataWorker Doesn't Cover Account

`Account` has 1–3 records. Fetching it on the main actor takes under 1ms. Adding a background actor for Account would introduce the same value-type extraction complexity as Weakness 1 with no measurable benefit. **Accept as a design trade-off.**

---

## Recommended Fix Order

```
1. Fix 4 — Silent nil logging       (zero risk, immediate observability gain)
2. Fix 7 — Token hydration helpers  (low risk, prevents future bugs)
3. Audit AccountServiceTests.swift  (understand what the tests assert before touching state)
4. Fix 2 — Reduce updatePublishedState calls  (medium risk, run tests after)
5. Fix 3 — Parallel sync calls      (medium risk, most complex, run tests after)
```
