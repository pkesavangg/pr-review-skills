import Foundation

// MARK: - GoalType

enum GoalType: String, Codable, Equatable {
    case none
    case gain
    case lose
    case maintain
}

// MARK: - ActivityLevel

enum ActivityLevel: String, Codable, Equatable {
    case normal
    case athlete
}

// MARK: - Sex

enum Sex: String, Codable, Equatable, CaseIterable {
    case male
    case female
    case `private`

    init?(rawInput: String?) {
        guard let input = rawInput?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return nil
        }

        switch input {
        case "male":
            self = .male
        case "female":
            self = .female
        case "private":
            self = .private
        default:
            return nil
        }
    }
}

// MARK: - WeightUnit

enum WeightUnit: String, Codable, Equatable {
    case kg
    case lb
}

// MARK: - MeasurementUnits

/// Preferred measurement units for multi-product accounts.
/// Mirrors the server `measurementUnits` field (see Me App 2.0 API spec).
enum MeasurementUnits: String, Codable, Equatable, CaseIterable {
    case metric
    case imperialLbOz
    case imperialLbDecimal
}

// MARK: - ProductType

/// Product-type identifiers persisted in `account.productTypes`.
/// Raw values match the persisted vocabulary: "myWeight", "myBloodPressure", "baby".
enum ProductType: String, Codable, Equatable, CaseIterable {
    case weight = "myWeight"
    case bloodPressure = "myBloodPressure"
    case baby
}

extension ProductType {
    /// The API-format string sent to and received from the server.
    /// Distinct from `rawValue`, which is the app-internal persisted vocabulary.
    var apiValue: String {
        switch self {
        case .weight: return "weight"
        case .bloodPressure: return "blood_pressure"
        case .baby: return "baby"
        }
    }
}

// MARK: - DashboardType

enum DashboardType: String, Codable, Equatable {
    case dashboard4 = "dashboard_4_metrics"
    case dashboard12 = "dashboard_12_metrics"

    // Account.applyResponse(_:) historically writes case names ("dashboard12") instead of
    // rawValues ("dashboard_12_metrics") via String(describing:), so stored values appear in
    // both forms. Use this resolver anywhere that reads dashboardType until the writer is
    // corrected and existing user data is migrated.
    static func from(stored value: String?) -> DashboardType? {
        guard let value = value else { return nil }
        if let direct = DashboardType(rawValue: value) { return direct }
        switch value {
        case "dashboard4":  return .dashboard4
        case "dashboard12": return .dashboard12
        default:            return nil
        }
    }
}
