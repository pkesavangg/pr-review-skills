# AccountSnapshot Pattern — Implementation Guide

Replacing `@Published var activeAccount: Account?` (a SwiftData reference type) with
`@Published var activeAccount: AccountSnapshot?` (a flat value-type struct).

This is the same approach Financia uses: the repository owns the managed object, the
service publishes a plain struct copy. Subscribers never touch the SwiftData model
directly.

---

## Table of Contents

1. [Why This Change](#1-why-this-change)
2. [How the Pattern Works](#2-how-the-pattern-works)
3. [Implementation — Step by Step](#3-implementation--step-by-step)
4. [Pros and Cons](#4-pros-and-cons)
5. [All Files Affected](#5-all-files-affected)
6. [Risk Assessment](#6-risk-assessment)
7. [Migration Checklist](#7-migration-checklist)
8. [Crash Coverage Verification](#8-crash-coverage-verification)
9. [Recommended Implementation Order](#9-recommended-implementation-order)

---

## 1. Why This Change

### Production crashes — the primary motivation

Two `EXC_BAD_ACCESS` crashes were reported in v5.0.0 (Build 19), both caused by
reading SwiftData `@Model` properties from a thread that does not own the model's
`ModelContext`. Full analysis: `CRASH_ANALYSIS.md`.

**Crash 1 — Sync process (April 10)**

```
Thread 8 (background):
  EntryService.performSync()
    → EntryRepositoryAPI.fetchOperations()
      → HTTPClient.makeRequest(...)
        → account.accessToken.getter        ← EXC_BAD_ACCESS SIGSEGV
```

`HTTPClient.getAccount()` returns the `Account` @Model from `accountService.activeAccount`
(a `@MainActor`-bound `ModelContext` object). `makeRequest` then reads `account.accessToken`
on a background thread. SwiftData's `_KKMDBackingData` is not thread-safe — crash.

**Crash 2 — Dashboard init (April 8)**

```
Thread 8 (background):
  DashboardStore.initializeDashboard()
    → AccountService.refreshAccount()
      → HTTPClient.send(...)
        → account.expiresAt.getter          ← EXC_BAD_ACCESS SIGSEGV (PAC failure)
```

Same pattern: `account.expiresAt` read off-actor. The CPU's pointer authentication
hardware detected a mismatched tag — the backing store value was corrupted by concurrent
`ModelContext` writes from `LoggerRepository` on other threads.

**Why the current "R1" convention fails:**

The code has `// R1` comments saying "Extract primitives from @Model before crossing
async boundaries":

```swift
// HTTPClient.swift:93 — the extraction IS the crash point
let expiresAt = account.expiresAt   // ← account is already off-actor here
let acctId = account.accountId      // ← this read can crash too
```

The extraction happens after the `Account` object has already been passed to the
background. The rule is correct, but it's unenforceable at the type level — a struct
passed instead of a managed object makes the entire class of crash impossible.

---

### The re-render problem

`Account` is a SwiftData `@Model class` — a **reference type**. When you mutate a field
on it (e.g. `account.firstName = "Jane"`), `@Published` does not fire because the
pointer hasn't changed. That is why three separate mechanisms exist today to work around
this:

| Workaround | Purpose |
|---|---|
| `notifyActiveAccountChanged()` | Spawns a detached `Task` that calls `updatePublishedState(forceRefresh: true)` to force a re-render |
| `forceRefresh: Bool` parameter | Bypasses the identity check and re-assigns `activeAccount` unconditionally |
| Direct `activeAccount = freshAccount` in `updateBodyComp` | Extra assignment to force observation before `updatePublishedState` runs |

These workarounds compound each other. A single weight-unit change currently fires
`@Published` **3 times** and re-renders all 13 subscribing stores up to **28 times**.
See `account-swiftdata-trigger-analysis.md` for the full breakdown.

### The Financia comparison

| | Financia | meApp (current) |
|---|---|---|
| Published type | `AccountInfo` (struct, value type) | `Account` (@Model class, reference type) |
| `@Published` fires when | Value changes — Swift detects it automatically | Only when pointer changes — requires manual workarounds |
| Workarounds needed | None | `notifyActiveAccountChanged`, `forceRefresh`, direct assignment |
| Cross-`await` safety | Safe — structs are `Sendable` | Unsafe — SwiftData objects must not cross actor boundaries |
| Optional chaining on child models | None — struct has flat fields | `account?.weightSettings?.weightUnit` — silent nil if relationship missing |

---

## 2. How the Pattern Works

### Before (current)

```
SwiftData context (main actor)
       │
       │  Account (@Model class) ──────────────── stored in SwiftData
       │       │
       │       │  same object pointer published
       ▼       ▼
AccountService
  @Published var activeAccount: Account?   ← reference to the managed object
       │
       │  13 feature stores observe this
       ▼
SettingsStore.activeAccount = account      ← holds same managed object
DashboardStore reads account?.weightSettings?.weightUnit
```

Problem: any internal field mutation on `Account` is invisible to `@Published`. Every
write needs a manual nudge.

### After (proposed)

```
SwiftData context (main actor)
       │
       │  Account (@Model class) ──────────────── still stored in SwiftData (unchanged)
       │       │
       │       │  AccountSnapshot(from: account)  ← copy all fields into a struct
       ▼       ▼
AccountService
  @Published var activeAccount: AccountSnapshot?  ← value type, Equatable
       │
       │  13 feature stores observe this
       ▼
SettingsStore.activeAccount = snapshot     ← plain struct, safe everywhere
DashboardStore reads snapshot?.weightUnit  ← flat field, no optional chain
```

`Account` + all 7 child `@Model` classes remain exactly as they are. SwiftData storage
is untouched. Only what gets **published** changes.

---

## 3. Implementation — Step by Step

---

### Step 1 — Create `AccountSnapshot.swift`

**File:** `iOS/meApp/Domain/Models/Domain/Auth/AccountSnapshot.swift`

```swift
import Foundation

/// A flat, immutable value-type copy of Account and all its child settings models.
/// Published by AccountService instead of the SwiftData @Model directly.
/// Safe to use across async boundaries and as Combine publisher payloads.
struct AccountSnapshot: Equatable, Sendable {

    // MARK: - Account core fields
    let accountId: String
    let email: String
    let firstName: String?
    let lastName: String?
    let gender: Sex?
    let height: String?
    let dob: String?
    let zipcode: String?
    let isLoggedIn: Bool
    let isExpired: Bool
    let isActiveAccount: Bool
    let fcmToken: String?
    let lastActiveTime: String?
    let isSynced: Bool
    let productTypes: [String]

    // MARK: - Flattened from WeightCompSettings
    let weightUnit: WeightUnit
    let weightHeight: String
    let activityLevel: ActivityLevel?

    // MARK: - Flattened from GoalSettings
    let goalType: GoalType?
    let goalWeight: Double?
    let initialWeight: Double?
    let goalPercent: Double?
    let goalIsSynced: Bool

    // MARK: - Flattened from StreaksSettings
    let isStreakOn: Bool
    let streakTimestamp: String?

    // MARK: - Flattened from WeightlessSettings
    let isWeightlessOn: Bool
    let weightlessWeight: Double?
    let weightlessTimestamp: String?

    // MARK: - Flattened from NotificationSettings
    let shouldSendEntryNotifications: Bool
    let shouldSendWeightInEntryNotifications: Bool

    // MARK: - Flattened from DashboardSettings
    let dashboardType: String?
    let dashboardMetrics: String?
    let progressMetrics: String?

    // MARK: - Flattened from IntegrationSettings
    let isHealthKitOn: Bool
    let isFitbitOn: Bool
    let isFitbitValid: Bool
    let isMfpOn: Bool
    let isMfpValid: Bool

    // MARK: - Tokens (in-memory only — sourced from Keychain, never persisted)
    let accessToken: String?
    let refreshToken: String?
    let expiresAt: String?
}
```

---

### Step 2 — Create `Account+Snapshot.swift`

**File:** `iOS/meApp/Domain/Models/DB/Account+Snapshot.swift`

This extension lives next to `Account.swift` and owns the single conversion path.
All relationship access happens here, safely on the main actor before any `await`.

```swift
import Foundation

extension Account {
    /// Converts the SwiftData Account and all its child models into a flat,
    /// Sendable AccountSnapshot. Call this only on the main actor while the
    /// model context is valid and before any await boundary.
    func toSnapshot(accessToken: String? = nil,
                    refreshToken: String? = nil,
                    expiresAt: String? = nil) -> AccountSnapshot {
        AccountSnapshot(
            // Core
            accountId:     accountId,
            email:         email,
            firstName:     firstName,
            lastName:      lastName,
            gender:        gender,
            height:        height,
            dob:           dob,
            zipcode:       zipcode,
            isLoggedIn:    isLoggedIn    ?? false,
            isExpired:     isExpired     ?? false,
            isActiveAccount: isActiveAccount ?? false,
            fcmToken:      fcmToken,
            lastActiveTime: lastActiveTime,
            isSynced:      isSynced      ?? true,
            productTypes:  productTypes,

            // WeightCompSettings
            weightUnit:    weightSettings?.weightUnit    ?? .lb,
            weightHeight:  weightSettings?.height        ?? "0",
            activityLevel: weightSettings?.activityLevel,

            // GoalSettings
            goalType:      goalSettings?.goalType,
            goalWeight:    goalSettings?.goalWeight,
            initialWeight: goalSettings?.initialWeight,
            goalPercent:   goalSettings?.goalPercent,
            goalIsSynced:  goalSettings?.isSynced        ?? false,

            // StreaksSettings
            isStreakOn:       streaksSettings?.isStreakOn       ?? false,
            streakTimestamp:  streaksSettings?.streakTimestamp,

            // WeightlessSettings
            isWeightlessOn:       weightlessSettings?.isWeightlessOn       ?? false,
            weightlessWeight:     weightlessSettings?.weightlessWeight,
            weightlessTimestamp:  weightlessSettings?.weightlessTimestamp,

            // NotificationSettings
            shouldSendEntryNotifications:
                notificationSettings?.shouldSendEntryNotifications         ?? true,
            shouldSendWeightInEntryNotifications:
                notificationSettings?.shouldSendWeightInEntryNotifications ?? false,

            // DashboardSettings
            dashboardType:    dashboardSettings?.dashboardType,
            dashboardMetrics: dashboardSettings?.dashboardMetrics,
            progressMetrics:  dashboardSettings?.progressMetrics,

            // IntegrationSettings
            isHealthKitOn: integrationSettings?.isHealthKitOn ?? false,
            isFitbitOn:    integrationSettings?.isFitbitOn    ?? false,
            isFitbitValid: integrationSettings?.isFitbitValid ?? false,
            isMfpOn:       integrationSettings?.isMfpOn       ?? false,
            isMfpValid:    integrationSettings?.isMfpValid    ?? false,

            // Tokens (in-memory, caller passes these after Keychain hydration)
            accessToken:  accessToken,
            refreshToken: refreshToken,
            expiresAt:    expiresAt
        )
    }
}
```

---

### Step 3 — Update `AccountServiceProtocol.swift`

**File:** `iOS/meApp/Domain/Services/AccountServiceProtocol.swift`

Change the four `Account`-returning or `Account`-typed members to `AccountSnapshot`.
The mutation methods (`signUp`, `logIn`, `updateProfile`, etc.) no longer need to
return `Account` — callers read the updated state from `activeAccount` after the call.

```swift
@MainActor
protocol AccountServiceProtocol {

    // MARK: - Published state (value types now)
    var activeAccount: AccountSnapshot? { get }
    var allAccounts: [AccountSnapshot] { get }
    var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { get }
    var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { get }

    // MARK: - Account Lifecycle
    func signUp(email: String, password: String, profile: Profile) async throws
    func logIn(email: String, password: String) async throws
    func logOut(accountId: String?, isAutoLogout: Bool) async throws
    func deleteAccount() async throws
    func deleteAllAccounts() async throws
    func removeAccountFromDevice(accountId: String) async throws
    func switchAccount(to accountId: String) async throws   // takes ID, not managed object
    func setActiveAccount(accountId: String) async throws   // takes ID, not managed object

    // MARK: - Account State
    func shouldDeferUnauthenticatedLanding() -> Bool
    func getActiveAccount() async throws -> AccountSnapshot?
    func getAllLoggedInAccounts() async throws -> [AccountSnapshot]
    func fetchAccount(byId id: String) async throws -> AccountSnapshot?
    func fetchAllAccounts() async throws -> [AccountSnapshot]

    // MARK: - Account Updates (return Void — read updated state from activeAccount)
    func createGoal(_ goal: Goal) async throws
    func updateProfile(_ profile: Profile, canSaveOffline: Bool) async throws
    func updateBodyComp(_ bodyComp: BodyComp) async throws
    func updateProductTypes(_ productTypes: [String]) async throws
    func updateTokens(_ tokens: Tokens, _ accountId: String?) async throws
    func updateDashboardType(type: DashboardType) async throws
    func updateIntegrations(integrationType: IntegrationType,
                            preferences: [String: AnyCodable]) async throws
    func updateNotifications(notifications: Notifications) async throws
    func updateDashboardMetrics(metrics: [String]) async throws
    func updateProgressMetrics(metrics: [String]) async throws
    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws
    func updateWeightless(isWeightlessOn: Bool,
                          weightlessTimestamp: String,
                          weightlessWeight: Double) async throws

    // MARK: - Password & Security
    func requestPasswordReset(email: String) async throws
    func updatePassword(oldPassword: String, newPassword: String) async throws

    // MARK: - Sync & Offline
    func refreshAllAccounts() async throws
    func refreshAccount(accountId: String?) async throws
    func logOutAllAccounts() async throws
    func syncUnsyncedAccounts() async throws

    // MARK: - Tokens
    func getActiveTokens() async throws -> Tokens
    func refreshTokens(accountId: String?) async throws -> Tokens
    func deleteHealthIntegration(_ type: IntegrationType) async throws
    func updatePublishedState() async throws     // forceRefresh parameter removed
}
```

---

### Step 4 — Update `AccountService.swift`

**Key changes only — the persistence logic is untouched.**

#### 4a. Change published property types

```swift
// Before
@Published var activeAccount: Account?
@Published var allAccounts: [Account] = []
var activeAccountPublisher: Published<Account?>.Publisher { $activeAccount }
var allAccountsPublisher: Published<[Account]>.Publisher { $allAccounts }

// After
@Published private(set) var activeAccount: AccountSnapshot?
@Published private(set) var allAccounts: [AccountSnapshot] = []
var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }
var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { $allAccounts }
```

#### 4b. Replace `updatePublishedState` — drop `forceRefresh`, drop `notifyActiveAccountChanged`

```swift
// Before — 45 lines, forceRefresh parameter, two assignment paths
func updatePublishedState(forceRefresh: Bool = false) async throws {
    allAccounts = try await localRepo.fetchAllAccounts()
    for account in allAccounts { hydrateTokensInAccount(account) }
    let nextActive = allAccounts.first { $0.isActiveAccount == true }
    if forceRefresh || activeAccount?.accountId != nextActive?.accountId {
        activeAccount = nextActive
        Theme.shared.setActiveAccount(nextActive?.accountId)
    }
}

// After — clean, one path, Equatable guards redundant fires
func updatePublishedState() async throws {
    let rawAccounts = try await localRepo.fetchAllAccounts()

    let snapshots: [AccountSnapshot] = rawAccounts.map { account in
        let tokens = keychainService.getTokens(for: account.accountId)
        return account.toSnapshot(
            accessToken:  tokens?.accessToken,
            refreshToken: tokens?.refreshToken,
            expiresAt:    tokens?.expiresAt
        )
    }

    allAccounts = snapshots                                      // @Published fires if list changed

    let nextActive = snapshots.first { $0.isActiveAccount }
    if activeAccount != nextActive {                             // Equatable — no spurious fires
        activeAccount = nextActive
        Theme.shared.setActiveAccount(nextActive?.accountId)
    }
}
```

#### 4c. Delete `notifyActiveAccountChanged` entirely

```swift
// DELETE this method — it no longer has a reason to exist
func notifyActiveAccountChanged() {
    Task { try await updatePublishedState(forceRefresh: true) }
}
```

Replace every call site of `notifyActiveAccountChanged()` with nothing —
`updatePublishedState()` now correctly fires `@Published` based on value equality.

#### 4d. Fix `updateBodyComp` — remove the extra direct assignment

```swift
// Before — assigns activeAccount twice
try await updateAccountClearingTokens(localAccount)
if let freshAccount = try await localRepo.fetchAccount(byId: localAccount.accountId) {
    if activeAccount?.accountId == freshAccount.accountId {
        activeAccount = freshAccount          // ← DELETE this block
    }
}
try await updatePublishedState(forceRefresh: true)

// After — single assignment through updatePublishedState
try await updateAccountClearingTokens(localAccount)
try await updatePublishedState()
```

#### 4e. Update `switchAccount` and `setActiveAccount` to take an ID

Feature stores must stop passing `Account` objects across async calls. The method
signatures change to take `accountId: String` instead.

```swift
// Before
func switchAccount(to account: Account) async throws { ... }

// After
func switchAccount(to accountId: String) async throws {
    guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
        throw AccountError.accountNotFound(id: accountId)
    }
    // ... existing logic using localAccount
}
```

#### 4f. Update `hydrateTokensInAccount` — now private implementation detail

Token hydration moves inside `updatePublishedState` (see 4b above) and is no longer
called at individual fetch sites. The four manual call sites are removed.

---

### Step 5 — Update `MockAccountService.swift`

**File:** `iOS/meAppTests/Support/Mocks/Services/MockAccountService.swift`

```swift
// Change published types to match the protocol
@Published var activeAccount: AccountSnapshot? = nil
@Published var allAccounts: [AccountSnapshot] = []
var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }
var allAccountsPublisher: Published<[AccountSnapshot]>.Publisher { $allAccounts }

// Helper for tests — build a snapshot without needing a SwiftData context
static func makeSnapshot(
    accountId: String = "test-id",
    email: String = "test@example.com",
    firstName: String? = "Test",
    weightUnit: WeightUnit = .lb,
    isActiveAccount: Bool = true
) -> AccountSnapshot {
    AccountSnapshot(
        accountId: accountId,
        email: email,
        firstName: firstName,
        // ... all other fields with safe defaults
        weightUnit: weightUnit,
        isActiveAccount: isActiveAccount,
        isLoggedIn: true,
        isExpired: false,
        // ... remaining fields
    )
}
```

---

### Step 6 — Update the 52 feature/service files (mechanical find-and-replace)

All access patterns change from nested optional chains to flat field access.

```swift
// Before                                          After
activeAccount?.weightSettings?.weightUnit      →   activeAccount?.weightUnit
activeAccount?.weightSettings?.height          →   activeAccount?.weightHeight
activeAccount?.weightSettings?.activityLevel   →   activeAccount?.activityLevel
activeAccount?.goalSettings?.goalType          →   activeAccount?.goalType
activeAccount?.goalSettings?.goalWeight        →   activeAccount?.goalWeight
activeAccount?.goalSettings?.initialWeight     →   activeAccount?.initialWeight
activeAccount?.streaksSettings?.isStreakOn     →   activeAccount?.isStreakOn
activeAccount?.streaksSettings?.streakTimestamp→   activeAccount?.streakTimestamp
activeAccount?.weightlessSettings?.isWeightlessOn → activeAccount?.isWeightlessOn
activeAccount?.weightlessSettings?.weightlessWeight→ activeAccount?.weightlessWeight
activeAccount?.dashboardSettings?.dashboardType→   activeAccount?.dashboardType
activeAccount?.dashboardSettings?.dashboardMetrics→ activeAccount?.dashboardMetrics
activeAccount?.integrationSettings?.isHealthKitOn→ activeAccount?.isHealthKitOn
activeAccount?.integrationSettings?.isFitbitOn →   activeAccount?.isFitbitOn
activeAccount?.notificationSettings?.shouldSendEntryNotifications →
                                                   activeAccount?.shouldSendEntryNotifications
```

Places that pass `account` (the managed object) to `switchAccount` or `setActiveAccount`:

```swift
// Before
try await accountService.switchAccount(to: account)

// After — pass the ID extracted before the Task boundary
let accountId = snapshot.accountId
Task {
    try await accountService.switchAccount(to: accountId)
}
```

---

## 4. Pros and Cons

### Pros

**1. Eliminates the class of crash seen in production (Crashes 1 & 2)**
The `Account` @Model object never leaves `AccountService`. Every consumer receives
an `AccountSnapshot` — a plain struct, `Sendable`, safe to read from any thread.
`HTTPClient.getAccount()` returns a struct instead of a managed object. Reading
`snapshot.accessToken` or `snapshot.expiresAt` on a background thread is a normal
property read, not a SwiftData fault — no `_KKMDBackingData` access, no crash.

After this migration, there is **zero remaining path** for an `Account` @Model
object to escape to a background thread through any public API:

| API | Before (unsafe) | After (safe) |
|---|---|---|
| `accountService.activeAccount` | `Account?` (managed object) | `AccountSnapshot?` (struct) |
| `accountService.allAccounts` | `[Account]` | `[AccountSnapshot]` |
| `accountService.fetchAccount(byId:)` | `Account?` | `AccountSnapshot?` |
| `accountService.getActiveAccount()` | `Account?` | `AccountSnapshot?` |
| `accountService.getAllLoggedInAccounts()` | `[Account]` | `[AccountSnapshot]` |
| All update methods (`updateProfile`, etc.) | Return `Account` | Return `Void` |

The only code that touches `Account` @Model objects after this change is
`AccountService` (internally) and `AccountRepository` — both `@MainActor`.

**2. `@Published` fires only when data actually changes**
`AccountSnapshot` is `Equatable`. Swift compares old and new values field-by-field.
If a profile save results in the same data (e.g. a no-op network response), zero fires.
If data changes, exactly one fire. No workarounds needed.

**3. `notifyActiveAccountChanged()` is deleted**
The entire mechanism — the detached `Task`, the `forceRefresh` parameter, the
`updatePublishedState` overload — goes away. The publishing model becomes:
write to SwiftData → call `updatePublishedState()` once → done.

**4. Re-render count drops 66–100%**

| Operation | Before | After |
|---|---|---|
| `updateProfile` | 3 `@Published` fires, 17 re-renders | 1 fire (if data changed), 0 if not |
| `updateBodyComp` | 3 fires + NotificationCenter | 1 fire + NotificationCenter |
| `createGoal` | 3 fires | 1 fire |
| `syncUnsyncedAccounts` (full) | ~14 fires | ~10 fires (one per update method) |

**5. Cross-`await` safety enforced by the type system**
Feature stores can safely capture a snapshot before a `Task`, use it inside the
task, and never worry about SwiftData context invalidation. The "extract primitives
before await" convention becomes structurally enforced — the compiler rejects passing
a non-`Sendable` `Account` @Model across actor boundaries, but `AccountSnapshot` is
`Sendable` by design.

**6. No more silent nil child model access**
`account?.weightSettings?.weightUnit` silently returns `nil` if the relationship
is missing. `snapshot.weightUnit` always has a value — the default is baked into
`toSnapshot()`. Nil-relationship bugs surface at conversion time, not at read time.

**7. `MockAccountService` becomes trivially easy to set up in tests**
Tests build an `AccountSnapshot` struct directly — no SwiftData context required.
No `ModelContainer`, no `PersistenceController`, no `@MainActor` juggling.

**8. Tokens are naturally included**
The snapshot carries `accessToken`, `refreshToken`, `expiresAt` as regular fields.
No separate `hydrateTokensInAccount()` call needed at each fetch site. The hydration
happens once inside `updatePublishedState()` and is baked into the snapshot.

---

### Cons

**1. Wide but mechanical change — 52 feature/service files + 7 infrastructure files**
Every file that reads child model data through optional chaining needs a one-line
update. The changes are repetitive but safe. There is no logic change — only the
access path changes. Four files need a small logic refactor (mutation routing).

**2. Snapshot can go stale between writes**
If code reads `snapshot.weightUnit`, mutates the underlying `Account`, then reads
`snapshot.weightUnit` again without calling `updatePublishedState()`, the snapshot
is stale. This is the same risk as any cached value. Mitigated by the rule: always
read from `activeAccount` (the latest published snapshot) not from a captured local.

**3. `switchAccount` and `setActiveAccount` signatures change**
Methods that accepted `Account` objects now accept `accountId: String`. Any caller
that passed a managed object must be updated to pass the ID. The compiler will
catch every missing change.

**4. `toSnapshot()` is a new code path that must stay in sync with `Account`**
When a new field is added to `Account` or a child model, `AccountSnapshot` and
`toSnapshot()` must be updated too. This is easy to miss. Mitigation: add a
compiler-visible test that constructs a snapshot from a known `Account` and asserts
every field — it will fail to compile if a field is missing.

**5. Some callers that returned `Account` now return `Void`**
Methods like `updateProfile`, `createGoal`, `updateStreak` currently return
`Account` so callers can read the result immediately. After this change they return
`Void` and callers read `activeAccount` (the updated snapshot) instead. This is a
protocol-level change that cascades to all call sites.

---

## 5. All Files Affected

### New files (2)

| File | Purpose |
|---|---|
| `Domain/Models/Domain/Auth/AccountSnapshot.swift` | The flat value-type struct |
| `Domain/Models/DB/Account+Snapshot.swift` | `toSnapshot()` conversion extension |

### Modified — infrastructure (7)

| File | Change |
|---|---|
| `Domain/Services/AccountServiceProtocol.swift` | Change `Account?` → `AccountSnapshot?` in published properties and method return types. Remove `forceRefresh` parameter. |
| `Data/Services/AccountService.swift` | Change published types. Rewrite `updatePublishedState`. Delete `notifyActiveAccountChanged`. Fix `updateBodyComp`. Remove `hydrateTokensInAccount` call sites. |
| `Core/Network/HTTPClient.swift` | `getAccount()` return type: `Account` → `AccountSnapshot`. One line change — all field accesses (`expiresAt`, `accountId`, `accessToken`) exist on `AccountSnapshot`. |
| `Data/Services/ScaleService.swift` | Combine sink closure has explicit `(newAccount: Account?)` type annotation — change to `AccountSnapshot?`. All accesses inside the closure are `.accountId` only. |
| `meAppTests/Support/Mocks/Services/MockAccountService.swift` | Change published types. Add `makeSnapshot()` helper. |
| `meAppTests/Features/Common/Network/Mocks/MockTokenManagerAccountService.swift` | Update `activeAccount` type |
| `meAppTests/Features/Account/AccountServiceTests.swift` | Update test assertions that used `Account` fields to use `AccountSnapshot` fields |

### Modified — feature / service files (52)

Most changes are mechanical: nested optional chain → flat field, or `Account?` type → `AccountSnapshot?`.
Four files require logic refactoring (see Critical section below — marked with **Critical**).

**Dashboard (13)**

| File | Example change |
|---|---|
| `Features/Dashboard/Stores/DashboardStore.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/Dashboard/Managers/DashboardGoalManager.swift` | `activeAccount?.goalSettings?.goalType` → `activeAccount?.goalType` |
| `Features/Dashboard/Managers/DashboardStreakManager.swift` | `activeAccount?.streaksSettings?.isStreakOn` → `activeAccount?.isStreakOn` |
| `Features/Dashboard/Managers/DashboardDisplayManager.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/Dashboard/Managers/DashboardGridEditingManager.swift` | `account.dashboardSettings?.progressMetrics` → `account.progressMetrics` |
| `Features/Dashboard/Managers/DashboardMetricsManager.swift` | `account.dashboardSettings?.dashboardType` → `account.dashboardType`; `account.dashboardSettings?.dashboardMetrics` → `account.dashboardMetrics` |
| `Features/Dashboard/Managers/DashboardLifecycleManager.swift` | `account.dashboardSettings?.dashboardType` → `account.dashboardType`; passes `accountService.activeAccount` to coordinator — type changes |
| `Features/Dashboard/Managers/DashboardSyncCoordinator.swift` | Methods accept `activeAccount: Account?` parameter — change to `AccountSnapshot?` |
| `Features/Dashboard/Protocols/DashboardMangerProtocols.swift` | Protocol method signatures with `activeAccount: Account?` parameter — change to `AccountSnapshot?` |
| `Features/Dashboard/Views/Components/WeightSnapshotCard.swift` | `activeAccount?.goalSettings?.goalWeight` → `activeAccount?.goalWeight` |
| `Features/Dashboard/Views/Components/BabySnapshotCard.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/Dashboard/ViewModels/WeightSnapshotCardViewModel.swift` | flatten child access |
| `Features/Dashboard/ViewModels/BabySnapshotCardViewModel.swift` | flatten child access |

**Settings (10)**

| File | Example change |
|---|---|
| `Features/Settings/Stores/SettingsStore.swift` | 24 child-model accesses → flat fields |
| `Features/Settings/Views/Screens/SettingsScreen.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/Settings/Profile/Views/Screens/GoalSettingScreen.swift` | `activeAccount?.goalSettings?.goalType` → `activeAccount?.goalType` |
| `Features/Settings/Profile/Views/Screens/WeightlessScreen.swift` | `activeAccount?.weightlessSettings?.isWeightlessOn` → `activeAccount?.isWeightlessOn` |
| `Features/Settings/Account/Stores/AccountsStore.swift` | `switchAccount(to: account)` → `switchAccount(to: snapshot.accountId)` |
| `Features/Settings/Integrations/Stores/IntegrationStore.swift` | Private methods `applyAccountState(_ account: Account?)` and `isIntegrationEnabled(_:in account: Account)` — parameter types change to `AccountSnapshot?` / `AccountSnapshot` |
| `Features/Settings/Integrations/Stores/HealthKitStore.swift` | flatten integration access |
| `Features/Settings/Scale/Stores/ScaleSettingsStore.swift` | `activeAccount?.firstName` → `activeAccount?.firstName` (unchanged) |
| `Features/Settings/MyKids/Stores/MyKidsStore.swift` | flatten child access |
| `Features/Settings/Help/Stores/HelpStore.swift` | `@Published var activeAccount: Account?` → `AccountSnapshot?` |

**ScaleSetup (9)**

| File | Example change |
|---|---|
| `Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStore.swift` | `activeAccount?.firstName` → `activeAccount?.firstName` |
| `Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStorePairingFlow.swift` | `accountId` access |
| `Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreCustomization.swift` | `activeAccount?.dashboardSettings?.dashboardType` → `activeAccount?.dashboardType` |
| `Features/ScaleSetup/Baby/Stores/BabyScaleSetupStorePairingFlow.swift` | flatten access |
| `Features/ScaleSetup/Baby/Stores/BabyScaleSetupStoreBabyProfileFlow.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/ScaleSetup/Bluetooth/Stores/BluetoothScaleSetupStore.swift` | flatten access |
| `Features/ScaleSetup/Wifi/Stores/WifiScaleSetupStore.swift` | `accountId` access |
| `Features/ScaleSetup/A6/Stores/A6ScaleSetupStore.swift` | `accountId` access |
| `Features/ScaleSetup/AppSync/Stores/AppSyncSetupStore.swift` | `accountId` access |

**Auth (2)**

| File | Example change |
|---|---|
| `Features/Auth/Stores/LandingStore.swift` | `@Published var accounts: [Account]` → `[AccountSnapshot]`. Sort closure `(Account, Account) -> Bool` → `(AccountSnapshot, AccountSnapshot) -> Bool`. `switchAccount(to: account)` → `switchAccount(to: account.accountId)` |
| `Features/Auth/Stores/SignupStore.swift` | `setInitialProductTypes(on account: Account)` → `AccountSnapshot` type (but see Critical section — mutation on line 526 must be removed). `persistSignupBabies(for account: Account)` → `AccountSnapshot` (reads only `.accountId` — safe) |

**Other features (9)**

| File | Example change |
|---|---|
| `Features/Common/ViewModels/ContentViewModel.swift` | Subscribes to `activeAccountPublisher`, stores `currentAccount: Account?` — change type to `AccountSnapshot?` |
| `Features/Entry/Stores/EntryStore.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/History/Stores/HistoryStore.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/AppSync/Stores/AppSyncTabStore.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |
| `Features/BpmSetup/Stores/BpmSetupStore.swift` | `accountId` access |
| `Features/Common/ViewModels/BottomTabBarViewModel.swift` | `accountId` access |
| `Features/Common/Stores/ProductTypeStore.swift` | **Critical** — see mutation section. 5 sites mutate `account.productTypes` directly. Must refactor to call `accountService.updateProductTypes(_:)` |
| `Features/Common/Stores/GoalProgressViewModel.swift` | flatten goal access |
| `Features/Common/Extensions/WeightUnitKey.swift` | `activeAccount?.weightSettings?.weightUnit` → `activeAccount?.weightUnit` |

**Services that access `activeAccount` (8)**

| File | Change |
|---|---|
| `Data/Services/EntryService.swift` | `activeAccount?.weightUnit` (flat — already a top-level field) |
| `Data/Services/HealthKitService.swift` | Flatten integration access |
| `Data/Services/BluetoothService/BluetoothService.swift` | `var activeAccount: Account?` → `AccountSnapshot?`; `handleAccountUpdate(_ account: Account?)` and `scheduleProfileUpdateIfNeeded(for account: Account?)` — parameter types change. All extension files auto-inherit the type change. |
| `Data/Services/BluetoothService/BluetoothServiceDeviceProfileUtils.swift` | `createScanData(from account: Account?)` and `getProfileInfo(from account: Account)` — parameter types change to `AccountSnapshot`. Child model chains: `account.weightSettings?.height` → `account.weightHeight`; `account.weightSettings?.activityLevel` → `account.activityLevel`; `account.goalSettings?.goalWeight` → `account.goalWeight` |
| `Data/Services/BluetoothService/BluetoothServiceScanEventPipeline.swift` | `createBathScaleEntry(...activeAccount: Account)` and `convertBabyScaleEntry(...activeAccount: Account...)` — parameter types change. `activeAccount.weightSettings?.height` → `activeAccount.weightHeight` |
| `Data/Services/BluetoothService/BluetoothServiceBpmOperations.swift` | **Critical** — see mutation section. `account.productTypes.append("myBloodPressure")` → must call `accountService.updateProductTypes(_:)` |
| `Data/Services/BabyService.swift` | **Critical** — see mutation section. `account.productTypes.append("baby")` / `removeAll` → must call `accountService.updateProductTypes(_:)` |
| `Data/Services/GoalAlertService.swift` | `account.goalSettings?.goalType` → `account.goalType`; `account.goalSettings?.goalWeight` → `account.goalWeight` |

**Test mocks (1 additional)**

| File | Change |
|---|---|
| `Core/Testing/UITest/Mocks/UITestAccountService.swift` | Full mock — `@Published var activeAccount: Account?` → `AccountSnapshot?`, `@Published var allAccounts: [Account]` → `[AccountSnapshot]`, `switchAccount(to account: Account)` → `switchAccount(to accountId: String)`, all other method return types updated |

---

### Critical — Files That Mutate Account Objects via `activeAccount`

Four files do not just READ from `accountService.activeAccount` — they mutate the
SwiftData managed object directly. `AccountSnapshot` is a value type with `let`
properties — it cannot be mutated. These files **cannot** be mechanically updated
with a type rename. Each requires a logic refactor.

| File | Mutation | Lines |
|---|---|---|
| `Data/Services/BabyService.swift` | `account.productTypes.append("baby")` / `account.productTypes.removeAll { $0 == "baby" }` | 107, 120 |
| `Data/Services/BluetoothService/BluetoothServiceBpmOperations.swift` | `account.productTypes.append("myBloodPressure")` | 40 |
| `Features/Common/Stores/ProductTypeStore.swift` | `account.productTypes.append(...)` (4 sites) + `account.productTypes = reconstructed` | 181, 186, 191, 209, 262 |
| `Features/Auth/Stores/SignupStore.swift` | `account.productTypes = types` on account returned from `signUp()` | 526 |

**Required fix pattern — route all mutations through `AccountService`:**

```swift
// Before (mutates managed object directly — breaks with AccountSnapshot)
guard let account = accountService.activeAccount,
      !account.productTypes.contains("baby") else { return }
account.productTypes.append("baby")

// After (calls service method — AccountService owns the mutation)
guard let snapshot = accountService.activeAccount,
      !snapshot.productTypes.contains("baby") else { return }
try await accountService.updateProductTypes(snapshot.productTypes + ["baby"])
```

`AccountService.updateProductTypes(_:)` already exists in the protocol.
All four files just need to call it instead of mutating the object themselves.

**SignupStore special case:** `setInitialProductTypes(on:)` already calls
`accountService.updateProductTypes(types)` on the next line — the direct
`account.productTypes = types` assignment is redundant and can simply be deleted.

```swift
// Before (SignupStore — line 526–527)
account.productTypes = types                              // ← DELETE this line
_ = try await accountService.updateProductTypes(types)    // ← this already persists it

// After
try await accountService.updateProductTypes(types)
```

---

### Files confirmed safe (no change needed)

These files reference `activeAccount` but only access top-level primitive fields
(`accountId`, `fcmToken`, `email`) that exist identically on `AccountSnapshot`.
No code changes required — the type inference flows through naturally.

| File | Access pattern |
|---|---|
| `Data/Services/PushNotificationService.swift` | `.accountId`, `.fcmToken` only |
| `Data/Services/IntegrationsService.swift` | `.accountId` only |
| `Domain/Services/FeedService.swift` | `.accountId` only |
| `Core/Services/LoggerService.swift` | `.accountId` only |
| `Data/Services/AccountFlagService.swift` | No direct `activeAccount` access |
| `Theme/Theme.swift` | Stores `activeAccountId: String?` — not the Account object |
| `Data/Services/AccountMigrationService.swift` | Creates `Account(from: DTO)` — never reads from `activeAccount` |
| `Data/Storage/DB/AccountRepository.swift` | Below the service boundary — works with SwiftData objects directly |
| `Features/Dashboard/Views/Components/GraphView.swift` | `.onReceive(accountService.$activeAccount) { _ in ... }` — discards value |
| `Features/Auth/Stores/LoginStore.swift` | `_ = try await accountService.logIn(...)` — discards return |
| `Data/Services/BluetoothService/BluetoothServiceCoreOperations.swift` | `activeAccount?.accountId` — inherits type from BluetoothService |
| `Data/Services/BluetoothService/BluetoothServiceEventAlerts.swift` | `activeAccount?.accountId` — inherits type from BluetoothService |

### Total file count

| Category | Count |
|---|---|
| New files | 2 |
| Modified infrastructure | 7 |
| Modified feature / service files | 52 |
| _(of which 4 need critical mutation refactoring)_ | _(4)_ |
| **Total** | **61** |

---

## 6. Risk Assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Snapshot field missing after `Account` gains a new field | Medium | Add a compile-time test that constructs `AccountSnapshot` from `Account`. Missing field → compile error |
| Stale snapshot read after a write without `updatePublishedState` | Low | Existing pattern — `updatePublishedState()` is already called at end of every write. No change here |
| `switchAccount` callers that pass managed objects | Certain — compiler catches all | Compiler error at every call site — no silent failures |
| `toSnapshot()` called before child relationships are populated (new account) | Low | Defaults baked in: `.lb` for weight unit, `false` for booleans, etc. |
| Test failures due to `Account?` → `AccountSnapshot?` type change in mocks | Certain | All test files are already identified. Changes are type-level only, no logic changes |
| Regression in `$activeAccount` Combine sink in `AccountService.init` | Low | Sink behaviour is unchanged — `nil` still triggers service deregistration |
| Direct `Account` mutation via `activeAccount` (4 files) | **High** | BabyService, BluetoothServiceBpmOperations, ProductTypeStore, and SignupStore mutate `account.productTypes` on the managed object from `activeAccount`. With `AccountSnapshot` (a `let` struct), these fail at compile time. Must refactor to call `accountService.updateProductTypes(_:)` instead. The compiler catches all 4 — but the refactor requires logic changes, not just a type rename. |
| Combine sinks with explicit `Account?` type annotation | Certain — compiler catches | `ScaleService` has `(newAccount: Account?)` explicit closure type — change to `AccountSnapshot?` |

**Overall regression risk: Low–Medium.** The Swift compiler catches every type
mismatch — there are no silent failures. The four `productTypes` mutation files are the
only logic-level changes (not just type renames). All other changes are mechanical.
No logic changes in `AccountRepository` or the SwiftData persistence layer.

---

## 7. Migration Checklist

```
Phase 1 — Foundation (no breakage, additive only)
  [ ] Create AccountSnapshot.swift
  [ ] Create Account+Snapshot.swift with toSnapshot()
  [ ] Verify toSnapshot() compiles against all 7 child models

Phase 2 — Service layer (breaks protocol conformance intentionally)
  [ ] Update AccountServiceProtocol — change published types, remove forceRefresh
  [ ] Update AccountService — new updatePublishedState, delete notifyActiveAccountChanged
  [ ] Fix updateBodyComp — remove direct activeAccount assignment
  [ ] Update switchAccount / setActiveAccount to take accountId: String
  [ ] Build — expect 50+ compiler errors (all type mismatches in features/services)

Phase 3 — Feature files (fix compiler errors one by one)

  Infrastructure:
  [ ] HTTPClient (getAccount() return type — 1 line)
  [ ] ScaleService (explicit Account? closure type annotation)

  Settings:
  [ ] SettingsStore (24 accesses — largest file)
  [ ] IntegrationStore (applyAccountState + isIntegrationEnabled signatures + refreshAccount return)
  [ ] HelpStore (@Published activeAccount type)

  Dashboard:
  [ ] DashboardStore + GoalManager + StreakManager + DisplayManager
  [ ] DashboardGridEditingManager + DashboardMetricsManager (child model chains)
  [ ] DashboardLifecycleManager + DashboardSyncCoordinator + DashboardMangerProtocols (Account? params)

  Auth:
  [ ] LandingStore ([Account] type + switchAccount call site)
  [ ] AccountsStore (switchAccount call site)
  [ ] SignupStore (method signatures — see Critical section for mutation fix)

  Common:
  [ ] ContentViewModel (currentAccount: Account? type)
  [ ] BottomTabBarViewModel, GoalProgressViewModel, WeightUnitKey

  Other feature stores:
  [ ] EntryStore, HistoryStore, AppSyncTabStore, BpmSetupStore
  [ ] All ScaleSetup stores (9 files)
  [ ] ViewModels (WeightSnapshotCardViewModel, BabySnapshotCardViewModel)

  Services:
  [ ] EntryService, HealthKitService
  [ ] BluetoothService (var activeAccount: Account? + method signatures)
  [ ] BluetoothServiceDeviceProfileUtils (method params + child model chains)
  [ ] BluetoothServiceScanEventPipeline (method params + child model chain)
  [ ] GoalAlertService (child model chain → flat field)

  [ ] Build — zero compiler errors

  Critical — handle separately (logic changes, not just type renames):
  [ ] BabyService — remove direct account.productTypes.append/removeAll;
      replace with accountService.updateProductTypes(_:) call
  [ ] BluetoothServiceBpmOperations — remove account.productTypes.append;
      replace with accountService.updateProductTypes(_:) call
  [ ] ProductTypeStore — remove 5 direct account.productTypes mutations;
      replace with accountService.updateProductTypes(_:) calls
  [ ] SignupStore — delete redundant account.productTypes = types (line 526);
      the next line already calls accountService.updateProductTypes(types)

Phase 4 — Tests
  [ ] Update MockAccountService published types
  [ ] Add makeSnapshot() helper
  [ ] Update MockTokenManagerAccountService
  [ ] Update UITestAccountService (full mock — published types, switchAccount signature)
  [ ] Run AccountServiceTests — verify sync/offline assertions still pass
  [ ] Build and run full test suite on device

Phase 5 — Verify
  [ ] Manual smoke test: login, profile save, weight unit change, goal save, streak toggle
  [ ] Confirm @Published fires once per operation (add debug print in updatePublishedState)
  [ ] Confirm notifyActiveAccountChanged is fully deleted (grep the codebase)
  [ ] Confirm forceRefresh parameter is gone everywhere
  [ ] Confirm no Account @Model escapes AccountService (grep for `: Account?` and `-> Account` outside AccountService/AccountRepository)
```

---

## 8. Crash Coverage Verification

Maps each known crash vector from `CRASH_ANALYSIS.md` to the implementation step
that eliminates it. This section confirms every crash path is addressed.

### Crash 1 — `Account.accessToken.getter` off-actor (HTTPClient.swift:222)

**Crash path:**
```
EntryService.performSync() [background]
  → HTTPClient.makeRequest()
    → getAccount() returns Account @Model
      → account.accessToken    ← reads @Model off main actor → CRASH
```

**Fixed by:** Step 4a (published type change) + infrastructure change to `HTTPClient.swift`.
After migration, `getAccount()` returns `AccountSnapshot`. Reading `snapshot.accessToken`
is a plain `let String?` property access — no SwiftData backing store involved.

### Crash 2 — `Account.expiresAt.getter` off-actor (HTTPClient.swift:77)

**Crash path:**
```
DashboardStore.initializeDashboard() [background]
  → AccountService.refreshAccount()
    → HTTPClient.send()
      → getAccount() returns Account @Model
        → account.expiresAt    ← reads @Model off main actor → CRASH
```

**Fixed by:** Same as Crash 1. `getAccount()` returns `AccountSnapshot`.
`snapshot.expiresAt` is a `let String?` — safe on any thread.

### Crash vector 3 — BluetoothService reads Account off BLE callback thread

**Current risk (not yet crashed, but structurally identical):**
```
BLE callback [background thread]
  → BluetoothServiceScanEventPipeline.convertGGEntry()
    → activeAccount.accountId               ← reads @Model off-actor
    → activeAccount.weightSettings?.height   ← reads child @Model off-actor
```

`BluetoothService` stores `var activeAccount: Account?` from the publisher sink.
BLE entry conversion accesses this object on callback threads.

**Fixed by:** Step 6 migration of `BluetoothService.swift` (type changes to
`AccountSnapshot?`) + `BluetoothServiceScanEventPipeline.swift` (method param types
+ child model flattening). After migration, all reads are on a plain struct.

### Crash vector 4 — Direct Account mutation from feature stores

**Current risk:**
```
ProductTypeStore.syncProductTypesFromDevices()
  → account = accountService.activeAccount   ← gets @Model reference
  → account.productTypes.append("baby")      ← mutates managed object from store
```

Mutating a SwiftData model from outside the owning service can trigger autosave
conflicts, especially during concurrent sync operations.

**Fixed by:** Critical mutation refactoring (Phase 3). All 4 mutation sites are
rerouted through `accountService.updateProductTypes(_:)`. The managed object is
only mutated inside `AccountService` (which is `@MainActor`).

### What the snapshot does NOT fix (separate issues)

| Issue | Why not fixed | Required fix |
|---|---|---|
| `LoggerRepository` concurrent background `ModelContext` writes | Separate from Account — logger creates multiple `Task.detached` contexts | Replace with serial `LoggerContextActor` (see `CRASH_ANALYSIS.md` Fix 2) |
| Entry deletion stuck loader + crash on reopen | UI state management gap, not an Account type issue | Add `defer { isLoading = false }` + `context.rollback()` on failure |
| Concurrent `EntryRepository` faults (Crash 2 amplifier) | Entry uses `SwiftDataWorker` which is separate from Account | Audit `SwiftDataWorker` context usage for deadlock patterns |

---

## 9. Recommended Implementation Order

### Step A — Emergency patch (this week, 1 file)

Fix the active crash in `HTTPClient.swift` before the full migration. Add `@MainActor`
to `getAccount` and extract all model properties there:

```swift
@MainActor
private func getAccountCredentials(_ accountId: String?) throws -> (id: String, token: String?, expiresAt: String?) {
    if let accountId {
        guard let account = try accountService.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        return (account.accountId, account.accessToken, account.expiresAt)
    } else {
        guard let account = accountService.activeAccount else {
            throw AccountError.noActiveAccount
        }
        return (account.accountId, account.accessToken, account.expiresAt)
    }
}
```

This stops Crashes 1 and 2 immediately. One file, one PR.

### Step B — Fix LoggerRepository (separate PR, 1 file)

Replace `Task.detached` + new-context-per-call with a serial `LoggerContextActor`.
This eliminates the concurrent write amplifier from Crash 1.

### Step C — Full AccountSnapshot migration (the main PR)

Implement this guide. This makes the Step A emergency patch redundant — the snapshot
IS the credentials struct, applied app-wide. After this migration:

- `HTTPClient.getAccount()` naturally returns a struct (no special `@MainActor` wrapper)
- All 52 feature/service files receive structs instead of managed objects
- The Bluetooth BLE callback crash vector is eliminated
- The 4 mutation sites are properly routed through the service
- The entire class of "Account @Model read off-actor" crash becomes impossible

Step A is the bandaid. Step C is the permanent fix.
