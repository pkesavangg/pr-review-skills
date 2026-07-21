# SDK Capability Protocols — semantic, stateless, right-sized

The SDK uses an **inheritance hierarchy + opt-in capability protocols** pattern: a concrete device (`GGBLEKettleSage`) extends the abstract `GGBLEDevice` and *adopts* capability protocols (`GGBLEHeatable`, `GGBLEWeighable`, `GGBLEProgrammable`) to declare what it can do. Consumers cast a connected device to a capability to drive it (`if let heatable = device as? GGBLEHeatable { … }`). `docs/CAPABILITY-CONTRACTS.md` calls these "the public contract for the consuming app team — anything wrong here becomes a MAJOR version bump later." The whole pattern only pays off if capabilities stay **thin, semantic, and stateless**; the moment one leaks transport detail or carries state, it stops being a reusable contract and becomes device-coupled plumbing. Severity uses the orchestrator's taxonomy; this file prescribes its own severities — do not re-classify.

Anchors are Sage-specific (`GGBLEHeatable`, `GGBLEDevice`, `Temperature`); the pattern generalizes to any GG BLE SDK. **If a repo `CLAUDE.md`/`README` documents a different capability convention, prefer it and skip the conflicting rule.** (Note: `GGBLEBrewable` was removed from the Sage SDK — a capability being deleted is itself a MAJOR-bump doc-sync event; see [`docs-and-confluence-sync.md`](docs-and-confluence-sync.md).)

---

## P1 — Capability protocol exposes a transport type

SDK anti-pattern, verbatim: "Capability protocols never expose `Data`, `CBCharacteristic`, `BluetoothGattCharacteristic`, byte payloads, opcodes, or characteristic UUIDs — capability surfaces are semantic only." A capability describes *what a device can do* ("it heats"), not *how the bytes move*. Leaking a `Data` payload or an opcode into the protocol forces every consumer to understand the wire format and turns a wire-protocol tweak into a public MAJOR break.

```swift
// Capabilities/GGBLEHeatable.swift — BEFORE: transport bleeds into the contract
public protocol GGBLEHeatable: AnyObject {
    func writeSetTargetCommand(_ payload: Data) throws          // raw bytes
    func handleNotification(_ characteristic: CBCharacteristic) // transport type
    var setTargetOpcode: UInt8 { get }                          // wire opcode
}
```

**Sniff.** In files under `Capabilities/` / `capabilities/`: a protocol/interface member typed `Data` / `[UInt8]` / `ByteArray` / `byte[]` / `CBCharacteristic` / `BluetoothGattCharacteristic` / `CBUUID`, or a member named like an opcode/characteristic (`opcode`, `commandByte`, `characteristicUUID`, `write…Command`, `handleNotification`).

**Fix.** Keep the surface semantic: `func setTargetTemperature(_ value: Temperature) throws` / `var thermalState: ThermalState { get }`. The byte encoding/decoding lives *inside the concrete device* (`GGBLEKettleSage`), never on the capability. The capability returns domain models (`Temperature`, `KettleStatus`), not payloads.

---

## P1 — Capability protocol carries state

ARCHITECTURE.md Anti-Pattern 3: capabilities are **stateless behavioral contracts**; state (connection state, last-known snapshot, queued commands) lives on the abstract `GGBLEDevice`. A `var … { get set }` on a capability forces every adopter to reimplement state management for every capability it adopts, and it's especially painful on the Kotlin side where interfaces can't hold backing fields.

```swift
// Capabilities/GGBLEHeatable.swift — BEFORE: state on the contract
public protocol GGBLEHeatable: AnyObject {
    var connectionState: GGConnectionState { get set }   // belongs on GGBLEDevice
    var retryCount: Int { get set }                      // per-device state, not a capability
    func setTargetTemperature(_ value: Temperature) throws
}
```

**Sniff.** In `Capabilities/`: a `{ get set }` property, or a mutable stored-style property (Kotlin `var` in an `interface`), particularly one named for connection/session/retry/queue state.

