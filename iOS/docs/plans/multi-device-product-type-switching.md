# Multi-Device / Product-Type Switching — Implementation Plan

> **Feature goal:** Allow users to switch between device types (Scale, BPM) and individual baby profiles via a shared header dropdown. The selected item propagates across Dashboard, Manual Entry, and History, with auto-selection rules for baby-linked entries and dynamic population from the user's registered devices and baby profiles.

---

## Table of Contents

1. [Current State](#1-current-state)
2. [Dropdown Design Requirements](#2-dropdown-design-requirements)
3. [Architecture Overview](#3-architecture-overview)
4. [Domain Model Changes](#4-domain-model-changes)
5. [ProductTypeStore — Global State Manager](#5-producttypestore--global-state-manager)
6. [Service Layer Changes](#6-service-layer-changes)
7. [Reusable Header Dropdown Component](#7-reusable-header-dropdown-component)
8. [Screen Integration](#8-screen-integration)
9. [Baby Entry Assignment — BLE Weight from Baby Scale](#9-baby-entry-assignment--ble-weight-from-baby-scale)
10. [Dynamic Dropdown Population](#10-dynamic-dropdown-population)
11. [Conditional UI Rendering Per Selection](#11-conditional-ui-rendering-per-selection)
12. [State Management Flow Diagram](#12-state-management-flow-diagram)
13. [Signup Flow — Initial Product Type Selection](#13-signup-flow--initial-product-type-selection)
14. [Edge Cases](#14-edge-cases)
15. [Step-by-Step Implementation Order](#15-step-by-step-implementation-order)
16. [Pseudocode Examples](#16-pseudocode-examples)
17. [Open Questions for the Team](#17-open-questions-for-the-team)

---

## 1. Current State

### What exists today


| Component                      | Current behaviour                                                              |
| ------------------------------ | ------------------------------------------------------------------------------ |
| `DeviceType` enum              | Two cases: `.scale`, `.bpm` — stored as `Entry.deviceType: String`             |
| `Device.deviceType`            | Raw `String?` field — currently hardcoded to `"scale"` in `Device.init(from:)` |
| `ScaleService.scalesPublisher` | Publishes `[ScaleItemInfo]` (all scale-type devices)                           |
| `DashboardStore`               | Reads weight/body-comp data only — no concept of active product type           |
| `EntryStore`                   | Saves entries with `deviceType` from scale logic only                          |
| `HistoryStore`                 | Loads all entries regardless of device type                                    |
| Tab navigation                 | Five fixed tabs; no header-level product-type control                          |


### What is missing

- A **global store** that holds the currently selected product type / baby profile and the available options.
- A **reusable header dropdown** rendered at the top of Dashboard, Entry, and History screens.
- **Filtering logic** in each screen store so that data responds to the selected item.
- **Baby auto-selection** when navigating to or viewing a baby-linked entry.
- **Device-aware population** — device types and individual baby profiles must both appear in the dropdown.

---

## 2. Dropdown Design Requirements

The product-type selector is a bottom sheet that appears when the user taps the header button. It lists items in this order:

```
My History
─────────────────────────────────
My Weight              ✓
─────────────────────────────────
My Blood Pressure      ○
─────────────────────────────────
Tammy Thompson         ○    ← individual baby name
─────────────────────────────────
Sally Thompson         ○    ← individual baby name
─────────────────────────────────
Katey                  ○    ← individual baby name
```

**Figma note:** *"If you have multiple me.health devices & multiple babies the names of each child will be listed here and will have their own history view."*

### Dropdown item rules

1. **"My Weight"** — shown when the user has a weight scale device.
2. **"My Blood Pressure"** — shown when the user has a BPM device.
3. **Each baby by name** — shown when the user has a baby scale device. One row per `Baby` record in SwiftData, sorted alphabetically. Babies are stored in a separate table — not on the `Device` model.
4. Babies are identified by `Baby.id`, not by any device ID. `Entry.babyId` = `Baby.id` links an entry to a specific child.

### Dropdown item model

The dropdown does not list flat device type categories. It uses an **enum with associated values**:

- `.myWeight` — primary user's scale data
- `.myBloodPressure` — primary user's BPM data
- `.baby(profile: BabyProfile)` — one case per registered baby, with the baby's name as display text

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          ProductTypeStore                           │
│  - selectedItem: ProductSelection        (global @Published)        │
│  - availableItems: [ProductSelection]    (devices + baby profiles)  │
│  - Registered in ServiceRegistry at session start                   │
└──────────────┬──────────────────────────────────────────────────────┘
               │  @Injector (same singleton)
       ┌───────┼───────────────────────────────┐
       │       │                               │
┌──────▼──┐ ┌──▼─────────┐            ┌───────▼──────┐
│Dashboard│ │EntryStore  │            │HistoryStore  │
│  Store  │ │            │            │              │
│observes │ │observes    │            │observes      │
│selected │ │selected    │            │selected      │
│item →   │ │item →      │            │item →        │
│reloads  │ │updates UI  │            │filters data  │
│metrics  │ │fields      │            │per baby ID   │
└─────────┘ └────────────┘            └──────────────┘
       │            │                        │
       └────────────┴────────────────────────┘
                        │
           ┌────────────▼─────────────────────┐
           │      ProductTypeHeaderView        │
           │  (reusable SwiftUI view)          │
           │  - Bottom sheet or Menu picker    │
           │  - "My Weight" / "My Blood        │
           │    Pressure" / baby names         │
           └───────────────────────────────────┘
```

**Why a shared singleton store instead of passing state via environment?**

- The selection must survive tab switches (Dashboard → History → back).
- All three screens need *write* access (baby auto-selection can change it).
- Registering it via `ServiceRegistry` keeps it consistent with how all other cross-feature state is managed in this project (e.g. `EntryService`, `AccountService`).
- `@Injector` access pattern is already well-understood and tested across the codebase.

---

## 4. Domain Model Changes

### 4.1 Extend `DeviceType` enum

**File:** `iOS/meApp/Domain/Models/Domain/Entry/DeviceType.swift`

```swift
// BEFORE
enum DeviceType: String, Codable, Equatable {
    case scale
    case bpm
}

// AFTER
enum DeviceType: String, Codable, Equatable, CaseIterable {
    case scale       // weight scale device
    case bpm         // blood pressure monitor device
    case babyScale   // baby scale device (hardware type stored on Device.deviceType)
}
```

**Two separate uses of this enum:**


| Use                 | Value         | Meaning                                  |
| ------------------- | ------------- | ---------------------------------------- |
| `Device.deviceType` | `"babyScale"` | The hardware is a baby scale             |
| `Entry.deviceType`  | `"babyScale"` | This entry was recorded via a baby scale |


Individual babies are distinguished by `Entry.babyId` (FK to the `Baby` SwiftData table) — not by device type alone.

### 4.2 `ProductSelection` — the dropdown item model

**File (new):** `iOS/meApp/Domain/Models/Domain/Product/ProductSelection.swift`

```swift
/// Represents a single selectable item in the product-type header dropdown.
///
/// - myWeight: the primary user's weight/body-comp data (scale devices)
/// - myBloodPressure: the primary user's BPM data
/// - baby: an individual child profile, shown by the child's name
enum ProductSelection: Equatable, Hashable, Identifiable {

    case myWeight
    case myBloodPressure
    case baby(profile: BabyProfile)

    // MARK: - Identifiable

    var id: String {
        switch self {
        case .myWeight:           return "my_weight"
        case .myBloodPressure:    return "my_blood_pressure"
        case .baby(let profile):  return "baby_\(profile.id)"
        }
    }

    // MARK: - Display

    var displayName: String {
        switch self {
        case .myWeight:           return "My Weight"
        case .myBloodPressure:    return "My Blood Pressure"
        case .baby(let profile):  return profile.name
        }
    }

    var iconName: String {
        switch self {
        case .myWeight:           return "scalemass"
        case .myBloodPressure:    return "heart.fill"
        case .baby:               return "figure.2.and.child.holdinghands"
        }
    }

    // MARK: - Convenience

    /// True if this item represents the primary user (not a baby).
    var isPersonalSelection: Bool {
        switch self {
        case .myWeight, .myBloodPressure: return true
        case .baby: return false
        }
    }

    /// The DeviceType to use when saving or filtering entries for this selection.
    var deviceType: DeviceType {
        switch self {
        case .myWeight:           return .scale
        case .myBloodPressure:    return .bpm
        case .baby:               return .baby
        }
    }
}
```

### 4.3 `Baby` — new SwiftData model (separate table)

**File (new):** `iOS/meApp/Domain/Models/DB/Baby.swift`

Baby data lives in its **own SwiftData table**, separate from `Device`. The `Device` with `deviceType == "babyScale"` signals that baby scale hardware is registered. The `Baby` table stores the individual children's profiles (names, IDs).

```swift
/// Table: baby
///
/// Stores child profiles registered under a user account.
/// Each baby appears as a separate item in the product-type header dropdown.
///
/// | Column Name   | Type    | Description                                    |
/// |---------------|---------|------------------------------------------------|
/// | id            | string  | Unique baby ID (PK, server-assigned or UUID)   |
/// | accountId     | string  | FK to the parent account                       |
/// | name          | string  | Baby's display name ("Tammy Thompson")         |
/// | deviceId      | string? | FK to the linked baby scale Device (optional)  |
/// | isSynced      | bool    | Whether this record is synced to the server    |

import Foundation
import SwiftData

@Model
final class Baby {
    @Attribute(.unique) var id: String
    var accountId: String       // parent account
    var name: String            // display name shown in the dropdown
    var deviceId: String?       // links to the baby scale Device.id (optional)
    var isSynced: Bool

    init(id: String = UUID().uuidString,
         accountId: String,
         name: String,
         deviceId: String? = nil,
         isSynced: Bool = false) {
        self.id = id
        self.accountId = accountId
        self.name = name
        self.deviceId = deviceId
        self.isSynced = isSynced
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
```

**How it is populated:** During baby scale setup, after the hardware is paired (`Device` saved with `deviceType == "babyScale"`), the user enters the baby's name. A `Baby` record is created and saved to SwiftData. Multiple babies can be registered per account (each gets their own row).

### 4.4 `BabyProfile` — lightweight domain struct derived from `Baby`

**File (new):** `iOS/meApp/Domain/Models/Domain/Product/BabyProfile.swift`

```swift
/// Read-only domain view of a baby, used in ProductSelection and the UI.
/// Derived from the `Baby` SwiftData model — not stored separately.
struct BabyProfile: Identifiable, Equatable, Hashable {
    let id: String       // = Baby.id
    let name: String     // = Baby.name ("Tammy Thompson")
    let deviceId: String? // = Baby.deviceId (optional link to baby scale)
}
```

### 4.5 Entry model — `babyId` field

**File:** `iOS/meApp/Domain/Models/DB/Entry.swift`

Entries for a baby need to be linked to the specific `Baby` record so they can be filtered per-child:

```swift
@Model
final class Entry {
    // ... existing fields ...
    var deviceType: String   // "scale", "bpm", "babyScale"
    var babyId: String?      // Non-nil only when deviceType == "babyScale"
                              // = Baby.id — identifies which child this entry belongs to
}
```

> **Migration note:** `babyId` is a new optional column. SwiftData handles this transparently — existing entries get `nil`. No migration class needed.

---

## 5. ProductTypeStore — Global State Manager

**File (new):** `iOS/meApp/Features/Common/Stores/ProductTypeStore.swift`

```swift
/// Global store that manages the currently selected product type / baby profile
/// and the ordered list of available items in the header dropdown.
///
/// Dropdown order (per Figma):
///   1. My Weight        (if user has a scale device)
///   2. My Blood Pressure (if user has a BPM device)
///   3. [Baby names...]  (one per registered baby profile, sorted by name)
///
/// Registered via ServiceRegistry after login.
/// Access via: @Injector var productTypeStore: ProductTypeStoreProtocol
@MainActor
final class ProductTypeStore: ObservableObject, ProductTypeStoreProtocol {

    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var babyService: BabyServiceProtocol    // reads Baby SwiftData table
    @Injector private var logger: LoggerServiceProtocol

    // MARK: - Public State

    /// The item currently selected in the header dropdown.
    @Published private(set) var selectedItem: ProductSelection = .myWeight

    /// Ordered list of items for the dropdown, rebuilt whenever devices or baby
    /// profiles change.
    @Published private(set) var availableItems: [ProductSelection] = [.myWeight]

    private var cancellables = Set<AnyCancellable>()

    init() {
        subscribeToChanges()
    }

    // MARK: - Public API

    func select(_ item: ProductSelection) {
        guard item != selectedItem else { return }
        selectedItem = item
        persistSelection(item)
        logger.info("ProductTypeStore: switched to \(item.displayName)", tag: "ProductTypeStore")
    }

    /// Auto-selects the baby matching the given Baby.id.
    /// Called when a baby entry is opened in History.
    func autoSelectBaby(babyId: String) {
        if let babyItem = availableItems.first(where: { $0.id == "baby_\(babyId)" }) {
            select(babyItem)
        }
    }

    /// Sets the active selection to the last added device / baby.
    /// Called after a device or baby is successfully added during setup.
    func selectLastAdded(_ item: ProductSelection) {
        select(item)
    }

    /// Restore to the first available item (typically "My Weight").
    func resetToDefault() {
        if let first = availableItems.first {
            select(first)
        }
    }

    // MARK: - Persistence

    /// Persist selection so it survives app restarts.
    /// Stored via KvStorageService using the item's stable id string.
    private func persistSelection(_ item: ProductSelection) {
        // kvStorage.set(item.id, forKey: KvStorageKeys.lastSelectedProductType)
        // Implementation: store item.id; on init, resolve the matching ProductSelection
        // after availableItems is first populated.
    }

    /// Called once after the first rebuild() — restores the last persisted selection if valid.
    private func restorePersistedSelection() {
        // let savedId = kvStorage.string(forKey: KvStorageKeys.lastSelectedProductType)
        // if let savedId, let match = availableItems.first(where: { $0.id == savedId }) {
        //     selectedItem = match
        // }
    }

    // MARK: - Rebuild on Changes

    private func subscribeToChanges() {
        // When devices change — check if a babyScale device is present
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.rebuild() }
            .store(in: &cancellables)

        // When the Baby table changes — a baby was added, removed, or renamed
        babyService.babiesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.rebuild() }
            .store(in: &cancellables)
    }

    private func rebuild() {
        let devices = scaleService.currentDevices
        let babies  = babyService.currentBabies

        var items: [ProductSelection] = []

        // 1. My Weight — present if any scale-type device is registered
        if devices.contains(where: { $0.device.deviceType == DeviceType.scale.rawValue }) {
            items.append(.myWeight)
        }

        // 2. My Blood Pressure — present if any BPM device is registered
        if devices.contains(where: { $0.device.deviceType == DeviceType.bpm.rawValue }) {
            items.append(.myBloodPressure)
        }

        // 3. Individual babies — shown ONLY when a babyScale device is registered.
        //    Names come from the separate Baby SwiftData table (not from Device).
        //    One dropdown item per Baby row, sorted alphabetically.
        let hasBabyScaleDevice = devices.contains(where: {
            $0.device.deviceType == DeviceType.babyScale.rawValue
        })
        if hasBabyScaleDevice {
            for baby in babies.sorted(by: { $0.name < $1.name }) {
                let profile = BabyProfile(id: baby.id, name: baby.name, deviceId: baby.deviceId)
                items.append(.baby(profile: profile))
            }
        }

        // Fallback: always show at least "My Weight"
        if items.isEmpty { items = [.myWeight] }

        availableItems = items

        // If the current selection is no longer valid, fall back to the first item
        if !items.contains(selectedItem) {
            selectedItem = items[0]
        }
    }
}
```

**Protocol (new):** `iOS/meApp/Domain/Services/ProductTypeStoreProtocol.swift`

```swift
@MainActor
protocol ProductTypeStoreProtocol: AnyObject {
    var selectedItem: ProductSelection { get }
    var availableItems: [ProductSelection] { get }
    func select(_ item: ProductSelection)
    func autoSelectBaby(profileId: String)
    func resetToDefault()
}
```

**ServiceRegistry registration:**

```swift
// In ServiceRegistry.registerSessionServices()
let productTypeStore = ProductTypeStore()
DependencyContainer.shared.register(productTypeStore as ProductTypeStoreProtocol)
```

> Register as a **session service** (after login) — not essential — because it depends on `scaleService`.

---

## 6. Service Layer Changes

### 6.1 `ScaleServiceProtocol` — expose all device types

Verify that `scalesPublisher` emits BPM and baby devices in addition to weight scales, or add a broader publisher:

```swift
// Add to ScaleServiceProtocol if scalesPublisher is scale-only:
var allDevicesPublisher: Published<[ScaleItemInfo]>.Publisher { get }
var currentDevices: [ScaleItemInfo] { get }  // synchronous last-known value
```

> **Open question:** Confirm with the team whether `scalesPublisher` today emits BPM/baby devices or only weight scales. If scale-only, use `allDevicesPublisher` in `ProductTypeStore`.

### 6.2 `BabyServiceProtocol` — new service for the Baby SwiftData table

**File (new):** `iOS/meApp/Domain/Services/BabyServiceProtocol.swift`

```swift
/// Manages baby records in the local SwiftData Baby table.
/// ProductTypeStore subscribes to babiesPublisher to rebuild the dropdown
/// whenever a baby is added, removed, or renamed.
@MainActor
protocol BabyServiceProtocol: AnyObject {
    /// Emits the full list of Baby records whenever it changes.
    var babiesPublisher: Published<[Baby]>.Publisher { get }

    /// Synchronous last-known value (for ProductTypeStore.rebuild()).
    var currentBabies: [Baby] { get }

    /// Save a new baby (called at the end of baby scale setup flow).
    func saveBaby(name: String, accountId: String, deviceId: String?) async throws -> Baby

    /// Update a baby's name.
    func updateBaby(_ baby: Baby, name: String) async throws

    /// Delete a baby record.
    func deleteBaby(_ baby: Baby) async throws
}
```

**Implementation:** `iOS/meApp/Data/Services/BabyService.swift` — thin wrapper over a `BabyRepository` that performs SwiftData CRUD on the `Baby` model.

**The full chain from hardware to dropdown:**

```
── Baby scale setup flow ──────────────────────────────────────────────────────
  Step 1: Device paired → Device.deviceType = "babyScale" saved to SwiftData
  Step 2: User enters baby's name → BabyService.saveBaby(name:deviceId:)
          → Baby(id: "b1", name: "Tammy Thompson", deviceId: device.id) saved

── ScaleService & BabyService emit ───────────────────────────────────────────
  scalesPublisher emits (device list now includes babyScale device)
  babiesPublisher emits (Baby table now has Tammy's record)

── ProductTypeStore.rebuild() ────────────────────────────────────────────────
  hasBabyScaleDevice = true  (Device with deviceType "babyScale" found)
  babies = [Baby(id: "b1", name: "Tammy Thompson")]
  → availableItems gains .baby(BabyProfile(id: "b1", name: "Tammy Thompson"))

── Dropdown ──────────────────────────────────────────────────────────────────
  My Weight           ✓
  My Blood Pressure   ○
  Tammy Thompson      ○   ← from Baby table
```

> **Why two separate signals?** `Device.deviceType == "babyScale"` tells us whether baby scale hardware is registered. The `Baby` table tells us *which* babies. These can update independently — e.g. a user adds a second baby without re-pairing the device — so both subscriptions are needed.

### 6.3 `EntryServiceProtocol` — filter by selection

Add overloads that accept a `ProductSelection` filter:

```swift
// New methods in EntryServiceProtocol
func fetchMonths(for selection: ProductSelection) async throws -> [HistoryMonth]
func fetchEntries(for month: HistoryMonth, selection: ProductSelection) async throws -> [Entry]
```

SwiftData predicate logic:

```swift
// In EntryService concrete implementation:
func fetchMonths(for selection: ProductSelection) async throws -> [HistoryMonth] {
    let predicate: Predicate<Entry>
    switch selection {
    case .myWeight:
        predicate = #Predicate<Entry> { entry in
            entry.accountId == accountId &&
            entry.deviceType == "scale" &&
            entry.operationType != "delete"
        }
    case .myBloodPressure:
        predicate = #Predicate<Entry> { entry in
            entry.accountId == accountId &&
            entry.deviceType == "bpm" &&
            entry.operationType != "delete"
        }
    case .baby(let profile):
        let babyId = profile.id
        predicate = #Predicate<Entry> { entry in
            entry.accountId == accountId &&
            entry.deviceType == "babyScale" &&
            entry.babyId == babyId &&          // KEY: per-child filter using Baby.id
            entry.operationType != "delete"
        }
    }
    // ... run query and group into HistoryMonth objects
}
```

---

## 7. Reusable Header Dropdown Component

The Figma mockup shows the selector as a **bottom sheet / modal picker** (not a compact Menu). The sheet has a title "My History" with an X close button, and each item shows a radio-button style selection indicator.

**File (new):** `iOS/meApp/Features/Common/Views/Components/ProductTypeSelectorSheet.swift`

```swift
/// Bottom sheet that shows the list of selectable product types / baby profiles.
/// Presented modally when the user taps the header dropdown trigger.
struct ProductTypeSelectorSheet: View {
    @ObservedObject var store: ProductTypeStore
    @Binding var isPresented: Bool
    let title: String   // "My History", "My Dashboard", "Log for…" etc.

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button { isPresented = false } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(Theme.shared.colorScheme.onSurface)
                }
                Spacer()
                Text(title)
                    .font(Theme.shared.typography.headlineSmall)
                Spacer()
                // Balance the X button
                Image(systemName: "xmark").opacity(0)
            }
            .padding()

            Divider()

            // Item list
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(store.availableItems) { item in
                        ProductTypeSelectorRow(
                            item: item,
                            isSelected: store.selectedItem == item
                        ) {
                            store.select(item)
                            isPresented = false
                        }
                        Divider()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

/// A single row in the selector sheet.
struct ProductTypeSelectorRow: View {
    let item: ProductSelection
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(item.displayName)
                    .font(Theme.shared.typography.bodyLarge)
                    .foregroundStyle(itemTextColor)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Theme.shared.colorScheme.onSurface)
                } else {
                    Image(systemName: "circle")
                        .foregroundStyle(Theme.shared.colorScheme.outline)
                }
            }
            .padding()
        }
        .buttonStyle(.plain)
    }

    private var itemTextColor: Color {
        // Match Figma: "My Weight" in blue, "My Blood Pressure" in green,
        // baby names in purple/teal — confirm exact colours with design.
        switch item {
        case .myWeight:        return Theme.shared.colorScheme.primary
        case .myBloodPressure: return .green  // replace with theme token
        case .baby:            return .purple  // replace with theme token
        }
    }
}
```

**Header trigger button** (embedded in each screen's nav bar or header area):

```swift
/// Compact button that shows the currently selected item name and opens the sheet.
struct ProductTypeHeaderButton: View {
    @ObservedObject var store: ProductTypeStore
    @Binding var isSheetPresented: Bool

    var body: some View {
        // Only show if there's more than one choice
        if store.availableItems.count > 1 {
            Button {
                isSheetPresented = true
            } label: {
                HStack(spacing: 4) {
                    Text(store.selectedItem.displayName)
                        .font(Theme.shared.typography.bodyMedium)
                        .foregroundStyle(Theme.shared.colorScheme.onSurface)
                    Image(systemName: "chevron.down")
                        .font(.caption2)
                        .foregroundStyle(Theme.shared.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

---

## 8. Screen Integration

### 8.1 DashboardScreen

```swift
struct DashboardScreen: View {
    @StateObject private var store = DashboardStore()
    @StateObject private var productTypeStore = /* resolve from DI */
    @State private var isSelectorSheetPresented = false

    var body: some View {
        NavigationStack {
            /* existing content */
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    ProductTypeHeaderButton(
                        store: productTypeStore,
                        isSheetPresented: $isSelectorSheetPresented
                    )
                }
            }
            .sheet(isPresented: $isSelectorSheetPresented) {
                ProductTypeSelectorSheet(
                    store: productTypeStore,
                    isPresented: $isSelectorSheetPresented,
                    title: "My Dashboard"
                )
            }
        }
    }
}
```

**In `DashboardStore`**, subscribe to selection changes:

```swift
@Injector var productTypeStore: ProductTypeStoreProtocol

private func subscribeToProductTypeChanges() {
    (productTypeStore as? ProductTypeStore)?.$selectedItem
        .dropFirst()
        .receive(on: DispatchQueue.main)
        .sink { [weak self] _ in
            Task { await self?.reloadForCurrentSelection() }
        }
        .store(in: &cancellables)
}

private func reloadForCurrentSelection() async {
    await dataManager.reload(for: productTypeStore.selectedItem)
}
```

### 8.2 ManualEntryScreen / EntryStore

```swift
struct ManualEntryScreen: View {
    @StateObject private var store = EntryStore()
    @StateObject private var productTypeStore = /* resolve from DI */
    @State private var isSelectorSheetPresented = false

    var body: some View {
        /* existing content */
        .toolbar {
            ToolbarItem(placement: .principal) {
                ProductTypeHeaderButton(
                    store: productTypeStore,
                    isSheetPresented: $isSelectorSheetPresented
                )
            }
        }
        .sheet(isPresented: $isSelectorSheetPresented) {
            ProductTypeSelectorSheet(
                store: productTypeStore,
                isPresented: $isSelectorSheetPresented,
                title: "Log for…"
            )
        }
    }
}
```

**In `EntryStore`**, reset form on selection change:

```swift
private func subscribeToProductTypeChanges() {
    (productTypeStore as? ProductTypeStore)?.$selectedItem
        .dropFirst()
        .receive(on: DispatchQueue.main)
        .sink { [weak self] newItem in
            self?.resetFormForSelection(newItem)
        }
        .store(in: &cancellables)
}

private func resetFormForSelection(_ item: ProductSelection) {
    manualEntryForm = ManualEntryForm()
    switch item {
    case .myWeight:
        showMetrics = true
        canShowOtherBodyMetrics = true
    case .myBloodPressure:
        showMetrics = false
        canShowOtherBodyMetrics = false
        // Show systolic, diastolic, pulse fields
    case .baby(let profile):
        showMetrics = false
        canShowOtherBodyMetrics = false
        activeBabyId = profile.id   // stored on EntryStore, used when saving
        // Show weight-only field, labelled with the baby's name
    }
}

// When saving:
func saveEntry() async {
    let selection = productTypeStore.selectedItem
    let babyId: String? = {
        if case .baby(let p) = selection { return p.id }
        return nil
    }()
    // Create Entry with:
    //   deviceType = selection.deviceType.rawValue   ("scale", "bpm", "babyScale")
    //   babyId     = babyId                          (Baby.id — NOT Device.id)
}
```

### 8.3 HistoryListScreen / HistoryStore

```swift
struct HistoryListScreen: View {
    @StateObject private var store = HistoryStore()
    @StateObject private var productTypeStore = /* resolve from DI */
    @State private var isSelectorSheetPresented = false

    var body: some View {
        /* existing content */
        .toolbar {
            ToolbarItem(placement: .principal) {
                ProductTypeHeaderButton(
                    store: productTypeStore,
                    isSheetPresented: $isSelectorSheetPresented
                )
            }
        }
        .sheet(isPresented: $isSelectorSheetPresented) {
            ProductTypeSelectorSheet(
                store: productTypeStore,
                isPresented: $isSelectorSheetPresented,
                title: "My History"
            )
        }
    }
}
```

**In `HistoryStore`**, re-fetch on selection change:

```swift
@Injector var productTypeStore: ProductTypeStoreProtocol

private func subscribeToProductTypeChanges() {
    (productTypeStore as? ProductTypeStore)?.$selectedItem
        .dropFirst()
        .receive(on: DispatchQueue.main)
        .sink { [weak self] _ in
            Task { await self?.loadMonthsInternal(canShowLoader: true) }
        }
        .store(in: &cancellables)
}

private func loadMonthsInternal(canShowLoader: Bool) async {
    let selection = productTypeStore.selectedItem
    months = try await entryService.fetchMonths(for: selection)
    isEmptyState = months.isEmpty
}
```

---

## 9. Baby Entry Assignment — BLE Weight from Baby Scale

When a BLE weight reading arrives from a baby scale, the app must assign it to the correct baby. The decision logic depends on how many babies the user has registered and which item is currently selected in the header.

### Assignment logic

```
BLE weight received from baby scale
        │
        ├── User has 1 baby registered
        │       → Auto-assign to that baby directly
        │         Entry.babyId = babies[0].id
        │
        ├── User has 2+ babies AND selectedItem == .baby(profile)
        │       → Auto-assign to the currently selected baby
        │         Entry.babyId = selectedItem.baby.id
        │
        └── User has 2+ babies AND selectedItem != .baby
                → Show alert: "Which baby is this entry for?"
                  List all babies as buttons
                  User picks → Entry.babyId = chosenBaby.id
                             → selectedItem switches to that baby
```

### Implementation in EntryStore

```swift
@Injector var babyService: BabyServiceProtocol
@Injector var productTypeStore: ProductTypeStoreProtocol
@Injector var notificationService: NotificationHelperServiceProtocol

func handleBabyScaleWeight(_ data: BTScaleData) {
    let babies = babyService.currentBabies

    switch babies.count {
    case 0:
        // No baby registered yet — prompt user to add a baby first
        notificationService.showAlert(
            AlertModel(title: "No baby registered",
                       message: "Add a baby in Settings before logging.")
        )

    case 1:
        // Only one baby — assign directly, no prompt needed
        let baby = babies[0]
        productTypeStore.autoSelectBaby(babyId: baby.id)
        populateFormFromScaleData(data, babyId: baby.id)

    default:
        // Multiple babies — check if user already has a baby selected
        if case .baby(let profile) = productTypeStore.selectedItem {
            // Already on a baby — assign to the selected one
            populateFormFromScaleData(data, babyId: profile.id)
        } else {
            // Not on a baby — show picker alert
            showBabyPickerAlert(for: data, babies: babies)
        }
    }
}

private func showBabyPickerAlert(for data: BTScaleData, babies: [Baby]) {
    let buttons = babies.map { baby in
        AlertButtonModel(title: baby.name) { [weak self] in
            self?.productTypeStore.autoSelectBaby(babyId: baby.id)
            self?.populateFormFromScaleData(data, babyId: baby.id)
        }
    }
    notificationService.showAlert(
        AlertModel(
            title: "Who is this for?",
            message: "Select which baby this weight entry belongs to.",
            buttons: buttons
        )
    )
}

private func populateFormFromScaleData(_ data: BTScaleData, babyId: String) {
    activeBabyId = babyId
    // populate weight field from scale data
}
```

### History — tapping a baby entry

When the user taps an entry in History that belongs to a specific baby, auto-switch the header selection to that baby:

```swift
// In HistoryStore:
func viewEntry(_ entry: Entry) {
    if entry.deviceType == DeviceType.babyScale.rawValue,
       let babyId = entry.babyId {
        productTypeStore.autoSelectBaby(babyId: babyId)
    }
    router.push(.entryDetail(entry: entry))
}
```

### Saving a baby entry

```swift
// In EntryStore.saveEntry():
Entry(
    deviceType: DeviceType.babyScale.rawValue,
    babyId: activeBabyId,   // set by populateFormFromScaleData or form selection
    operationType: OperationType.create.rawValue,
    ...
)
// Note: Entry.babyId = Baby.id — NOT Device.id. Device is not referenced on the entry.
```

---

## 10. Dynamic Dropdown Population

The `ProductTypeStore.rebuildAvailableItems()` is called whenever:

1. `scaleService.scalesPublisher` emits (device added/removed)
2. `babyProfileService.babyProfilesPublisher` emits (baby added/removed/renamed)

### Dropdown order (per Figma)

```
1. My Weight           ← only if user has a scale device
2. My Blood Pressure   ← only if user has a BPM device
3. [baby names...]     ← one per baby profile, sorted alphabetically
```

### Device type → dropdown mapping


| `Device.deviceType` string | Maps to dropdown item                                       |
| -------------------------- | ----------------------------------------------------------- |
| `"scale"`                  | "My Weight"                                                 |
| `"bpm"`                    | "My Blood Pressure"                                         |
| `"baby"`                   | (not used directly — babies come from `BabyProfileService`) |


> **Note:** Baby profiles are separate from device registration. A baby scale device (`Device.deviceType == "baby"`) provides the hardware connection, but the baby profiles (names, IDs) are managed by `BabyProfileService`. The two are linked by `babyProfileId` on entries.

---

## 11. Conditional UI Rendering Per Selection

### Dashboard

```swift
@ViewBuilder
private var dashboardContent: some View {
    switch productTypeStore.selectedItem {
    case .myWeight:
        ScaleDashboardContent(store: dashboardStore)
    case .myBloodPressure:
        BPMDashboardContent(store: dashboardStore)
    case .baby(let profile):
        BabyDashboardContent(store: dashboardStore, babyProfile: profile)
    }
}
```

### Manual Entry

```swift
@ViewBuilder
private var entryContent: some View {
    switch productTypeStore.selectedItem {
    case .myWeight:
        ScaleManualEntryContent(store: entryStore)
    case .myBloodPressure:
        BPMManualEntryContent(store: entryStore)
    case .baby(let profile):
        BabyManualEntryContent(store: entryStore, babyProfile: profile)
        // Heading shows baby's name, e.g. "Log for Tammy Thompson"
    }
}
```

### History Entry Cards

```swift
// EntryCard renders metrics appropriate to the selection:
EntryCard(entry: entry, selection: productTypeStore.selectedItem)
// - .myWeight → shows weight, BMI, body fat, etc.
// - .myBloodPressure → shows systolic/diastolic/pulse
// - .baby → shows child weight
```

---

## 12. State Management Flow Diagram

```
User taps header button
        │
        ▼
ProductTypeSelectorSheet appears
        │
User taps "Tammy Thompson"
        │
        ▼
productTypeStore.select(.baby(profile: tammyProfile))
        │
        ├──► @Published selectedItem changes
        │
        ├──► DashboardStore.$selectedItem.sink
        │         └──► dataManager.reload(for: .baby(tammyProfile))
        │              └──► Shows Tammy's weight history on Dashboard
        │
        ├──► EntryStore.$selectedItem.sink
        │         └──► resetFormForSelection(.baby(tammyProfile))
        │              └──► Form shows "Log for Tammy Thompson"
        │                   with child weight field
        │
        └──► HistoryStore.$selectedItem.sink
                  └──► fetchMonths(for: .baby(tammyProfile))
                       └──► predicate: deviceType == "babyScale"
                                    && babyId == tammyProfile.id
                            Shows only Tammy's history entries
```

---

## 13. Signup Flow — Initial Product Type Selection

During signup, the user selects which product type they own. This sets the initial `selectedItem` in `ProductTypeStore` so that when they reach the Dashboard for the first time, the correct product-related content is shown immediately.

### Signup step

The signup flow (in `Features/Auth/`) should include a product type selection screen:

```
Which product do you have?

  ○ Weight Scale
  ○ Blood Pressure Monitor
  ○ Baby Scale
```

The chosen product type is saved to the `Account` model (e.g. `Account.initialProductType: String?`) and synced to the backend.

### How it sets the default selection

After signup completes and the user lands on the Dashboard:

```swift
// In ProductTypeStore.restoreOrApplySignupDefault():
// 1. Check KvStorageService for a persisted selection → use it if found
// 2. Else check Account.initialProductType → convert to ProductSelection, use it
// 3. Else fall back to availableItems.first
```

### How the flow changes per product type


| Signup choice          | Dashboard shows                                                             | Device setup flow                    |
| ---------------------- | --------------------------------------------------------------------------- | ------------------------------------ |
| Weight Scale           | Weight graph, metrics                                                       | Existing scale pairing flow          |
| Blood Pressure Monitor | BPM chart (systolic/diastolic trend)                                        | BPM device pairing (new flow)        |
| Baby Scale             | Baby weight trend — but shows a name picker first if no baby registered yet | Baby scale pairing + baby name entry |


### Settings — device management & product switching

The Settings screen shows all registered devices grouped by type. When a user adds a new device from Settings:

1. **Adding a BPM device** → `scalesPublisher` emits → `ProductTypeStore.rebuild()` adds "My Blood Pressure" to the dropdown → `productTypeStore.selectLastAdded(.myBloodPressure)` is called → header now shows "My Blood Pressure" as the active selection, because it is the last connected device.
2. **Adding a baby scale + baby** → `scalesPublisher` emits (babyScale device), `babiesPublisher` emits (new Baby row) → dropdown gains the baby's name → `productTypeStore.selectLastAdded(.baby(profile: newBaby))` is called → header switches to the newly added baby.

The rule is: **the last added device/baby becomes the active selection.** This mirrors the user's intent — they just set it up, so they want to see its data.

```swift
// Called by the setup store at the end of the setup completion step:
// In BPMScaleSetupStore.onSetupComplete():
productTypeStore.selectLastAdded(.myBloodPressure)

// In BabyScaleSetupStore.onSetupComplete(baby: Baby):
productTypeStore.selectLastAdded(.baby(profile: BabyProfile(id: baby.id, name: baby.name)))
```

---

## 14. Edge Cases


| Scenario                                                            | Handling                                                                                                                                                                  |
| ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **User has only "My Weight"**                                       | Selector button is hidden (count ≤ 1). Existing UX unchanged.                                                                                                             |
| **User removes a baby scale**                                       | `babyProfileService.babyProfilesPublisher` emits. If that baby was selected, fall back to first available item. Notify user via toast.                                    |
| **Baby renamed**                                                    | `babyProfilesPublisher` emits, `rebuildAvailableItems()` runs, dropdown immediately shows new name.                                                                       |
| **Two babies with the same name**                                   | `BabyProfile.id` uniquely identifies them. Dropdown shows "Tammy Thompson" twice — disambiguate with a last-initial or birth-date suffix (design decision).               |
| **Baby entry in History when that baby is not in the dropdown**     | Cannot occur by design — you only see the entry if you selected that baby in the dropdown. As a safety net, `historyStore.loadMonthsInternal` filters by `babyProfileId`. |
| **User adds a BPM device while on Dashboard**                       | `scalesPublisher` emits, `rebuildAvailableItems` adds "My Blood Pressure", dropdown updates live.                                                                         |
| **Scale entry saved via BLE while "My Blood Pressure" is selected** | `entrySaved` publisher fires. Dashboard/History should only reload relevant type. Toast can be generic ("Entry saved").                                                   |
| **AppSync body-comp scan**                                          | AppSync entries are `.myWeight` type. No change to AppSync flow.                                                                                                          |
| **Multi-account switching**                                         | `SessionServices` deregistered and re-registered on account switch. `ProductTypeStore` destroyed and recreated, starting fresh with the new account's devices and babies. |
| **Baby has no entries yet**                                         | `fetchMonths(for: .baby(profile:))` returns empty. `isEmptyState = true`. Show empty-state view labelled with the baby's name.                                            |
| **BPM data model not yet built**                                    | The selector can still show "My Blood Pressure" in the dropdown. Tapping it shows an empty state / "coming soon" stub rather than crashing.                               |
| **Weight unit for baby**                                            | Baby entries use the same `weightUnit` from `AccountService.activeAccount.weightSettings` unless the API specifies otherwise. Confirm with backend.                       |


---

## 14. Step-by-Step Implementation Order

### Phase 1 — Foundation (no visible UI changes)

1. **Extend `DeviceType`** — add `.babyScale` case, add `CaseIterable`.
2. **Create `Baby` SwiftData model** in `Domain/Models/DB/Baby.swift` — new table with `id`, `accountId`, `name`, `deviceId?`, `isSynced`.
3. **Create `BabyProfile` domain struct** in `Domain/Models/Domain/Product/BabyProfile.swift` (lightweight view over `Baby`).
4. **Create `ProductSelection` enum** in `Domain/Models/Domain/Product/ProductSelection.swift`.
5. **Add `babyId: String?` to the `Entry` SwiftData model** — FK to `Baby.id`, non-nil only for babyScale entries.
6. **Create `BabyServiceProtocol`** in `Domain/Services/BabyServiceProtocol.swift`.
7. **Implement `BabyService`** in `Data/Services/BabyService.swift` — CRUD over the `Baby` SwiftData table, publishes `babiesPublisher`.
8. **Create `ProductTypeStoreProtocol`** in `Domain/Services/ProductTypeStoreProtocol.swift`.
9. **Implement `ProductTypeStore`** in `Features/Common/Stores/ProductTypeStore.swift` — subscribes to both `scalesPublisher` (for device types) and `babiesPublisher` (for baby names).
10. **Register `BabyService` and `ProductTypeStore`** in `ServiceRegistry.registerSessionServices()`.
11. **Update baby scale setup flow** — after `Device` is saved with `deviceType = "babyScale"`, call `BabyService.saveBaby(name:deviceId:)` with the user-entered baby name.
12. **Verify `Device.deviceType`** is set to `"bpm"` for BPM devices during BPM device registration.
13. **Extend `EntryServiceProtocol`** with `fetchMonths(for: ProductSelection)` and `fetchEntries(for:selection:)`. Implement with `entry.babyId == baby.id` predicate for baby selections.

### Phase 2 — Dropdown Component

1. **Create `ProductTypeSelectorSheet`** and `ProductTypeSelectorRow` in `Features/Common/Views/Components/`.
2. **Create `ProductTypeHeaderButton`** in `Features/Common/Views/Components/`.
3. **Write unit tests** for `ProductTypeStore` — `rebuildAvailableItems`, `select`, `autoSelectBaby`, fallback on device/baby removal.

### Phase 3 — History Integration (highest value, easiest)

1. **Inject `ProductTypeStore` into `HistoryStore`** — subscribe to selection changes, re-fetch months.
2. **Pass `ProductSelection` filter to `EntryService` calls** in `HistoryStore`.
3. **Add `ProductTypeHeaderButton` + sheet to `HistoryListScreen`**.
4. **Update `EntryCard`** to render per-selection metrics (scale vs BPM vs baby weight).
5. **Implement `historyStore.viewEntry`** baby auto-select logic.

### Phase 4 — Dashboard Integration

1. **Inject `ProductTypeStore` into `DashboardStore`** — subscribe and reload on selection change.
2. **Add `ProductTypeHeaderButton` + sheet to `DashboardScreen`**.
3. **Wrap existing dashboard content** in `ScaleDashboardContent`; add `BPMDashboardContent` and `BabyDashboardContent` as stubs.

### Phase 5 — Manual Entry Integration

1. **Inject `ProductTypeStore` into `EntryStore`** — subscribe and reset form on selection change.
2. **Extend `ManualEntryForm`** with BPM fields (`systolic`, `diastolic`).
3. **Add `ProductTypeHeaderButton` + sheet to `ManualEntryScreen`**.
4. **Pass correct `deviceType` and `babyProfileId` to entry save call**.
5. **Implement baby auto-select** on BLE baby scale weight received.
6. **Implement `wasAutoSelectedBaby` tracking** and `onScreenDisappear` reset.

### Phase 6 — Polish & Testing

1. **Update unit tests** for `HistoryStore` and `EntryStore` covering per-baby filtering.
2. **Empty state handling** for each selection type on Dashboard and History.
3. **Accessibility** — `accessibilityLabel` on `ProductTypeHeaderButton` and rows.
4. **Row colour tokens** — replace hardcoded green/purple with proper `Theme` tokens (confirm with design).

---

## 15. Pseudocode Examples

### 15.1 Adding a baby scale + baby → name appears in dropdown

```swift
// ── Baby scale setup ────────────────────────────────────────────────────
// Step 1: hardware paired
// ScaleService saves: Device(id: "dev-1", deviceType: "babyScale")
// → scalesPublisher emits

// Step 2: user enters "Tammy Thompson" in the setup flow
// BabyService.saveBaby(name: "Tammy Thompson", accountId: "acc-1", deviceId: "dev-1")
// → Baby(id: "b1", name: "Tammy Thompson") saved to SwiftData
// → babiesPublisher emits

// ── ProductTypeStore.rebuild() ──────────────────────────────────────────
// devices = [Device(deviceType: "scale"), Device(deviceType: "babyScale")]
// babies  = [Baby(id: "b1", name: "Tammy Thompson")]

// hasBabyScaleDevice = true
// availableItems = [
//   .myWeight,
//   .baby(BabyProfile(id: "b1", name: "Tammy Thompson")),
// ]

// ── User adds another baby (no new device needed) ───────────────────────
// BabyService.saveBaby(name: "Katey", accountId: "acc-1", deviceId: nil)
// → babiesPublisher emits again
// availableItems = [
//   .myWeight,
//   .baby(BabyProfile(id: "b1", name: "Tammy Thompson")),
//   .baby(BabyProfile(id: "b3", name: "Katey")),
// ]
// Sheet renders exactly matching the Figma mockup.
```

### 15.2 HistoryStore filtering per baby

```swift
// User selects "Tammy Thompson" in the sheet
// productTypeStore.selectedItem = .baby(BabyProfile(id: "b1", name: "Tammy Thompson"))

// HistoryStore.loadMonthsInternal fires:
let months = try await entryService.fetchMonths(for: .baby(tammyProfile))

// EntryService builds SwiftData predicate:
// entry.deviceType == "babyScale" && entry.babyId == "b1" && entry.operationType != "delete"

// Result: only Tammy's entries appear in History.
// Katey's entries (babyId == "b3") are excluded.
```

### 15.3 Manual entry for a baby

```swift
// selectedItem = .baby(BabyProfile(id: "b1", name: "Tammy Thompson"))
// EntryStore.resetFormForSelection fires
// → form title: "Log for Tammy Thompson"
// → fields: weight only (in lb/kg), date, time
// → Body metrics hidden (not relevant for baby weight)

// On save:
Entry(
    deviceType: "babyScale",
    babyId: "b1",        // FK to Baby.id — links to Tammy's record
    operationType: "create",
    ...
)
```

### 15.4 Auto-select when viewing a baby entry from History

```swift
// User is on History with "My Weight" selected
// They somehow navigate to an entry with deviceType == "baby", babyProfileId == "b3"
// (e.g. from a push notification deep link)

historyStore.viewEntry(entry)
// entry.deviceType == "baby", entry.babyProfileId == "b3" → "Katey"

productTypeStore.autoSelectBaby(profileId: "b3")
// Searches availableItems for id == "baby_b3"
// Finds .baby(BabyProfile(id: "b3", name: "Katey"))
// Calls select() → all screens reload for Katey's data
```

### 15.5 Dropdown hidden for new users

```swift
// New user, no devices paired, no babies registered
// productTypeStore.availableItems = [.myWeight]  (fallback)

// ProductTypeHeaderButton:
if store.availableItems.count > 1 {
    // NOT rendered — header button hidden
}
// Clean UI, no dropdown shown for single-type users
```

---

## 17. Open Questions for the Team

1. `**Baby` SwiftData model fields:** Confirm whether `Baby` needs additional fields beyond `id`, `name`, `accountId`, `deviceId`. For example: date of birth (for growth chart), gender, birth weight?
2. `**babyId` on `Entry`:** `Entry.babyId` = `Baby.id`. Does the backend API already accept a `babyId` field on the entry payload, or does this need to be added server-side? Note: the device ID is NOT stored on the entry.
3. `**scalesPublisher` scope:** Confirm whether `ScaleService.scalesPublisher` today includes BPM and babyScale devices or only weight scales. If scale-only, a new `allDevicesPublisher` is needed.
4. **Row colours in dropdown:** The Figma shows "My Weight" in blue, "My Blood Pressure" in green, baby names in purple. Confirm the exact `Theme` tokens to use (or add new tokens if missing).
5. **Signup product type field:** Confirm which `Account` model field stores the initial product type choice from signup (e.g. `Account.initialProductType`). Does this field already exist on the backend or does it need to be added?
6. **Two babies with the same display name:** How should disambiguation be handled in the dropdown? (middle initial, birth month, or is uniqueness enforced during setup?)
7. **BPM device registration flow:** Does a BPM device go through the existing `ScaleSetup` flow or a new dedicated BPM pairing flow? The plan assumes a new `BPMDeviceSetupStore` is required.
8. **Baby scale alert: user-number slot:** The baby assignment alert fires when 2+ babies exist and no baby is pre-selected. Is there a way to skip the alert based on hardware (e.g. user-number slot 1 = first baby, slot 2 = second baby)? Confirm with firmware team.

---

*Document created: 2026-03-11*
*Document updated: 2026-03-11 — Added signup flow, last-added-device selection, baby BLE alert logic, settings behaviour; corrected babyId linkage (Baby.id not Device.id)*
*Author: Implementation Plan (Claude Code)*