# Product Types — Current iOS Implementation

**Last updated:** 2026-07-20  
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

`productTypes` is stored on the local SwiftData `Account` model **and** is now server-backed: it is part of the account payload (`AccountDTO.productTypes`, "auto-managed by the server"), decoded on account init, hydrated from the server on refresh when the local value is empty, and pushed back to the server via `PATCH` when it changes. Reconstruction from synced devices/babies remains only as a fallback for an empty payload.

---

## Core Rules (confirmed — MOB-686)

Two rules govern the whole `productTypes` surface. Both are **confirmed by UX/Product on MOB-686** and are enforced in code today.

### Rule 1 — `productTypes` is additive-only

Once a product type is earned (`"myWeight"`, `"myBloodPressure"`, or `"baby"`), it is **never removed** from the account. Every write path only ever *adds* a type; nothing subtracts.

- `AccountService.updateProductTypes(_:)` **unions** with the existing value — it can only grow the set.
- `ProductTypeStore.resolveProductTypes()` unions `account.productTypes` with device-/baby-derived types — it never drops a type earned elsewhere.
- **Baby is not an exception:** deleting the last baby profile (and even removing the baby scale device) does **not** remove `"baby"`. This preserves the recovery path and keeps "My Kids" available.

> **Consequence for "My Kids" (revised Rule A, MOB-686, 2026-06-30):** "My Kids" is **always enabled for all users** and **never toggles to disabled under any condition** — regardless of signup path, Add-Devices engagement, or deletion state. The Settings "My Kids" row is shown unconditionally (MOB-1226); the no-profile case is handled by the "No babies added yet" empty state (MOB-662), not by hiding/disabling the section.

### Rule 2 — Header/switcher label for the baby product

When `productTypes` contains `"baby"`, the header dropdown and Manual-Entry switcher show:

- **the baby's name** — one row per baby profile — when at least one baby profile exists; **"Baby Scale" is never shown once ≥1 baby exists**.
- **"Baby Scale"** (a single placeholder item) — when `"baby"` is present but **no** baby profile exists yet (e.g. signed up with a baby scale but skipped adding a baby, or deleted the last baby while `"baby"` stays additive).

The placeholder is built in `ProductTypeStore.rebuild()` using `BabyProfile.pendingSelectionId` + display name `ProductTypeStrings.babyScale`.

### Rule 3 — Adding a product makes it the active selection

Whenever the user adds a new product, the header/selector **switches to it** immediately (e.g. a BPM-only user who adds a baby lands on that baby as the selected product). This is done via `ProductTypeStore.selectLastAdded(_:)` at each add site:

| Added | Selected after add | Call site |
|---|---|---|
| Weight scale (Wi-Fi / BT-Wi-Fi / AppSync / A6) | `.myWeight` | each scale setup store |
| BPM device | `.myBloodPressure` | `BpmSetupStore` |
| Baby scale device (no profile yet) | `.baby` — "Baby Scale" placeholder | `BabyScaleSetupStore.saveScaleLocally()` |
| Baby profile (baby-scale setup flow) | `.baby(profile:)` — the new baby | `BabyScaleSetupStoreBabyProfileFlow.saveBabyProfile()` |
| Baby profile (Settings → My Kids) | `.baby(profile:)` — the new baby | `MyKidsStore.saveBabyProfile()` |

