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
    case kg
    case lb
}

// MARK: - DashboardType

enum DashboardType: String, Codable, Equatable {
    case dashboard4 = "dashboard_4_metrics"
    case dashboard12 = "dashboard_12_metrics"
}
