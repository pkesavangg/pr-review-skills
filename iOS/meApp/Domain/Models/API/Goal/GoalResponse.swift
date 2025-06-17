import Foundation

/// Represents a response containing goal information for a user.
struct GoalResponse: Codable {
    /// The type of goal
    let type: GoalType
    /// The target goal weight
    let goalWeight: Double
    /// The date and time when the goal was created
    let createdAt: String
    /// The initial weight of the user
    let initialWeight: Double
}
