# Account Model — SwiftData Trigger Analysis

Why a single account change causes multiple UI re-renders, traced from service method down to every store subscriber.

---

## The Two Guns That Fire on Every Write

Every mutating method in `AccountService` ends with some combination of these two calls:

```swift
updatePublishedState()          // direct await — always fires
notifyActiveAccountChanged()    // spawns a Task — fires independently, shortly after
```

`notifyActiveAccountChanged` is the key problem:

```swift
func notifyActiveAccountChanged() {
    Task {                                              // ← NEW concurrent Task
        try await updatePublishedState(forceRefresh: true)
    }
}
```

It does not `await`. It spawns a fire-and-forget `Task` that calls `updatePublishedState(forceRefresh: true)` independently of the caller. The result: most write paths invoke `updatePublishedState` **twice** — once directly, once via the Task — at two slightly different points in time.

---

## What `updatePublishedState()` Does Each Time It Runs

```swift
func updatePublishedState(forceRefresh: Bool = false) async throws {
    allAccounts = try await localRepo.fetchAllAccounts()   // ← SwiftData full-table read
    for account in allAccounts { hydrateTokensInAccount(account) }
    let nextActive = allAccounts.first { $0.isActiveAccount == true }

    if forceRefresh || activeAccount?.accountId != nextActive?.accountId {
        activeAccount = nextActive                         // ← @Published fires
    }
}
```

Every call:
1. Reads **all accounts** from SwiftData (full table scan)
2. Assigns `allAccounts` → `@Published allAccounts` fires → subscribers re-render
3. Conditionally assigns `activeAccount` → `@Published activeAccount` fires → subscribers re-render

---

## Who Subscribes — 13 Stores Across the App

```
accountService.$allAccounts  ──►  SettingsStore
                                  AccountsStore

accountService.$activeAccount ──► SettingsStore          (populateEditFormIfNeeded + 2 other side effects)
                                  AccountsStore
                                  DashboardStore
                                  ProductTypeStore        (rebuild)
                                  GoalProgressViewModel
                                  EntryStore
                                  BottomTabBarViewModel
                                  WeightSnapshotCardViewModel
                                  BabySnapshotCardViewModel
                                  LandingStore
                                  IntegrationStore
                                  ContentViewModel
                                  GraphView
```

Every `@Published activeAccount` fire touches all 13. Every `@Published allAccounts` fire touches 2.

---

## Trigger Count Per Operation

### `updateStreak()` — toggling a streak (online path)

```
User taps streak toggle
│
├─ updateAccountClearingTokens()  →  context.save()         [SwiftData write #1]
│
├─ updatePublishedState()                                    [direct await]
│   ├─ fetchAllAccounts()         →  allAccounts = ...      [@Published #1 — 2 stores fire]
│   └─ accountId unchanged, forceRefresh: false             [activeAccount NOT reassigned]
│
└─ (done)

Total @Published fires: 1
Total store re-renders: 2
```

Streak is the **cleanest** case — no `notifyActiveAccountChanged`, no `forceRefresh`.

---

### `createGoal()` — saving a goal (online path)

```
User taps Save Goal
│
├─ updateAccountClearingTokens()  →  context.save()         [SwiftData write #1]
│
├─ updatePublishedState()                                    [direct await, forceRefresh: false]
│   ├─ fetchAllAccounts()         →  allAccounts = ...      [@Published #1 — 2 stores fire]
│   └─ accountId unchanged                                  [activeAccount NOT fired]
│
└─ notifyActiveAccountChanged()                             [spawns detached Task]
    └─ Task { updatePublishedState(forceRefresh: true) }
        ├─ fetchAllAccounts()     →  allAccounts = ...      [@Published #2 — 2 stores fire again]
        └─ activeAccount = nextActive  (forceRefresh: true) [@Published #3 — 13 stores fire]

Total @Published fires: 3
Total store re-renders: 17  (2 + 2 + 13)
```

---

### `updateProfile()` — saving profile (online path)

