# SDK Wire Protocol — fidelity to the BLE spec

Rules for the byte-level protocol code: characteristic/service UUIDs, command opcodes, payload layouts, and the decode/encode in `Devices/` + `Enums/`. The SDK's `CLAUDE.md` names the source of truth explicitly: **`docs/Sage_Kettle_BLE_protocol_spec_v1.md`** (mirrored from **Confluence page `1489993739`**) — "the byte-for-byte spec the ESP32 firmware ships against; SDK + simulator must match it. Any wire-layout drift here is a bug." The firmware, the SDK, and the KMP simulator all encode against the same spec, cross-checked byte-identically via `docs/protocol-fixtures.json`. A constant that silently diverges from the spec produces a device that appears to work in code review and fails on real hardware. Severity uses the orchestrator's taxonomy; this file prescribes its own severities — do not re-classify.

**Read the spec doc before flagging.** When a PR touches UUIDs/opcodes/layouts, open `docs/Sage_Kettle_BLE_protocol_spec_v1.md` in the branch and compare the changed constant against the relevant section — judge against the spec, not from memory. These anchors are Sage-kettle-specific; a future device has its own spec. **If a repo `CLAUDE.md`/`README` points at a different spec, prefer it.**

---

## P1 — Characteristic / service UUID or opcode diverges from the spec

The spec (§1.3, §7, §8) pins exact UUIDs: after the FW-23 hard cutover both custom services use 16-bit forms — **Inno Sage Kettle Control v2 = `0xFFA0`** (chars `0xFFA1`–`0xFFA7`), **Wi-Fi Provisioning v1 = `0xFFB0`** (chars `0xFFB1`–`0xFFB4`), with 128-bit forms deprecated and the **legacy Kettle Service v1 (`A7E81F01…`) removed — "do not use."** A UUID/opcode constant that doesn't match the spec, or that resurrects a deprecated/legacy identifier, talks to the wrong characteristic.

```swift
// Utils/GGUUIDManager.swift — BEFORE: wrong / legacy identifiers
static let kettleService   = CBUUID(string: "A7E81F01-7C8B-4B6A-9D5E-2F4E6B0F0001") // legacy v1 — removed from spec
static let notifyData      = CBUUID(string: "A5B40006-…")                            // pre-FW-23 128-bit form
static let setKettleData   = 0x04                                                    // opcode not in the spec table
```

**Sniff.** On `+` lines in `Enums/*Command*`, `Utils/GGUUIDManager*`, or `Devices/` decode/encode: a service/characteristic UUID literal or an opcode constant. Compare each against `docs/Sage_Kettle_BLE_protocol_spec_v1.md` §1.3/§7/§8. Flag a value not present in the spec, a resurrected 128-bit form marked deprecated, or any reference to the legacy `A7E81F01` service.

**Fix.** Use the spec's canonical current value (16-bit `0xFFA0`-family / `0xFFB0`-family). If the *spec* changed, that's a doc-sync event — update `docs/Sage_Kettle_BLE_protocol_spec_v1.md` + `CHANGELOG.md` + Confluence `1489993739` in the same PR (see [`docs-and-confluence-sync.md`](docs-and-confluence-sync.md)) and regenerate `protocol-fixtures.json`. If the code is wrong, correct the constant to match the spec.

---

## P1 — Temperature not encoded as `int16 LE`, 0.1 °C units

The kettle protocol carries temperatures as **`int16` little-endian in 0.1 °C units** (spec §7 / `CLAUDE.md`). Encoding as a single byte, as big-endian, in whole degrees, or in Fahrenheit sends the firmware a temperature it will misread — a safety-adjacent bug on a heating appliance.

```swift
// Devices/Sage/GGBLEKettleSage.swift — BEFORE: wrong wire encoding
frame[4] = UInt8(target.celsius)                          // whole-degree single byte — loses 0.1° + overflows
frame.append(contentsOf: withUnsafeBytes(of: raw.bigEndian) { … })  // big-endian — bytes swapped
```

**Sniff.** Temperature encode/decode on `+` lines that writes/reads a single byte for a temperature, uses `.bigEndian`, multiplies/divides by something other than 10 (the 0.1 °C scale), or reads `Fahrenheit` for a wire value. Look near Set Kettle Data (`0xFFA5`-family) and Notify Data.

**Fix.** Encode as `Int16(round(temp.celsius * 10)).littleEndian` split into two bytes; decode as `Int16(littleEndian:) / 10.0` back into a `Temperature`. Round-trip a spec fixture from `protocol-fixtures.json` in a unit test.

---

## P1 — Error byte not categorized by tens-digit into `KettleErrorReason`

