import SwiftUI

// MARK: - Data Models

/// Represents the data for a goal progress card
struct GoalCardData: Identifiable, Equatable {
    /// Unique identifier for the goal
    let id = UUID()
    
    /// The current change in weight (positive for gain, negative for loss)
    let delta: Double
    
    /// The starting weight
    let startWeight: Double
    
    /// The target goal weight
    let goalWeight: Double
    
    /// The unit of measurement (e.g., "lbs", "kg")
    let unit: String
    
    /// Whether this goal has been removed/hidden
    let isRemoved: Bool
    
    /// Progress value between 0.0 and 1.0
    let progress: CGFloat
    
    /// The type of goal (gain, lose, or maintain)
    let goalType: GoalType
    
    /// Custom Equatable conformance - compare all fields except id
    static func == (lhs: GoalCardData, rhs: GoalCardData) -> Bool {
        lhs.delta == rhs.delta &&
        lhs.startWeight == rhs.startWeight &&
        lhs.goalWeight == rhs.goalWeight &&
        lhs.unit == rhs.unit &&
        lhs.isRemoved == rhs.isRemoved &&
        lhs.progress == rhs.progress &&
        lhs.goalType == rhs.goalType
    }
}

/// Represents the data for a streak card
struct StreakCardData: Identifiable, Equatable {
    /// Unique identifier for the streak
    let id = UUID()
    
    /// The streak value to display (e.g., "5 days", "-2 lbs")
    let value: String
    
    /// The label describing the streak (e.g., "Current Streak")
    let label: String
    
    /// Optional SF Symbol icon name to display
    let icon: String?
    
    /// Whether this streak has been removed/hidden
    let isRemoved: Bool
    
    /// Custom Equatable conformance - compare all fields except id
    static func == (lhs: StreakCardData, rhs: StreakCardData) -> Bool {
        lhs.value == rhs.value &&
        lhs.label == rhs.label &&
        lhs.icon == rhs.icon &&
        lhs.isRemoved == rhs.isRemoved
    }
}

// MARK: - Row Model

/// Represents a row in the dashboard grid
enum DashboardRow: Identifiable, Equatable {
    /// A single goal card row
    case goal(GoalCardData)
    
    /// A row containing up to 2 streak cards
    case streaks([StreakCardData])
    
    /// Stable identifier used when a streaks row has no items
    private static let emptyStreaksId = UUID()
    
    /// Unique identifier for the row
    var id: UUID {
        switch self {
        case .goal(let data):
            return data.id
        case .streaks(let items):
            return items.first?.id ?? DashboardRow.emptyStreaksId
        }
    }
}

// MARK: - Date Range Models

/// Result of date range operations filtering
struct DateRangeOperationsResult {
    let operations: [BathScaleWeightSummary]
    let cachedPeriod: TimePeriod
    let cachedScrollPos: Date
    let cachedOps: [BathScaleWeightSummary]
}

// MARK: - Configuration Models

/// Configuration for loading dashboard configuration from API
struct DashboardConfigurationLoadConfig {
    let refreshAccount: () async throws -> Void
    let syncEntries: () async -> Void
    let loadMetricsFromAPI: () async throws -> Void
    let refreshStreakData: () async throws -> Void
    let loadProgressMetrics: () async -> Void
    let loadGoalData: () async throws -> Void
    let onMetricsLoaded: () -> Void
    let onProgressMetricsLoaded: () -> Void
    let onError: (Error) -> Void
}