`selectLastAdded(_:)` sets `selectedItem` directly (it does **not** require the item to already be in `availableItems`); the next `rebuild()` reconciles it by `id` once the device/baby publisher fires, so the switch is robust to the async rebuild hop. Baby selections are built from the new `Baby` via `Baby.toBabyProfile()`.

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
var productTypes: [String] = ["myWeight"]
```

### Reconstruction source

If `productTypes` is empty, `ProductTypeStore` reconstructs it from:

- `ScaleService.scales`
- `BabyService.currentBabies`

This is meant to recover state after reinstall or first login on a fresh device.

---

## Where Product Types Are Written

### 1. Signup

On signup **finish**, `SignupStore.writeAccumulatedProductTypes()` writes the **union** of the product types for every device the user successfully added during signup (not just one initial device).

File:

- `meApp/iOS/meApp/Features/Auth/Stores/SignupStore.swift`

Per-device mapping (accumulated into the union):

| Signup device | Contributes |
|---|---|
| `.weightScale` | `"myWeight"` |
| `.bpm` | `"myBloodPressure"` |
| `.babyScale` | `"baby"` |

`writeAccumulatedProductTypes()` calls:

- `AccountService.updateProductTypes(_:)` (which also `PATCH`es the value to the server)

### 2. Existing account migration

Pre-5.1.0 accounts are migrated to:

- `["myWeight"]`

File:

- `meApp/iOS/meApp/Data/Services/AccountMigrationService.swift`

The migration (`migrateProductTypesIfNeeded(for:devices:)`) is invoked from `AccountService`. Note the current order: `ServiceRegistry.registerSessionServices()` runs **first** (synchronously), and the migration runs **afterward** in a deferred `Task { @MainActor … }`. `ProductTypeStore` reacts to the resulting `productTypes` change via its account observer.

### 3. Baby creation

When a baby is saved, `BabyService` appends `"baby"` if needed.

File:

- `meApp/iOS/meApp/Data/Services/BabyService.swift`

### 4. Baby deletion

**`"baby"` is never removed on deletion** (additive-only — see Core Rule 1). Deleting a baby only
updates the local baby list; `BabyService` does **not** call `removeProductType("baby")` for the
last baby. The `"baby"` flag persists so "My Kids" stays enabled and the header falls back to the
"Baby Scale" placeholder (Core Rule 2).

File:

- `meApp/iOS/meApp/Data/Services/BabyService.swift`

`reconcileBabyProductType()` therefore only ever *appends* `"baby"` when profiles exist; the prior
`removeBabyProductTypeIfLast()` path was deleted per MOB-686 revised Rule A.

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
2. `ServiceRegistry.registerSessionServices()` registers `ProductTypeStore` (synchronously)
3. `AccountService` runs `migrateProductTypesIfNeeded(for:devices:)` afterward in a deferred `Task { @MainActor … }`
4. `ProductTypeStore` subscribes to account, scale, and baby changes
5. `ProductTypeStore` rebuilds `availableItems` (and re-rebuilds when the deferred migration updates `productTypes`)

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

If `"baby"` is present but no babies exist yet, the store shows a single placeholder baby selection
(the **"Baby Scale"** header item — Core Rule 2) using:

- `BabyProfile.pendingSelectionId`
- display name `ProductTypeStrings.babyScale`

This covers both the "signed up with a Baby Scale but skipped adding a baby" flow **and** the
"deleted the last baby while `"baby"` stays additive" flow. As soon as ≥1 baby profile exists, the
placeholder is replaced by one row per baby (by name) and "Baby Scale" is no longer shown.

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
- pairing a BPM appends `"myBloodPressure"`

(Deletion never mutates `productTypes` — it is additive-only, Core Rule 1.)

Without this observer, the selector would not refresh reliably for same-account mutations.

---

## Verified Status Against The Original Gap List

This is the current state of the seven gaps described in the earlier design docs.

| # | Gap from older docs | Current status | Notes |
|---|---|---|---|
| 1 | `Account` has no `productTypes` | Implemented | `Account.productTypes: [String] = ["myWeight"]` exists (now also server-backed) |
| 2 | `ProductTypeStore.rebuild()` uses device heuristic only | Implemented | Now uses `productTypes` first, reconstruction second |
| 3 | Signup does not write initial `productTypes` | Implemented | `SignupStore.writeAccumulatedProductTypes()` (union of added devices) |
| 4 | No migration default for existing users | Implemented | `migrateProductTypesIfNeeded(for:devices:)` defaults to `["myWeight"]` |
| 5 | BPM pairing does not append `"myBloodPressure"` | Implemented | `BluetoothService.connectBpm(...)` updates account state |
| 6 | ~~Last baby deletion does not remove `"baby"`~~ | **Superseded** | Reversed by MOB-686 revised Rule A: `productTypes` is additive-only, so `"baby"` is **never** removed on deletion. The old removal path (`removeBabyProductTypeIfLast()`) was deleted. |
| 7 | Adding a baby post-signup does not append `"baby"` | Implemented | `BabyService.saveBaby(...)` appends it if needed |

So the older "implementation gaps" document is now outdated for iOS.

---

## Server-Backed Status

`productTypes` is now **server-backed** as well as locally persisted.

That means:

- it is stored in SwiftData on the local `Account`
- it is part of the server account payload (`AccountDTO.productTypes: [String]?`, "auto-managed by the server"), decoded on account init and hydrated on refresh when the local value is empty
- writes are pushed to the server: `AccountService.updateProductTypes` `PATCH`es the value (additive-only — see Core Rule 1), and signup sends it in `createAccount`. A `removeProductType` `PATCH` path exists on `AccountService` but is **no longer invoked in production** (the baby-removal caller was deleted per MOB-686 revised Rule A)
- reconstruction from synced devices and babies remains only as a **fallback** for an empty payload

### Practical impact

- **Reinstall / second-device login** restore the same product availability from the server payload directly — no longer dependent on reconstructing from hardware.
- If the server payload is empty *and* nothing can be reconstructed from synced devices/baby profiles, the code still falls back to `["myWeight"]` as a defensive default.

### Remaining nuance

Reconstruction logic is kept for resilience (empty payload / partial sync), so the client is robust even if the server value is missing. Because `productTypes` is additive-only (Core Rule 1), deleted-device / deleted-baby cases intentionally **keep** the earned type rather than dropping it — there is no client-side removal path in production.

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

1. ✅ *Done* — `productTypes` is now in the server account payload + refresh path.
2. Consider dropping (or further reducing) reliance on device/baby reconstruction now that the payload is authoritative — keep it only as an empty-payload fallback.
3. Decide whether the `"myWeight"` fallback should remain when both the payload and reconstruction find nothing.
4. Consider replacing raw strings with a typed enum if the surface grows further.