`CLAUDE.md` (spec §10 / MOB-944): the wire `errorCode` byte is a **granular sub-code** encoded as the decimal `E`-number (`E11`→`0x0B`, `E32`→`0x20`); the SDK keeps the raw byte for logging and **buckets it into a category by tens digit (`raw / 10`)** — `sensor` (E1x), `heater` (E2x), `liftBase` (E3x), `bleStack` (E4x), `system` (E5x), `invalidCommand` (`0xFE`), `unknown` (`0xFF` + anything unrecognized). The old flat scheme (`OVER_TEMPERATURE 0x01`, `SENSOR_FAULT 0x02`, `INVALID_COMMAND 0x05`, …) is **gone**; `INVALID_COMMAND` is now `0xFE`.

```swift
// Devices/Sage/GGBLEKettleSage.swift — BEFORE: dead flat scheme
switch errorByte {
case 0x01: reason = .overTemperature   // removed from the spec
case 0x02: reason = .sensorFault
case 0x05: reason = .invalidCommand    // INVALID_COMMAND is 0xFE now, not 0x05
}
```

**Sniff.** New error-decode `switch`/`when` on `+` lines that matches raw error bytes directly to public reasons (especially the removed `0x01`/`0x02`/`0x04`/`0x05` literals), or that maps `0x05` to invalid-command. Any error mapping that doesn't compute a category via `raw / 10`.

**Fix.** Keep the raw byte for logging; derive the public `KettleErrorReason` by tens digit (`raw / 10`), mapping `0xFE`→`invalidCommand` and `0xFF`/unrecognized (incl. cloud-only E6x)→`unknown`. The mapping lives in `mapError` (iOS) / `toPublic` (Android) — extend that, don't inline a new scheme.

---

## P1 — Assuming an optional characteristic instead of feature-detecting

`CLAUDE.md` (spec §12.2): optional characteristics — the preset chars `0xFFA6`/`0xFFA7` — are gated by **characteristic discovery**, not service version. The SDK exposes them only behind `KettleFeature.presets` + `GGBLEKettleSage.supports(.presets)`, and preset methods throw `KettleFeatureUnsupported` on a device whose discovered char set lacks them. Assuming presence from the service/firmware version reads/writes a characteristic that may not exist on that unit and fails at runtime.

```swift
// BEFORE: presence inferred from version, not discovery
if firmwareVersion >= "FW-23" {
    try writePreset(slot, preset)   // this unit may still not expose 0xFFA6
}
```

**Sniff.** On `+` lines: a read/write to an optional characteristic (`0xFFA6`/`0xFFA7` / preset ops) guarded by a firmware/service-version comparison, or not guarded by `supports(.presets)` / a `KettleFeature` check at all.

**Fix.** Gate on discovered characteristics: `guard supports(.presets) else { throw GGSDKError … }` and surface `KettleFeatureUnsupported` when absent. Feature-detect from the discovered char set, never from a version string.

---

## P1 — Re-introducing pairing / bonding / encryption / ownership

`CLAUDE.md` (spec §6): the access model is **open** — "No pairing, no bonding, no ownership claim, no encryption. Writes are validated for size + field ranges only. Safety interlocks live entirely inside the firmware." The abandoned ownership scheme (`owner_ctl` characteristic, `CLAIM`/`RELEASE`/`RESET_OWNER`, three-strike disconnect) was removed from the spec at v1.1 and from the SDK. Code that adds `setPairingRequired`, bonding, link encryption, or an ownership handshake contradicts the locked model and won't match the firmware.

**Sniff.** On `+` lines: CoreBluetooth/GATT pairing/bonding APIs (`CBConnectionEvent`, `createBond()`, `setPin`, `ENCRYPTION`/`encryptionRequired`, `BluetoothDevice.createBond`), or symbols named `owner`/`claim`/`release`/`resetOwner`/`ownerCtl`/`threeStrike`.

**Fix.** Remove it — the link is intentionally open and safety lives in firmware. If a genuine security requirement emerges, that's a spec change: update `docs/Sage_Kettle_BLE_protocol_spec_v1.md` + Confluence `1489993739` and get the firmware team's sign-off *before* any SDK code, not the other way round.

---

## P2 — Wire-layout change not reflected in `protocol-fixtures.json`

The SDK and the KMP simulator are kept byte-identical through `docs/protocol-fixtures.json` (canned encode/decode fixtures). A layout change in the code without a matching fixture update means the cross-platform parity test is asserting against the *old* bytes — it either fails spuriously or, worse, passes while iOS and Android silently diverge.

**Sniff.** A change to encode/decode byte offsets, lengths, or opcode constants on `+` lines in `Devices/`/`Enums/` while `docs/protocol-fixtures.json` is absent from the PR's changed-file list.

**Fix.** Update `protocol-fixtures.json` in the same PR with the new expected bytes, and confirm both the iOS and Android decode/encode tests round-trip against it. (This is the wire-layer twin of the doc-sync rule in [`docs-and-confluence-sync.md`](docs-and-confluence-sync.md).)
