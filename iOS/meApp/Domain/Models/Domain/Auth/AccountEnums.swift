import Foundation

// MARK: - GoalType

enum GoalType: String, Codable, Equatable {
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

enum Sex: String, Codable, Equatable {
    case male = "male"
    case female = "female"
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
}