```
User taps Save Profile
│
├─ updateAccountClearingTokens()  →  context.save()         [SwiftData write #1]
│
├─ notifyActiveAccountChanged()                             [spawns detached Task — NOT awaited]
│
├─ updatePublishedState()                                   [direct await, forceRefresh: false]
│   ├─ fetchAllAccounts()         →  allAccounts = ...      [@Published #1 — 2 stores fire]
│   └─ accountId unchanged                                  [activeAccount NOT fired]
│
└─ Task (from notifyActiveAccountChanged) runs shortly after:
    └─ updatePublishedState(forceRefresh: true)
        ├─ fetchAllAccounts()     →  allAccounts = ...      [@Published #2 — 2 stores fire again]
        └─ activeAccount = nextActive  (forceRefresh: true) [@Published #3 — 13 stores fire]

Total @Published fires: 3
Total SwiftData reads: 2 full-table scans
Total store re-renders: 17  (2 + 2 + 13)

SettingsStore alone runs:
  - activeAccount = account
  - populateEditFormIfNeeded()
  - populateWeightlessFormIfNeeded()
  - syncHeightPickers()
  ... twice (once per activeAccount fire, and once per allAccounts fire)
```

---

### `updateBodyComp()` — changing weight unit (online path)

```
User changes unit from lbs to kg
│
├─ updateAccountClearingTokens()  →  context.save()         [SwiftData write #1]
│
├─ localRepo.fetchAccount()       →  freshAccount           [SwiftData read #1]
│
├─ activeAccount = freshAccount                             [@Published #1 — 13 stores fire]
│   └─ SettingsStore:  activeAccount set, form re-populated
│   └─ DashboardStore: unit display updated
│   └─ ... 11 other stores fire
│
├─ updatePublishedState(forceRefresh: true)                  [direct await]
│   ├─ fetchAllAccounts()         →  allAccounts = ...      [@Published #2 — 2 stores fire]
│   └─ activeAccount = nextActive  (forceRefresh: true)     [@Published #3 — 13 stores fire AGAIN]
│       └─ All 13 stores re-render a second time
│
└─ NotificationCenter.post(.accountWeightUnitChanged)       [additional broadcast]
    └─ Any NotificationCenter observer fires on top of the above

Total @Published fires: 3  (+1 NotificationCenter)
Total SwiftData reads: 2 (1 direct fetch + 1 inside updatePublishedState)
Total store re-renders: 28  (13 + 2 + 13)
```

This is the worst single-method case. Changing the weight unit re-renders every subscribing store **twice**.

---

### `syncUnsyncedAccounts()` — first launch after offline session (worst case)

When all 10 domains have unsynced changes, each internal update method runs with its own `updatePublishedState()` call:

```
App launches with offline data
│
├─ updateProfile()        → updatePublishedState × 2   (direct + notifyActiveAccountChanged Task)
├─ updateBodyComp()       → activeAccount = x1, updatePublishedState × 1 + NotificationCenter
├─ updateNotifications()  → updatePublishedState × 2   (direct + notifyActiveAccountChanged Task)
├─ updateDashboardType()  → updatePublishedState × 1
├─ updateDashboardMetrics()→updatePublishedState × 1
├─ updateStreak()         → updatePublishedState × 1
├─ updateWeightless()     → updatePublishedState × 2   (direct + notifyActiveAccountChanged Task)
├─ createGoal()           → updatePublishedState × 2   (direct + notifyActiveAccountChanged Task)
├─ updateIntegrations()   → updatePublishedState × 1
└─ final updatePublishedState() at end of sync → × 1

Total updatePublishedState() calls: ~14
Total SwiftData full-table reads:   ~14
Total @Published allAccounts fires: ~14  → AccountsStore + SettingsStore re-render 14× each
Total @Published activeAccount fires: ~8  → all 13 stores re-render 8× each
```

---

## Summary Table

| Operation | `updatePublishedState` calls | SwiftData reads | `allAccounts` fires | `activeAccount` fires | Store re-renders |
|---|---|---|---|---|---|
| `updateStreak` | 1 | 1 | 1 | 0 | 2 |
| `updateDashboardType` | 1 | 1 | 1 | 0 | 2 |
| `updateTokens` | 1 | 1 | 1 | 0 | 2 |
| `createGoal` | 2 | 2 | 2 | 1 | 17 |
| `updateProfile` | 2 | 2 | 2 | 1 | 17 |
| `updateNotifications` | 2 | 2 | 2 | 1 | 17 |
| `updateWeightless` | 2 | 2 | 2 | 1 | 17 |
| `updateBodyComp` | 2 | 2 | 2 | 2 | 28 |
| `syncUnsyncedAccounts` (full) | ~14 | ~14 | ~14 | ~8 | ~200 |

