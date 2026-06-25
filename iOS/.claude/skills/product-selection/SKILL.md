---
name: product-selection
description: Work with Phase 2 product selection on iOS — the productTypes array, measurementUnits, the active-product switcher, and the ProductType/ProductSelection/MeasurementUnits enums. Use when implementing the product selector, gating UI by product, reading/writing account.productTypes, handling measurement units, or onboarding a new product (weight/blood_pressure/baby). Mind the rawValue-vs-apiValue vocab gotcha. Pair with phase2-context.
---

# Product selection (iOS) — `productTypes` / `measurementUnits`

## Types (real files)

| Concept | Type | File |
|---------|------|------|
| Product enum | `enum ProductType { .weight="myWeight", .bloodPressure="myBloodPressure", .baby }` (has `.apiValue`) | `meApp/Domain/Models/Domain/Auth/AccountEnums.swift` |
| Units enum | `enum MeasurementUnits { .metric, .imperialLbOz, .imperialLbDecimal }` | `meApp/Domain/Models/Domain/Auth/AccountEnums.swift` |
| Active selection | `enum ProductSelection { .myWeight, .myBloodPressure, .baby(profile: BabyProfile) }` (exposes `deviceType`, `entryType`, `entriesCategory`) | `meApp/Domain/Models/Domain/Product/ProductSelection.swift` |
| Store | `ProductTypeStore` (`.shared`, `ObservableObject`) / `ProductTypeStoreProtocol` (`@MainActor`) | `meApp/Features/Common/Stores/ProductTypeStore.swift`, `meApp/Domain/Services/ProductTypeStoreProtocol.swift` |
| Account fields | `AccountSnapshot.productTypes: [String]`, `AccountSnapshot.measurementUnits: String?` | `meApp/Domain/Models/Domain/Auth/AccountSnapshot.swift` |
| Units DTO | `UpdateMeasurementUnitsRequest { measurementUnits: String }` | `meApp/Domain/Models/API/Auth/UpdateMeasurementUnitsRequest.swift` |
| UI | `ProductTypeStrings`, `ProductTypeHeaderButton`, `ProductTypeSelectorSheet` | `meApp/Features/Common/...` |

## ⚠️ rawValue vs apiValue (the #1 gotcha)

`ProductType.rawValue` is the **persisted/internal** vocab (`"myWeight"`, `"myBloodPressure"`), but the **wire** value is `ProductType.apiValue` (`"weight"`, `"blood_pressure"`, `"baby"`).

- Sending to the server → use `.apiValue`.
- `ProductTypeStore.normalizeProductTypes` maps server `"weight"`→`"myWeight"` and `"blood_pressure"`/`"bpm"`→`"myBloodPressure"` on the way in.
- Never compare a server string directly to `rawValue`; normalize first or compare against `.apiValue`.

## Store behavior

`ProductTypeStore.shared` manages `selectedItem: ProductSelection` + `availableItems: [ProductSelection]`, persists the per-account selection via `KvStorageService`, and reconciles `account.productTypes` from paired devices / babies. Read it from feature stores to gate product-specific UI; don't re-derive product state ad hoc.

## Updating products / units

- Products: `AccountService.updateProductTypes(_:)` → `PATCH /account/products` (send `.apiValue` strings). Also auto-added when pairing a device (`paired-device`) or creating a baby (`baby-profile`).
- Units: send `UpdateMeasurementUnitsRequest` → `PATCH /account/measurement-units`.
- `gender`/`dob`/`height` are conditionally required at signup based on selected products (see `phase2-context`).

## Checklist
- Gating UI by product → read `ProductTypeStore.shared.selectedItem`, not raw account strings.
- Any server round-trip → `.apiValue` out, `normalizeProductTypes` in.
- New product surface → extend `ProductType` + `ProductSelection` together; cover with `meAppTests`.
