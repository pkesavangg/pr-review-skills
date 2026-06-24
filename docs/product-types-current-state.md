# Product Types — Current iOS Implementation

**Last updated:** 2026-04-10  
**Scope:** iOS app (`meApp/iOS/meApp`)  
**Audience:** Engineers working on auth, account switching, dashboard selection, signup, baby flows, and device pairing

---

## Overview

`productTypes` is now implemented in the iOS app as the local source of truth for which product contexts a user should see in the product selector.

It replaces the old "infer from currently loaded devices" behavior as the primary driver for:

- dashboard product selection
- entry/history scoping
- baby profile selection
- account-aware product availability

Today, `productTypes` is stored on the local SwiftData `Account` model only. It is not yet server-backed in the account payload, so reinstall recovery still depends on reconstruction from synced devices and babies.

---

## Allowed Values

`Account.productTypes` is a `[String]` with these supported values:

| Value | Meaning |
|---|---|
| `"myWeight"` | User owns or has owned a weight-scale product context |
| `"myBloodPressure"` | User owns or has owned a BPM product context |
| `"baby"` | User should see baby product selections |

`"baby"` is a presence flag only. Individual baby rows still come from `BabyService`.

---

## Source Of Truth

### Primary source

The primary source is:

- `Account.productTypes`

Defined in:

- `meApp/iOS/meApp/Domain/Models/DB/Account.swift`

```swift
var productTypes: [String] = []
```

### Reconstruction source

If `productTypes` is empty, `ProductTypeStore` reconstructs it from:

- `ScaleService.scales`
- `BabyService.currentBabies`

This is meant to recover state after reinstall or first login on a fresh device.

---

## Where Product Types Are Written

### 1. Signup

`SignupStore.createUser()` writes initial product types immediately after successful signup.

File:

- `meApp/iOS/meApp/Features/Auth/Stores/SignupStore.swift`

Mapping:

| Signup device | Initial product types |
|---|---|
| `.weightScale` | `["myWeight"]` |
| `.bpm` | `["myBloodPressure"]` |
| `.babyScale` | `["baby"]` |

This is done in `setInitialProductTypes(on:)`, which calls:

- `AccountService.updateProductTypes(_:)`

### 2. Existing account migration

Pre-5.1.0 accounts are migrated to:

- `["myWeight"]`

File:

- `meApp/iOS/meApp/Data/Services/AccountMigrationService.swift`

The migration is invoked from `AccountService` before `ServiceRegistry.registerSessionServices()`, so `ProductTypeStore` sees a migrated value on first build.

### 3. Baby creation

When a baby is saved, `BabyService` appends `"baby"` if needed.

File:

- `meApp/iOS/meApp/Data/Services/BabyService.swift`

### 4. Baby deletion

When the last baby is deleted, `BabyService` removes `"baby"`.

File:

- `meApp/iOS/meApp/Data/Services/BabyService.swift`

If other babies remain, the flag stays.

### 5. BPM pairing

After successful BPM pairing, `BluetoothService.connectBpm(...)` appends `"myBloodPressure"` if needed.

File:

- `meApp/iOS/meApp/Data/Services/BluetoothService/BluetoothServiceBpmOperations.swift`

### 6. Reconstruction / sync

`ProductTypeStore` can also repopulate missing product types from already-synced devices and babies when the account has an empty `productTypes` array.

File:

- `meApp/iOS/meApp/Features/Common/Stores/ProductTypeStore.swift`

---

## How ProductTypeStore Works Now

`ProductTypeStore` is the session-scoped presentation store for the selector shown in dashboard, entry, and history.

It publishes:

- `availableItems: [ProductSelection]`
- `selectedItem: ProductSelection`

### Lifecycle

The store is registered only after login / active-account availability:

1. `AccountService.activeAccount` becomes non-`nil`
2. `AccountService` runs `migrateProductTypesIfNeeded(for:)`
3. `ServiceRegistry.registerSessionServices()` registers `ProductTypeStore`
4. `ProductTypeStore` subscribes to account, scale, and baby changes
5. `ProductTypeStore` rebuilds `availableItems`

### Rebuild logic

`ProductTypeStore.rebuild()` now uses this flow:

1. Read the active account
2. Resolve product types
3. Build `availableItems`
4. Restore persisted selection if available
5. If current selection is invalid, fall back to the first item

#### Resolve product types

`resolveProductTypes()` uses two paths:

**Primary path**

- if `account.productTypes` is non-empty, use it directly

**Reconstruction path**

- if `account.productTypes` is empty:
  - add `"myWeight"` if a scale device exists
  - add `"myBloodPressure"` if a BPM device exists
  - add `"baby"` if a baby scale exists or babies exist
  - if nothing can be derived, fall back to `["myWeight"]`
  - write the reconstructed value back onto the active account object

