//
//  EntryUnit.swift
//  meApp
//

/// Measurement unit for unified entry operations.
///
/// - lb: Pounds (weight)
/// - kg: Kilograms (weight)
/// - lbOz: Pounds and ounces (baby weight)
/// - mmhg: Millimeters of mercury (blood pressure)
enum EntryUnit: String, Codable, Equatable {
    case lb
    case kg
    case lbOz = "lb_oz"
    case mmhg

    /// Human-readable unit label for display in the UI.
    var displayString: String {
        switch self {
        case .lb: return "lb"
        case .kg: return "kg"
        case .lbOz: return "lb oz"
        case .mmhg: return "mmHg"
        }
    }
}
