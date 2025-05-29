import Foundation

/// Request DTO for setting a user's goal
struct GoalRequest: Codable {
    /// The type of goal
    let goalType: GoalType
    /// The target goal weight
    let goalWeight: Double
    /// The initial weight
    let initialWeight: Double
    /// Whether the previous goal was met
    let metPreviousGoal: Bool?
    /// The percent progress towards the goal
    let percent: Double?
}
