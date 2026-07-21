# SDK BLE Core & Concurrency — the threading contract

Rules for the `Core/` layer (CoreBluetooth / `android.bluetooth` wrappers) and the concurrency boundary every BLE call crosses. CoreBluetooth and Android GATT each serialize internally, but mixing their callbacks with the host app's threading without a deliberate boundary produces timing-dependent bugs that pass on a laptop and fail on hardware — the single hardest class of BLE defect to reproduce. ARCHITECTURE.md §"Concurrency / Threading Model" and the SDK's anti-pattern list lock the contract: a dedicated per-peripheral serial queue/dispatcher, public callbacks marshaled to main, BLE calls only on the BLE thread, no reentrancy, teardown on disconnect. Severity uses the orchestrator's taxonomy; this file prescribes its own severities — do not re-classify.

Anchors are Sage/`GGBLEDevice`-specific but the model is standard CoreBluetooth/GATT practice. **If a repo `CLAUDE.md`/`README` documents a different threading model, prefer it and skip the conflicting rule.** These rules fire on `Core/`, `Devices/`, and the handler impl — not on pure `Models/`/`Utils/` value code.

---

## P1 — `CBCentralManager(queue: nil)` / no dedicated BLE dispatcher

SDK anti-pattern, verbatim: "`CBCentralManager(queue: nil)` is forbidden — always a dedicated serial queue / dispatcher." Passing `nil` routes every CoreBluetooth callback onto the main thread, entangling BLE serialization with UI work; the Android equivalent is running GATT operations straight off the binder callback thread instead of hopping to a per-device dispatcher.

```swift
// Core/BluetoothCentralManager.swift — BEFORE: callbacks land on main
central = CBCentralManager(delegate: self, queue: nil)   // forbidden
```
```kotlin
// core/GattClient.kt — BEFORE: work stays on the binder thread
override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
    decodeAndDeliver(ch.value)   // heavy work on Android's binder thread
}
```

**Sniff.** `CBCentralManager(` with `queue: nil` (or no `queue:` argument) on `+` lines; a `CBCentralManager`/`CBPeripheral` created without an associated `DispatchQueue(label:)`. Android: a `BluetoothGattCallback` override whose body does decode/dispatch work without an explicit hop onto a per-device dispatcher (`Dispatchers.IO.limitedParallelism(1)` / a dedicated `HandlerThread`).

**Fix.** Construct with a dedicated serial queue: `CBCentralManager(delegate: self, queue: DispatchQueue(label: "sage.ble.central"))`, and a per-peripheral `DispatchQueue(label: "sage.ble.<peripheralId>")`. On Android, immediately post the binder-thread callback onto the per-device dispatcher before doing any work.

---

## P1 — BLE read/write issued off the BLE thread (or re-entered from a callback)

ARCHITECTURE.md Anti-Pattern 5 + the cross-platform invariant "no reentrancy from delegate callbacks back into device commands on the same thread without explicit re-dispatch." A `peripheral.writeValue(...)` called directly from a UI-thread delegate callback — or synchronously from *inside* another BLE callback — mixes CoreBluetooth's per-instance serialization with foreign threading and deadlocks flakily.

```swift
// Devices/Sage/GGBLEKettleSage.swift — BEFORE: write straight from a callback
func onConnected() {                       // fires on the BLE queue's caller context
    peripheral.writeValue(frame, for: writeChar, type: .withResponse)  // no re-dispatch
}
```

**Sniff.** A `peripheral.writeValue`/`readValue`/`setNotifyValue` (iOS) or `gatt.writeCharacteristic`/`readCharacteristic` (Android) call on `+` lines that is *not* inside a `queue.async { … }` / dispatcher hop — especially inside a delegate/callback method or a public-facing method that may be called on main.

**Fix.** Route every BLE operation through the per-peripheral serial queue: `bleQueue.async { self.peripheral.writeValue(...) }`, and never call a device command synchronously from within a delegate callback — re-dispatch. The SDK's `GGBLEDevice` command-queue API exists precisely so devices never call `peripheral.write` directly; use it.

**Do NOT flag** a BLE call that already sits inside the peripheral's serial queue / dispatcher, or one made from the command-queue drain that runs on that queue.

---

## P1 — Public callback not marshaled to the main thread / main looper

ARCHITECTURE.md: "Public callbacks marshal to main … consumers can update UI directly from delegate callbacks without thread checks." A `GGScanDelegate`/`GGDataDelegate` (iOS) or `GGScanCallback`/`GGDataCallback` (Android) invoked from the BLE queue/binder thread forces every integrator to add their own thread hop, and most won't — leading to UIKit/Android main-thread violations in the consuming app.

