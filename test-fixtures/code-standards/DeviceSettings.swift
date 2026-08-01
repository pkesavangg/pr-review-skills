// Fixture for references/code-standards/swift.md. Deliberate triggers — do not fix.
//
// Expected findings:
//   P1  stringly-typed value where an enum belongs (unit, status)
//   P1  bare numeric primitive for a domain quantity (weight, temperature)
//   P1  switch over an internal enum closed with `default:`
//   P2  class where a struct fits / non-final class
//   P2  Swift API Design Guidelines naming (get- prefix, I- protocol, SCREAMING_SNAKE, abbreviations)
//   P2  var that is never mutated
//   P2  magic literals with no named constant
//   P2  missing access control on new declarations

import Foundation

// P2 — `I` prefix is a C#/Java habit; a Swift protocol names a capability or a thing.
protocol IDeviceStore {
    func save(_ device: DeviceRecord)
}

// P2 — pure data with no identity and no inheritance: should be a struct.
//      Also non-final, with no subclass anywhere.
class DeviceRecord {
    // P2 — SCREAMING_SNAKE_CASE is not a Swift convention.
    static let MAX_RETRY_COUNT = 3

    // P2 — type name repeated in the property name; abbreviation `mgr`.
    var nameString: String
    var mgr: IDeviceStore?

    // P1 — bare Double for a domain quantity: kg or lb? nothing says, nothing checks.
    var weight: Double
    // P1 — same for temperature; °C, °F, or tenths?
    var temperature: Double

    // P1 — stringly-typed: "pairing", "paierd", and "banana" all compile.
    var status: String

    init(nameString: String, weight: Double, temperature: Double, status: String) {
        self.nameString = nameString
        self.weight = weight
        self.temperature = temperature
        self.status = status
    }

    // P2 — `get` prefix on a value-returning method; should read `displayName`.
    func getDisplayName() -> String {
        // P2 — var never mutated
        var prefix = "Device"
        return "\(prefix): \(nameString)"
    }

    // P2 — boolean method that doesn't read as an assertion; should be `isReadyToSync`.
    func checkSync() -> Bool {
        // P2 — magic literals: what are 0.5 and 120?
        return temperature > 0.5 && weight < 120
    }
}

enum UnitSystem {
    case imperial
    case metric
}

// P1 — `default:` disables exhaustiveness: add `.stone` and this silently returns "".
func unitLabel(_ unit: UnitSystem) -> String {
    switch unit {
    case .imperial: return "lb"
    case .metric: return "kg"
    default: return ""
    }
}

// P2 — no access control: internal by default, intent unstated, and in a framework
//      target this silently widens the surface you must keep working.
func syncAll(store: IDeviceStore, records: [DeviceRecord]) {
    for record in records {
        store.save(record)
    }
}