### Item construction

Once product types are resolved:

- `"myWeight"` -> append `.myWeight`
- `"myBloodPressure"` -> append `.myBloodPressure`
- `"baby"` -> append one `.baby(profile:)` per baby

If `"baby"` is present but no babies exist yet, the store shows a placeholder baby selection using:

- `BabyProfile.pendingSelectionId`
- display name `ProductTypeStrings.babyScale`

This supports the "signed up with Baby Scale but skipped adding a baby" flow.

### Selection persistence

Selection is still persisted per account in `KvStorage` using:

- `KvStorageKeys.selectedProductTypeKey(for:)`

That part of the previous implementation remains intact.

---

## Account Change Behavior

`ProductTypeStore` now listens for two distinct account-level changes:

### 1. Account ID changes

When the active account changes:

- babies are loaded for that account
- the store rebuilds
- persisted selection is restored for that account

### 2. Product type changes on the same account

The store also observes:

- `accountId`
- `productTypes`

as an `AccountSelectionSnapshot`

This matters because `productTypes` can change while the same account remains active, for example:

- signup sets initial product types
- adding the first baby appends `"baby"`
- deleting the last baby removes `"baby"`
- pairing a BPM appends `"myBloodPressure"`

Without this observer, the selector would not refresh reliably for same-account mutations.

---

## Verified Status Against The Original Gap List

This is the current state of the seven gaps described in the earlier design docs.

| # | Gap from older docs | Current status | Notes |
|---|---|---|---|
| 1 | `Account` has no `productTypes` | Implemented | `Account.productTypes: [String] = []` exists |
| 2 | `ProductTypeStore.rebuild()` uses device heuristic only | Implemented | Now uses `productTypes` first, reconstruction second |
| 3 | Signup does not write initial `productTypes` | Implemented | `SignupStore.setInitialProductTypes(on:)` |
| 4 | No migration default for existing users | Implemented | `migrateProductTypesIfNeeded(for:)` defaults to `["myWeight"]` |
| 5 | BPM pairing does not append `"myBloodPressure"` | Implemented | `BluetoothService.connectBpm(...)` updates account state |
| 6 | Last baby deletion does not remove `"baby"` | Implemented | `BabyService.deleteBaby(...)` removes it when no babies remain |
| 7 | Adding a baby post-signup does not append `"baby"` | Implemented | `BabyService.saveBaby(...)` appends it if needed |

So the older "implementation gaps" document is now outdated for iOS.

---

## Important Current Limitation

`productTypes` is still local-only.

That means:

- it is stored in SwiftData on the local `Account`
- it is not restored from server account payloads
- reinstall recovery depends on reconstructing from synced devices and babies

### Practical impact

If the app is reinstalled and no server-synced devices or baby profiles can reconstruct the product type set, the current code falls back to:

- `["myWeight"]`

This is a defensive fallback, but it is not the same as true server-backed product ownership.

### What server-backed storage would improve

If `productTypes` moves into the server account payload:

- reinstall would restore the same product availability immediately
- second-device login would restore the same product availability immediately
- deleted-device edge cases would no longer depend on reconstructing from hardware

Until then, the current client-side implementation is functionally correct for normal flows, but not a perfect substitute for server persistence.

---

## Related Files

Core model and service files:

- `meApp/iOS/meApp/Domain/Models/DB/Account.swift`
- `meApp/iOS/meApp/Data/Services/AccountService.swift`
- `meApp/iOS/meApp/Data/Services/AccountMigrationService.swift`
- `meApp/iOS/meApp/Features/Common/Stores/ProductTypeStore.swift`
- `meApp/iOS/meApp/Data/Services/BabyService.swift`
- `meApp/iOS/meApp/Data/Services/BluetoothService/BluetoothServiceBpmOperations.swift`
- `meApp/iOS/meApp/Features/Auth/Stores/SignupStore.swift`

Consumer / presentation files:

- `meApp/iOS/meApp/Domain/Models/Domain/Product/ProductSelection.swift`
- `meApp/iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift`
- `meApp/iOS/meApp/Features/Entry/Stores/EntryStore.swift`
- `meApp/iOS/meApp/Features/History/Stores/HistoryStore.swift`

---

## Recommended Follow-Up Work

If we continue evolving this area, the next best steps are:

1. Add `productTypes` to the server account payload and refresh path.
2. Stop relying on reconstruction for reinstall recovery.
3. Decide whether `"myWeight"` fallback should remain when reconstruction finds nothing.
4. Consider replacing raw strings with a typed enum if the surface grows further.

