# SDK Public API Contract — the frozen `External/` surface

Rules for the **public, published surface** of a GreaterGoods BLE SDK — the `External/` (iOS) / `external/` (Android) layer that the consuming app integrates against over private SPM / private Maven. This surface is a **SemVer contract**: per the SDK's `docs/PUBLIC-API.md`, "changes to a signature, name, or a removed case are MAJOR-version bumps." Consumers only ever integrate against tagged releases, so a careless rename or a leaked transport type ripples out to every integrator and can't be walked back without a major bump. Severity uses the orchestrator's taxonomy (`P0` / `P1` / `P2` / `Nit`); this file prescribes its own severities — do not re-classify.

Concrete anchors below come from the Sage SDK (`GGIStub`, `GGIBluetoothHandler`, `GGScanResult`, `GGDeviceInfo`, `Temperature`, `GGSDKError`); a future GG BLE SDK has the same shape. **If a repo `CLAUDE.md`/`README` documents a different public-API convention, prefer it and skip the conflicting rule.**

What counts as "public" (the surface these rules police):
- Anything declared `public`/`open` under `ios/**/External/**` or Kotlin `external/**` that isn't `internal`/`private`.
- The entry point (`GGIStub`), the handler interface (`GGIBluetoothHandler`), scan/data delegate protocols, public enums, and public value types (`GGScanResult`, `GGDeviceInfo`, `ScanFilter`, `GGDeviceId`, `BluetoothAvailability`, …).

Everything below `External/` (Core, Devices, Capabilities internals, Enums, Utils) is internal and free to evolve — these rules do **not** apply there.

---

## P1 — Breaking change to a frozen public symbol without a MAJOR bump

Removing or renaming a public type/method/property, changing a method signature (params, return type, `throws`/`async`), or deleting an enum case is a source-breaking change for every consumer. The SDK's contract is that these require a MAJOR version bump plus a `docs/PUBLIC-API.md` update — silently landing one on a MINOR/PATCH branch breaks integrators at their next tag pull.

```swift
// External/GGIBluetoothHandler.swift — BEFORE (shipped in v1.x)
func connectDevice(_ deviceInfo: GGDeviceInfo, dataDelegate: GGDataDelegate) async throws

// this PR (still tagged as a MINOR) — renamed param + dropped `async`: source-breaking
func connectDevice(_ device: GGDeviceInfo, delegate: GGDataDelegate) throws
```

**Sniff.** On `-`/modified lines under `External/` / `external/`: a `public`/`open` symbol removed or renamed, a signature changed (param label/type/order, return type, `throws`, `async`, `@escaping`), or an `enum` case deleted. Cross-check the change against `docs/api-snapshots/` — if the previous snapshot lists the old symbol and the version label in `Package.swift` / `build.gradle` / `CHANGELOG.md` isn't a MAJOR bump, flag.

**Fix.** If the change is genuinely needed, bump MAJOR and record it in `CHANGELOG.md` + `docs/PUBLIC-API.md`. If not, keep the old symbol and add the new one alongside (additive) — e.g. keep the `async throws` overload and add the new one, as the SDK already does with its "legacy Phase-1 surface preserved for source compat" overloads. Deprecate (`@available(*, deprecated)` / `@Deprecated`) rather than delete.

**Do NOT flag** additive changes — a *new* public method, a new enum case (see the forward-compat rule below), or a new optional-with-default parameter. Those are MINOR-compatible and are the normal way this surface grows.

---

## P1 — Bare numeric primitive for a domain quantity in the public API

The SDK's anti-pattern list is explicit: "Public API never accepts/returns bare `Double`/`Float`/`Int` for temperature or weight — always wrapped in `Temperature` / `WeightReading` domain types." A raw `Double` loses the unit (°C vs °F, g vs oz), invites the classic unit-confusion bug at the integration boundary, and can't evolve without a breaking change.

```swift
// Capabilities/GGBLEHeatable.swift — BEFORE: unit-ambiguous public surface
public protocol GGBLEHeatable: AnyObject {
    func setTargetTemperature(_ celsius: Double) throws   // °C? °F? caller can't tell
    var currentTemperature: Double? { get }
}
```

**Sniff.** Public method params / return types / properties under `External/` (and public capability protocols) typed as bare `Double`/`Float`/`Int`/`Long` whose name or context implies temperature, weight, mass, duration, or another dimensioned quantity (`temp`, `celsius`, `weight`, `grams`, `holdMin`, `duration`).

