# Account Model — Architecture Deep Dive

A comprehensive reference for how the `Account` SwiftData model is created, mutated, persisted, synced, and observed across the entire iOS app.

---

## Table of Contents

1. [Model Structure](#1-model-structure)
2. [Full Data Flow Diagram](#2-full-data-flow-diagram)
3. [Layer Responsibilities](#3-layer-responsibilities)
4. [Thread Safety — Foreground & Background](#4-thread-safety--foreground--background)
5. [Token Lifecycle](#5-token-lifecycle)
6. [Offline-First Sync Pattern](#6-offline-first-sync-pattern)
7. [State Observation & Reactivity](#7-state-observation--reactivity)
8. [Multi-Account Support](#8-multi-account-support)
9. [Key Methods Reference](#9-key-methods-reference)
10. [Pros & Cons Analysis](#10-pros--cons-analysis)

---

## 1. Model Structure

**File:** `Domain/Models/DB/Account.swift`

`Account` is a SwiftData `@Model` class — the single source of truth for a user's identity, preferences, and session state stored on-device.

### Fields

| Field | Type | Persisted | Notes |
|-------|------|-----------|-------|
| `accountId` | `String` | Yes | Primary key (`@Attribute(.unique)`) |
| `email` | `String` | Yes | |
| `firstName` | `String?` | Yes | |
| `lastName` | `String?` | Yes | |
| `gender` | `Sex?` | Yes | |
| `height` | `String?` | Yes | Legacy field; canonical height lives in `weightSettings` |
| `dob` | `String?` | Yes | |
| `zipcode` | `String?` | Yes | |
| `isLoggedIn` | `Bool?` | Yes | Active session flag |
| `isExpired` | `Bool?` | Yes | Session expired (triggers auto-logout) |
| `isActiveAccount` | `Bool?` | Yes | Which account is currently shown in the UI |
| `fcmToken` | `String?` | Yes | Firebase push token |
| `lastActiveTime` | `String?` | Yes | ISO-8601 timestamp of last switch |
| `isSynced` | `Bool?` | Yes | `false` when there are pending offline changes |
| `productTypes` | `[String]` | Yes | e.g. `["myWeight", "myBloodPressure", "baby"]` |
| `accessToken` | `String?` | **No** (`@Transient`) | In-memory only; source of truth is Keychain |
| `refreshToken` | `String?` | **No** (`@Transient`) | In-memory only; source of truth is Keychain |
| `expiresAt` | `String?` | **No** (`@Transient`) | In-memory only; source of truth is Keychain |

### Relationships (all cascade-delete)

| Relationship | Model | Purpose |
|---|---|---|
| `weightSettings` | `WeightCompSettings` | Height, weight unit, activity level |
| `goalSettings` | `GoalSettings` | Goal type, initial/target weight |
| `streaksSettings` | `StreaksSettings` | Streak on/off and timestamp |
| `weightlessSettings` | `WeightlessSettings` | Weightless mode configuration |
| `notificationSettings` | `NotificationSettings` | Entry/weigh-in notification flags |
| `dashboardSettings` | `DashboardSettings` | Dashboard type, metric order |
| `integrationSettings` | `IntegrationSettings` | Fitbit, HealthKit, MFP flags |

All child models are created in `Account.init(from: AccountDTO)` and destroyed automatically when the `Account` is deleted (cascade rule).

---

## 2. Full Data Flow Diagram

```
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                           NETWORK LAYER                                     │
 │   AccountRepositoryAPI  ──►  HTTPClient  ──►  REST API                     │
 │         ▼                                                                   │
 │   AccountDTO / AccountResponse (Codable)                                    │
 └───────────────────────────┬─────────────────────────────────────────────────┘
                             │ API response decoded to DTO
                             ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                        SERVICE LAYER (@MainActor)                           │
 │                                                                             │
 │   AccountService                                                            │
 │   ├── Calls apiRepo to fetch/push data                                      │
 │   ├── Calls account.update(from: dto) to merge into SwiftData model         │
 │   ├── Calls localRepo to save/update/delete in SwiftData                    │
 │   ├── Calls keychainService to store tokens                                 │
 │   ├── Calls hydrateTokensInAccount() to attach tokens from Keychain         │
 │   └── Calls updatePublishedState() to push @Published activeAccount         │
 └───────────────────────────┬─────────────────────────────────────────────────┘
                             │ mutations on main actor
                             ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                     LOCAL STORAGE LAYER (@MainActor)                        │
 │                                                                             │
 │   AccountRepository (SwiftData, ModelContext on main actor)                 │
 │   ├── insert / context.save()  — new account                                │
 │   ├── mergeAccount()           — update existing by copying fields          │
 │   ├── FetchDescriptor queries  — typed predicates, no raw SQL               │
 │   └── context.delete()         — cascade deletes all child models           │
 │                                                                             │
 │   PersistenceController.shared.context  (single ModelContext, main actor)   │
 └───────────────────────────┬─────────────────────────────────────────────────┘
                             │ @Published properties
                             ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                       UI LAYER (SwiftUI, @MainActor)                        │
 │                                                                             │
 │   Feature Stores (@MainActor ObservableObject)                              │
 │   └── @Injector var accountService: AccountServiceProtocol                  │
 │       ├── .activeAccount  (@Published Account?)  ─► drives navigation       │
 │       └── .allAccounts    (@Published [Account]) ─► account switcher UI     │
 └─────────────────────────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                     SECURE STORAGE (always async-safe)                      │
 │                                                                             │
 │   Keychain (via KeychainService)                                            │
 │   └── Tokens: accessToken, refreshToken, expiresAt  (per accountId)        │
 │                                                                             │
 │   NOT in SwiftData — @Transient fields are hydrated in-memory on fetch     │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Layer Responsibilities

### `AccountRepositoryAPI` — Network only
- Makes HTTP calls via `HTTPClient`.
- Decodes raw JSON into `AccountDTO` / `AccountResponse`.
- **Zero business logic, zero caching.**

### `AccountRepository` — SwiftData CRUD only
- All operations are `@MainActor`, single `ModelContext`.
- Provides: `fetchAccount`, `fetchAllAccounts`, `saveAccount`, `updateAccount`, `deleteAccount`, `activateAccount`.
- `mergeAccount()` copies every field from a source `Account` into an existing managed object to avoid creating orphaned records.
- `fetchAllAccountsSync()` is a synchronous variant used only at app startup before any `async` context is available.

### `AccountService` — Orchestration
- **Declared `@MainActor`** — all methods execute on the main thread.
- Owns two `@Published` properties that drive all SwiftUI observation:
  - `activeAccount: Account?`
  - `allAccounts: [Account]`
- Applies the **offline-first** pattern: try network → on network error, save locally with `isSynced = false` → sync on next launch.
- Manages token lifecycle (Keychain writes, hydration, refresh, migration).

### `TokenManager` — Swift `actor`
- Runs on its own isolated actor (not main actor).
- Serialises concurrent token-refresh attempts via a continuation queue (`refreshContinuations`).
- Calls back into `AccountService` via the `AccountServiceProtocol` interface.

### `SwiftDataWorker` — `@ModelActor`
- A **background** SwiftData worker for read-heavy operations (progress data, entry lists, device data).
- Uses its own actor-isolated `ModelContext` — completely separate from the main-actor context.
- Returns **value types** (`EntryData`, `DeviceData`, `ProgressFetchResult`) so nothing SwiftData-managed escapes the actor.
- `Account` itself is never accessed from `SwiftDataWorker` — only `Entry` and `Device` records are read here.

---

## 4. Thread Safety — Foreground & Background

### Rule #1 — SwiftData models are NOT `Sendable`

`Account` and all its child models (`WeightCompSettings`, `GoalSettings`, etc.) must never cross actor boundaries. They are bound to the `ModelContext` that created them — which is always the **main-actor context** (`PersistenceController.shared.context`).

```swift
// WRONG — crossing actor boundary with a SwiftData model
Task.detached {
    let name = account.firstName  // ❌ crash risk — account bound to main actor
}

// CORRECT — extract primitives before any await
let accountId = account.accountId   // ✅ extract primitive on main actor
let name = account.firstName        // ✅ extract primitive on main actor
Task.detached {
    await doSomething(with: accountId, name: name)  // ✅ primitives are Sendable
}
```

### Rule #2 — All SwiftData CRUD runs on `@MainActor`

`AccountRepository`, `AccountService`, and `PersistenceController` are all annotated `@MainActor`. This means:

- Every `context.fetch`, `context.insert`, `context.save`, and `context.delete` happens on the main thread.
- There is **no explicit locking** needed for the Account model because the main actor is the sole owner.
- This is safe for the UI since `@Published` properties on `AccountService` are updated on the same actor.

### Rule #3 — Background work uses separate value-type extraction

When entry or device data is needed off the main thread (e.g. for progress calculations):

```
Main Actor:          accountId (String) ──────────────────────────►┐
                                                                    │
SwiftDataWorker:     FetchDescriptor<Entry>(accountId == ...)       │
                     extractEntryData() → EntryData (Sendable)  ◄──┘
                                                                    │
Main Actor:          receives [EntryData] — all Sendable primitives │
```

`SwiftDataWorker` is a `@ModelActor` with its own isolated context. It fetches SwiftData records in background, immediately extracts all relationship data into `Sendable` value structs, and returns them. The caller on the main actor never touches the `@ModelActor`'s managed objects.

### Thread ownership summary

| Component | Actor | Concurrent? |
|---|---|---|
| `PersistenceController` | `@MainActor` | No — single context |
| `AccountRepository` | `@MainActor` | No |
| `AccountService` | `@MainActor` | No (tasks are serialised by actor) |
| `TokenManager` | Custom `actor` | Yes — serialises token refresh |
| `SwiftDataWorker` | `@ModelActor` | Yes — separate background context |
| UI Stores | `@MainActor` | No |

### Startup sequence (foreground vs background)

App launch fires two independent `Task {}` blocks inside `AccountService.init`:

```
Launch
 │
 ├── Task 1 (fast path)
 │   └── fetchAllAccountsSync()  ← synchronous, main actor
 │       hydrateTokensInAccount()
 │       self.activeAccount = ...  ← publishes immediately → UI can render
 │       Theme.shared.setActiveAccount()
 │
 └── Task 2 (full startup)
     ├── migrateTokensToKeychainIfNeeded()
     ├── syncUnsyncedAccounts()   ← network, may take time
     ├── updatePublishedState()
     ├── refreshAllAccounts()     ← network, hits all stored accounts
     └── migrateFromIonicAppIfNeeded()  ← only if no active account
```

Task 1 runs first and gives the UI something to display immediately. Task 2 does the heavy lifting in the background (but still on the main actor via Swift's structured concurrency scheduler).

---

## 5. Token Lifecycle

Tokens (`accessToken`, `refreshToken`, `expiresAt`) went through a deliberate security migration:

### Before (legacy)
- Stored directly in `Account` SwiftData fields → persisted to disk.

### After (current)
- `@Transient` on `Account` — **never written to SwiftData**.
- Written to **Keychain** via `KeychainService` (one entry per `accountId`).
- Loaded back into `Account` fields in-memory by `hydrateTokensInAccount()` every time an account is fetched.

### One-time migration path
```
App launch
└── migrateTokensToKeychainIfNeeded()
    ├── Check KvStorage flag "tokensMigratedToKeychain"
    ├── If not done:
    │   └── For each local account:
    │       ├── Read tokens from Account fields (legacy)
    │       ├── Write to Keychain
    │       └── Clear Account fields → context.save()
    └── Set migration flag = true
```

### Token refresh flow (TokenManager actor)

```
HTTPClient (making auth'd request)
└── checkTokenExpiration(expiresAt)
    └── if expired → TokenManager.refreshToken()
        ├── if already refreshing → waitForRefresh() [continuation queue]
        ├── else → apiRepo.refreshToken(refreshToken, accountId)
        │   ├── success → keychainService.setTokens() + AccountService.updateTokens()
        │   │             resumeWaitingRequests()
        │   └── failure
        │       ├── 401 → AccountService.logOut(isAutoLogout: true)
        │       ├── 502/503 / noInternet → retry (up to maxRetries)
        │       └── other → AccountService.logOut(isAutoLogout: true)
        └── returns Tokens to all callers
```

Multiple network requests arriving simultaneously during a refresh all park at `waitForRefresh()`. Only one refresh call goes to the network.

---

## 6. Offline-First Sync Pattern

`isSynced: Bool?` on `Account` is the core flag. It is set to `false` whenever a network mutation fails, and `true` when the server acknowledges the change.

### Write pattern (every mutating method)

```swift
// Successful path
let response = try await apiRepo.patchProfile(profile)
localAccount.update(from: response)
localAccount.isSynced = true
try await updateAccountClearingTokens(localAccount)   // saves to SwiftData
try await updatePublishedState()                       // pushes @Published

// Offline fallback
if HTTPError.isNetworkError(error) {
    localAccount.update(from: profile)     // apply locally
    localAccount.isSynced = false          // mark for later sync
    try await updateAccountClearingTokens(localAccount)
    try await updatePublishedState()
    return localAccount                    // UI sees local change immediately
}
throw error   // non-network errors are propagated
```

### Sync-on-startup

`syncUnsyncedAccounts()` runs at every launch before `refreshAllAccounts()`. It:

1. Fetches the active account from local storage.
2. Takes an **immutable snapshot** of all offline field values as primitives (before any `await` that could mutate the managed object).
3. For each changed domain (profile, bodyComp, notifications, dashboard, streak, weightless, goal, integrations):
   - If `isSynced == false` AND the relevant fields are non-nil → calls the appropriate update method.
4. Marks `localAccount.isSynced = true` after all syncs succeed.

The immutable snapshot step is critical — it prevents the `await` inside an update call from seeing a mutated `localAccount` object mid-flight.

### `updateAccountClearingTokens`

Every save path calls this helper, which:
1. Sets `account.accessToken = nil`, `account.refreshToken = nil`, `account.expiresAt = nil` — so `@Transient` fields are never accidentally persisted.
2. Calls `localRepo.updateAccount(account)` → `context.save()`.

---

## 7. State Observation & Reactivity

### Published properties

```swift
@Published private(set) var activeAccount: Account?
@Published var allAccounts: [Account]
```

Both live on `AccountService` (a `@MainActor ObservableObject`). Feature stores inject the service via `@Injector` and observe via Combine or direct access.

### `$activeAccount` sink (session management)

```swift
$activeAccount
    .dropFirst()
    .sink { data in
        if data == nil {
            ServiceRegistry.shared.deregisterSessionServices()  // cleanup after logout
        } else {
            ServiceRegistry.shared.registerSessionServices()    // setup after login
            Theme.shared.setActiveAccount(data?.accountId)
        }
    }
```

This is the **central session bus**. Setting `activeAccount = nil` (logout) automatically tears down session-scoped services. Setting it to a non-nil value (login/switch) brings them back up.

### `updatePublishedState()`

Called at the end of every mutating method:

```swift
func updatePublishedState(forceRefresh: Bool = false) async throws {
    allAccounts = try await localRepo.fetchAllAccounts()
    for account in allAccounts { hydrateTokensInAccount(account) }
    let nextActive = allAccounts.first { $0.isActiveAccount == true }
    if forceRefresh || activeAccount?.accountId != nextActive?.accountId {
        activeAccount = nextActive
        Theme.shared.setActiveAccount(nextActive?.accountId)
    }
}
```

The `forceRefresh` flag bypasses the identity check so SwiftUI observes a change even when the same account is updated in-place (e.g. weight unit change).

### NotificationCenter broadcast

Some settings changes (e.g. `updateBodyComp`) post a `NotificationCenter` notification for components that cannot observe `@Published` directly:

```swift
NotificationCenter.default.post(
    name: .accountWeightUnitChanged,
    object: nil,
    userInfo: ["weightUnit": finalWeightUnit]
)
```

This is the fallback reactivity path when SwiftUI observation alone is not sufficient.

---

## 8. Multi-Account Support

The app supports up to N simultaneous logged-in accounts (enforced by `checkIfMaxAccountsReached`). Key behaviors:

| Operation | Behavior |
|---|---|
| Login | Disconnect BLE scales if switching accounts. Break `activeAccount` reference before context changes to prevent SwiftData observation glitches. |
| Switch account | Requires network connectivity. Refreshes target account from API before switching. Sets `isActiveAccount = true` on target, `false` on all others. |
| Logout single account | API logout (best effort), delete Keychain tokens, mark `isLoggedIn = false`. |
| Logout all accounts | Non-active accounts logged out first (state updates suppressed), active account last, then one final `updatePublishedState()`. |
| Remove from device | API logout attempted (errors ignored), Keychain tokens deleted, local SwiftData record deleted. |
| Delete account | API deletion + Keychain cleanup + SwiftData deletion. |

`activateAccount(withId:)` in `AccountRepository` walks **all** accounts in a single SwiftData fetch and updates `isActiveAccount` in one `context.save()` call, preventing multiple saves.

---

## 9. Key Methods Reference

### `AccountService`

| Method | Description |
|---|---|
| `signUp(email:password:profile:)` | Create account on server, store locally, activate |
| `logIn(email:password:)` | Authenticate, create or update local record, activate |
| `logOut(accountId:isAutoLogout:)` | Expire session locally, call API logout |
| `switchAccount(to:)` | Refresh target from API, disconnect BLE, set active |
| `refreshAccount(accountId:)` | Fetch latest from API → `update(from: dto)` → save |
| `refreshAllAccounts()` | Refresh every stored account; mark expired on 401 |
| `syncUnsyncedAccounts()` | Push all `isSynced = false` changes to server |
| `updateProfile(_:canSaveOffline:)` | PATCH profile; offline fallback if `canSaveOffline` |
| `updateBodyComp(_:)` | PATCH body composition settings |
| `updateTokens(_:_:)` | Write new tokens to Keychain + Account in-memory fields |
| `refreshTokens(accountId:)` | POST refresh token → returns new `Tokens` |
| `getActiveTokens()` | Read from Keychain, fallback to Account fields |
| `updatePublishedState(forceRefresh:)` | Re-fetch all accounts from SwiftData, push @Published |
| `hydrateTokensInAccount(_:)` | Attach Keychain tokens to Account @Transient fields |

### `AccountRepository`

| Method | Description |
|---|---|
| `fetchAccount(byId:)` | Single-predicate FetchDescriptor |
| `fetchAllAccounts()` | No predicate, returns everything |
| `fetchActiveAccount()` | Predicate `isActiveAccount == true` |
| `saveAccount(_:)` | Insert or merge; deduplicate by email |
| `updateAccount(_:)` | Merge into existing or fall back to save |
| `activateAccount(withId:lastActiveTime:)` | Atomic multi-account flag update |
| `deleteAccount(byId:)` | Cascade deletes all child models |
| `fetchAllAccountsSync()` | Synchronous variant for early app startup |

### `Account` update methods

| Method | Description |
|---|---|
| `init(from: AccountDTO)` | Creates account + all 7 child settings models |
| `update(from: AccountDTO)` | Merges API response into existing model |
| `update(from: AccountResponse)` | Calls `update(from: dto)` + token fields |
| `update(from: Tokens)` | Updates @Transient token fields in-memory |
| `update(from: Profile)` | Updates personal info + weight/activity settings |
| `update(from: GoalResponse)` | Updates goal settings |
| `toAccountDTO()` | Serialises back to DTO for network transmission |

---

## 10. Pros & Cons Analysis

### Strengths

**1. Strict actor isolation eliminates data races**
All SwiftData operations run on `@MainActor`. There is no shared mutable state accessed from multiple threads. `TokenManager` as a Swift `actor` serialises concurrent token refreshes safely.

**2. Offline-first with transparent UX**
The `isSynced` flag + offline fallback pattern means users can update settings without connectivity. Changes are applied locally immediately and synced on the next launch. The user never sees a failure for non-critical writes.

**3. Single source of truth per concern**
- Identity: SwiftData `Account` model.
- Tokens: Keychain (post-migration).
- Active session: `@Published activeAccount` on `AccountService`.
There is no duplication between layers — each layer has a defined role.

**4. Cascade delete safety**
All child models (`WeightCompSettings`, `GoalSettings`, etc.) are cascade-deleted with their parent `Account`. No orphaned records are left behind.

**5. Session lifecycle is data-driven**
The `$activeAccount` Combine sink automatically registers/deregisters session-scoped services without the feature layer needing to know about service lifecycle.

**6. Token migration was handled without breaking change**
The `migrateTokensToKeychainIfNeeded()` one-time migration transparently moves tokens from SwiftData to Keychain for existing users, protected by a KvStorage flag to prevent re-running.

**7. `mergeAccount()` prevents orphan records**
Rather than deleting and re-inserting on update, the repository merges field-by-field into the existing managed object. This preserves SwiftData's internal reference graph and avoids cascade-delete side effects.

---

### Weaknesses & Trade-offs

**1. All SwiftData on main thread — potential UI jank**
`AccountService` and `AccountRepository` are both `@MainActor`. If there are many accounts or complex queries at startup, this work runs on the same thread as the UI. Slow queries will delay frame rendering. There is no background context for Account operations (unlike `SwiftDataWorker` for entries).

**2. `updatePublishedState()` re-fetches all accounts on every write**
Every mutating operation ends with `fetchAllAccounts()` → assign to `allAccounts`. With many accounts this is a full table scan each time. For a typical user (1–3 accounts) this is fine, but it's not optimal at scale.

**3. `syncUnsyncedAccounts()` has combinatorial complexity**
The method constructs separate API calls for profile, body composition, notifications, dashboard type, dashboard metrics, streak, weightless mode, goal, and integrations independently. This can result in up to 9 sequential network calls on the first launch with connectivity restored after an offline session.

**4. Child model access through optional chains**
Code throughout the service layer uses long chains like `localAccount.weightSettings?.weightUnit`. If a relationship is unexpectedly `nil` (e.g. SwiftData corruption or a schema migration gap), mutations silently do nothing rather than throwing an error.

**5. `Account` is a god object**
The model owns 7 child relationships and exposes `toAccountDTO()` / `update(from:)` directly. It violates the single-responsibility principle by containing mapping logic. `AccountService` also accumulates significant complexity as a result of being the sole orchestrator for a very wide domain (auth + sync + settings + tokens + multi-account).

**6. No optimistic locking or conflict resolution**
When `syncUnsyncedAccounts()` pushes stale offline data, the server's response overwrites the local state. If the server has newer data that conflicts with the offline changes, the offline version always loses. There is no merge/conflict strategy.

**7. Token hydration is call-site coupling**
Every code path that fetches an account must remember to call `hydrateTokensInAccount()`. If a future contributor adds a new fetch path and forgets, `account.accessToken` will be `nil` silently. The `@Transient` annotation provides no compiler enforcement.

**8. `SwiftDataWorker` only helps Entry/Device — not Account**
Background fetching is only implemented for `Entry` and `Device`. Dashboard stores that need account settings data (e.g. weight unit, goal settings) must fetch these on the main actor — adding to the main-thread budget.

---

## Quick Reference: Where to look

| Question | File |
|---|---|
| What fields does Account store? | `Domain/Models/DB/Account.swift` |
| How is Account persisted? | `Data/Storage/DB/AccountRepository.swift` |
| How is Account orchestrated? | `Data/Services/AccountService.swift` |
| Where is the SwiftData container configured? | `Core/Services/PersistenceController.swift` |
| How are tokens refreshed concurrently? | `Core/Network/TokenManager.swift` |
| How is background entry fetching done safely? | `Core/Services/SwiftDataWorker.swift` |
| How are session services managed? | `Core/Services/ServiceRegistry.swift` |
| How are tokens stored securely? | `Core/Storage/KeychainAccess.swift` |
