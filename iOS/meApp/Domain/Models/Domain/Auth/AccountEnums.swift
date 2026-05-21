import Foundation

// MARK: - GoalType

enum GoalType: String, Codable, Equatable {
    case none = "none"
    case gain = "gain"
    case lose = "lose"
    case maintain = "maintain"
}

// MARK: - ActivityLevel

enum ActivityLevel: String, Codable, Equatable {
    case normal = "normal"
    case athlete = "athlete"
}

// MARK: - Sex

enum Sex: String, Codable, Equatable, CaseIterable {
    case male = "male"
    case female = "female"
    
    init?(rawInput: String?) {
        guard let input = rawInput?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return nil
        }
        
        switch input {
        case "male":
            self = .male
        case "female":
            self = .female
        default:
            return nil
        }
    }
}

// MARK: - WeightUnit

enum WeightUnit: String, Codable, Equatable {
    case kg = "kg"
    case lb = "lb"
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