**Fix.** Wrap in the domain type: `func setTargetTemperature(_ value: Temperature) throws` / `var currentWeight: WeightReading?`. Use `TimeInterval` (iOS) / a typed duration (Android) for times. If no domain type exists yet for a genuinely new quantity, add one in `Models/` rather than exposing a primitive.

**Do NOT flag** a bare primitive that is genuinely unitless and self-describing — `rssi: Int` (dBm is the only unit BLE reports), a `count`, an index, or a `percent: Double` documented as `0.0...1.0`.

---

## P1 — Transport type leaks through the public API

The public surface must be platform-type-free (SDK anti-pattern: "Public API never imports CoreBluetooth or `android.bluetooth.*`") and must never hand a consumer raw bytes (PITFALL #11 — `GGScanResult.advertisingData` is `[String: String]`, never `Data`/`byte[]`). Leaking `CBPeripheral`, `BluetoothGattCharacteristic`, `Data`, `CBUUID`, or a byte array couples integrators to CoreBluetooth/Android-BLE and to the wire format, defeating the whole point of the facade.

```swift
// External/GGScanResult.swift — BEFORE: transport type in a public type
public struct GGScanResult {
    public let peripheral: CBPeripheral          // leaks CoreBluetooth into the app
    public let advertisingData: Data             // raw bytes — consumer must parse the wire format
}
```

**Sniff.** In files under `External/` / `external/`: `import CoreBluetooth` / `import android.bluetooth` (or fully-qualified `android.bluetooth.*` uses), or a public member typed `Data` / `[UInt8]` / `byte[]` / `ByteArray` / `CBUUID` / `CBPeripheral` / `CBCharacteristic` / `BluetoothGatt*` / `BluetoothDevice`.

**Fix.** Expose an OS-decoded, semantic shape instead: `advertisingData: [String: String]`, a `GGDeviceId` wrapper instead of `CBPeripheral`, a `String` UUID instead of `CBUUID`. Do the decode inside `Core/` and hand the public layer only domain types. Keep `import CoreBluetooth`/`android.bluetooth` confined to `Core/`.

---

## P2 — New public symbol not reflected in `docs/PUBLIC-API.md` / api-snapshot

`docs/PUBLIC-API.md` is described as "the canonical, side-by-side reference … every public type, method, and property listed here is part of the v1.0.0 contract," and `docs/api-snapshots/` captures the frozen surface per release. A public symbol added without updating those makes the doc lie about the contract. (The broader doc/Confluence sync requirement lives in [`docs-and-confluence-sync.md`](docs-and-confluence-sync.md); this rule is the narrow "the API reference itself drifted" case.)

**Sniff.** A new `public`/`open` symbol on `+` lines under `External/` while `docs/PUBLIC-API.md` and `docs/api-snapshots/` are absent from the PR's changed-file list.

**Fix.** Add the new symbol to the matching table in `docs/PUBLIC-API.md` (both iOS and Android columns) and regenerate the api-snapshot in the same PR, so the reference and the code ship together.

---

## P2 — New enum case without forward-compat handling

Public enums (`GGDeviceCategory`, `GGSDKError`, `BluetoothAvailability`) grow by MINOR releases; `docs/PUBLIC-API.md` notes "new cases added in MINOR releases; consumers must add `default` arms." Adding a case is fine, but the SDK's own internal exhaustive switches over that enum must handle it, and the intent should be recorded so consumers aren't surprised.

**Sniff.** A new `case` added to a public `enum` on `+` lines, combined with an internal `switch` over that enum elsewhere in the diff that has no `default`/`@unknown default` arm.

**Fix.** Add the case, add `@unknown default` (Swift) handling at internal switch sites, and note the addition in `CHANGELOG.md` under a MINOR entry so integrators know to extend their `default` arms.

---

## Nit — Public value-type conformance parity across platforms

The SDK's public value types carry a deliberate conformance set (`Sendable, Equatable, Hashable` on iOS; the Android twin mirrors it). A new public struct/data class that drops `Sendable`/`Equatable`/`Hashable` where its siblings have them creates a subtle asymmetry (e.g. it can't be used as a dictionary key or crossed between actors the way the others can).

**Sniff.** A new public `struct`/`data class` under `External/` whose declared conformances are a strict subset of the neighboring public types' (`GGDeviceInfo`, `GGScanResult` are `Sendable, Equatable, Hashable`).

**Fix.** Add the missing conformances, or add a one-line comment explaining why this type intentionally differs (e.g. it holds a closure and can't be `Equatable`).