**Fix.** Move the state to `GGBLEDevice` (or into the model the capability returns — e.g. `KettleStatus.thermalState`). Keep the capability to read-only computed properties (`{ get }`) and behavior methods. Cross-device state never lives on a capability.

---

## P2 — Fat base class: product method on `GGBLEDevice` returning "not supported"

ARCHITECTURE.md Anti-Pattern 1 — the exact thing the capability design replaces. Putting `setTemperature`/`tare`/`runProgram` on the abstract `GGBLEDevice` and returning `GG_NOT_SUPPORTED` for devices that don't implement them means every new device grows the base class, concrete classes accumulate dead overrides, and consumers can't tell from the type what a device actually does.

```swift
// Devices/GGBLEDevice.swift — BEFORE: product behavior on the base class
class GGBLEDevice {
    func setTargetTemperature(_ t: Temperature) throws { throw GGSDKError.notSupported } // scale can't heat
    func tare() throws { throw GGSDKError.notSupported }                                  // kettle can't weigh
    func runProgram(_ p: ProgramDescriptor) throws { throw GGSDKError.notSupported }
}
```

**Sniff.** New product-specific methods (`setTargetTemperature`, `tare`, `runProgram`, `startBrew`) added directly to `GGBLEDevice` / the abstract base, especially with a body that throws/returns a `notSupported`/`GG_NOT_SUPPORTED` sentinel.

**Fix.** Put the method on the capability protocol and adopt it only on devices that have the behavior: `final class GGBLEKettleSage: GGBLEDevice, GGBLEHeatable`. `GG_NOT_SUPPORTED` stays an internal fallback for legacy interface points, not a design pattern. Consumers discover capability via `as? GGBLEHeatable`, not by calling and catching "not supported."

---

## P2 — Over-atomized capability (one protocol per method)

ARCHITECTURE.md Anti-Pattern 6. Splitting a cohesive capability into `GGBLECanReadTemperature` + `GGBLECanSetTemperature` + `GGBLECanCancelHeating` forces consumers to check three protocols to use one behavior. The cohesive concept is "this device heats" → one `GGBLEHeatable`.

**Sniff.** Multiple new capability protocols on `+` lines that each declare a single method and share an obvious cohesive theme (all about temperature, all about weighing), or protocol names shaped `GGBLECan<Verb>`.

**Fix.** Group by cohesive capability — `GGBLEHeatable` bundles read-temp + set-temp + cancel + `thermalState`. If a device supports only *part* of a capability's surface, that's a sign the capability is too broad; re-cut it, but that's rare. Don't atomize to one-method protocols.

---

## P2 — Concrete device class exposed in the public API

ARCHITECTURE.md Anti-Pattern 4. A handler method like `connectKettle() -> GGBLEKettleSage` couples the public API to the device class hierarchy: every new device adds a method, and every device rename cascades out to consumers. The connect flow must stay flat — `connectDevice(deviceInfo, dataDelegate)` — with the typed device handle arriving on the data delegate, cast to capabilities.

**Sniff.** A `public`/`open` method or a public type under `External/` whose name or return type references a concrete device class (`GGBLEKettleSage`, `connectKettle`, `getKettle`), or a public delegate callback typed as a concrete device rather than `GGBLEDevice` + capabilities.

**Fix.** Return nothing device-specific from the handler; deliver the connected device on `GGDataDelegate.onConnected(device)` typed as `GGBLEDevice`, and let the consumer `device as? GGBLEHeatable`. The public API stays flat as the device family grows. (This also overlaps [`public-api-contract.md`](public-api-contract.md) — post one finding, not two.)

---

## Nit — Capability method naming diverges from the domain verb

Capability methods read like a spec (`setTargetTemperature`, `cancelHeating`, `tare`, `zero`, `runProgram`). A method named for the wire action (`sendTempFrame`, `writeTareCmd`) or for the transport (`notifyStatus`) reads as plumbing even when the type is semantic.

**Sniff.** Capability method names containing `frame`/`cmd`/`command`/`write`/`notify`/`packet` on `+` lines in `Capabilities/`.

**Fix.** Rename to the domain verb the consumer thinks in (`startBrew`, not `sendBrewCommand`). The wire vocabulary stays inside the device implementation.