---

## Root Causes

### Root cause 1 — `notifyActiveAccountChanged` spawns an uncancelled Task

```swift
func notifyActiveAccountChanged() {
    Task {                                   // ← detached, no cancellation
        try await updatePublishedState(forceRefresh: true)
    }
}
```

Called immediately before or after a direct `updatePublishedState()` in the same method. The result is always two state-refresh cycles for every method that uses it. The Task runs on `@MainActor` so it doesn't race, but it still causes a second round of fetches and @Published fires.

### Root cause 2 — `forceRefresh: true` always re-assigns `activeAccount`

```swift
if forceRefresh || activeAccount?.accountId != nextActive?.accountId {
    activeAccount = nextActive    // fires even if the account object is identical
}
```

When `forceRefresh: true`, `activeAccount` is reassigned unconditionally — even when no field has changed. SwiftUI sees a new assignment and invalidates all observing views, even if the account data is identical.

### Root cause 3 — `updateBodyComp` assigns `activeAccount` directly AND via `updatePublishedState`

```swift
activeAccount = freshAccount                        // fire #1
try await updatePublishedState(forceRefresh: true)  // fire #2 — reassigns activeAccount again
```

Two separate assignments to `activeAccount` within the same method call.

### Root cause 4 — `updatePublishedState` does a full-table scan every call

```swift
allAccounts = try await localRepo.fetchAllAccounts()  // no predicate, reads every account
```

Each of the 14+ calls during sync reads every row in the Account table. With 1–3 accounts the cost is small, but the `@Published allAccounts` assignment fires every time regardless — causing AccountsStore and SettingsStore to re-run their Combine sinks on every call.

---

## What the Fix Looks Like

The fix has two parts:

**Part 1 — Remove `notifyActiveAccountChanged()` entirely**

Every call site that calls both `updatePublishedState()` and `notifyActiveAccountChanged()` should call `updatePublishedState(forceRefresh: true)` once instead:

```swift
// Before
try await updatePublishedState()
notifyActiveAccountChanged()           // spawns second Task

// After
try await updatePublishedState(forceRefresh: true)   // one call, one fetch, one set of fires
```

**Part 2 — Remove the direct `activeAccount = freshAccount` in `updateBodyComp`**

```swift
// Before
activeAccount = freshAccount                        // fire #1
try await updatePublishedState(forceRefresh: true)  // fire #2

// After — let updatePublishedState handle the assignment
try await updatePublishedState(forceRefresh: true)  // single fire
```

**Part 3 — Guard `forceRefresh` with an equality check on the object**

Rather than always reassigning on `forceRefresh: true`, only assign if the data actually differs:

```swift
// After
let accountChanged = activeAccount?.accountId != nextActive?.accountId
if forceRefresh || accountChanged {
    // Only assign if something the UI cares about actually changed
    activeAccount = nextActive
}
```

**Result of fix:**

| Operation | Before (fires) | After (fires) | Reduction |
|---|---|---|---|
| `updateStreak` | 1 | 1 | — |
| `createGoal` | 3 | 1 | 66% |
| `updateProfile` | 3 | 1 | 66% |
| `updateBodyComp` | 3 + NotifCenter | 1 + NotifCenter | 66% |
| `syncUnsyncedAccounts` (full) | ~14 | ~10 | ~30% |

---

## Files That Change

| File | Change |
|---|---|
| `Data/Services/AccountService.swift` | Remove `notifyActiveAccountChanged()`, replace call sites with single `updatePublishedState(forceRefresh: true)`. Remove direct `activeAccount = freshAccount` in `updateBodyComp`. |

No other production file changes. No test files need updating for these removals since `notifyActiveAccountChanged` has no direct test coverage in `AccountServiceTests`.