```swift
// External/GGIBluetoothHandlerImpl.swift — BEFORE: delegate fired on the BLE queue
func handleStatus(_ status: KettleStatus) {
    dataDelegate?.onKettleStatusUpdate(status)   // still on sage.ble.<id> queue
}
```

**Sniff.** A `scanDelegate`/`dataDelegate`/`callback` method invocation on `+` lines that is inside a BLE-queue/callback context and not wrapped in `DispatchQueue.main.async` (iOS) / a post to the host `Handler(Looper.getMainLooper())` (Android).

**Fix.** Hop to main before invoking the consumer: `DispatchQueue.main.async { self.dataDelegate?.onKettleStatusUpdate(status) }`; on Android, post to the main `Handler` the handler was constructed with. Decode on the BLE thread, deliver on main.

---

## P1 — Disconnect doesn't tear down the per-peripheral queue/dispatcher

Cross-platform invariant: "Disconnect tears down the per-peripheral queue/dispatcher. No leaked threads." A connect path that spins up a per-device dispatcher but a disconnect path that doesn't stop it leaks a thread per connect/disconnect cycle — invisible in a quick test, fatal over a long-running app session that reconnects repeatedly.

**Sniff.** A disconnect/cleanup method (`didDisconnectPeripheral`, `disconnect`, `close`, `onConnectionStateChange → DISCONNECTED`) on `+` lines that nils the peripheral/gatt but doesn't cancel/shutdown the associated dispatcher, `HandlerThread`, or coroutine scope created at connect time.

**Fix.** In the disconnect handler, tear down what connect created: cancel the coroutine scope / `quitSafely()` the `HandlerThread` (Android), release the serial queue reference and any retained timers (iOS). Pair every connect-time resource with a disconnect-time teardown.

---

## P1 — `Devices/` touches concrete `CBPeripheral`/`BluetoothGatt` instead of the seam

ARCHITECTURE.md testability seam: "`GGBLEDevice` constructor takes a `BluetoothPeripheralProtocol` (not a concrete `CBPeripheral`/`BluetoothGatt`)" so device decode/encode is unit-testable without any BLE stack (the SDK's iOS coverage floor is only 50% *because* transport isn't testable — the seam is what keeps devices at all testable). A device importing CoreBluetooth and holding a concrete `CBPeripheral` breaks both the layering and every device unit test's mockability.

```swift
// Devices/Sage/GGBLEKettleSage.swift — BEFORE: concrete transport in a device
import CoreBluetooth
final class GGBLEKettleSage: GGBLEDevice {
    let peripheral: CBPeripheral            // should be BluetoothPeripheralProtocol
}
```

**Sniff.** `import CoreBluetooth` / `import android.bluetooth` under `Devices/`, or a device property/parameter typed `CBPeripheral` / `BluetoothGatt` / `CBCharacteristic` rather than the project's `BluetoothPeripheralProtocol`-style seam.

**Fix.** Depend on the protocol seam: `init(peripheral: BluetoothPeripheralProtocol, …)`. Keep `import CoreBluetooth`/`android.bluetooth` confined to `Core/`. This is what lets `Tests/Devices/` feed byte-array fixtures through a `CoreBluetoothMock`-backed fake.

---

## P0 — Force-unwrap / force-cast / force-try in SDK code

A crash in a shipped SDK takes down the consuming app, and this library runs against untrusted device input (malformed BLE frames, unexpected disconnects) where "this is always non-nil" assumptions fail on real hardware. `!` / `as!` / `try!` (Swift) and `!!` / unchecked cast (Kotlin) are blockers here even where they'd be a lower severity in app code.

```swift
let temp = decode(frame)!                     // frame from the wire — can be malformed
let kettle = device as! GGBLEKettleSage        // wrong device category → crash
let value = try! JSONDecoder().decode(...)     // any decode failure crashes the SDK
```

**Sniff.** `!` force-unwrap, `as!` force-cast, or `try!` on `+` lines in SDK Swift; `!!` or an unchecked `as` cast in SDK Kotlin — especially in decode paths, callback handlers, and anything consuming `Data`/`ByteArray` off the wire.

**Fix.** Use `guard let` / `if let` / `as?` with a typed `GGSDKError` on the failure path, or Kotlin `?:` / `runCatching`. A malformed frame should surface a typed error to the delegate, never crash the host app.

**Do NOT flag** `!` used as the boolean-NOT operator, `!=`, or IUO-typed IBOutlets (there are none in a headless SDK). This targets force-unwrap/-cast/-try of optionals and results.
