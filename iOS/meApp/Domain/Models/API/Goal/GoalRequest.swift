import Foundation

struct GoalRequest: Codable {
    var goalWeight: Double
    var goalType: GoalType 
    var initialWeight: Double
    var metPreviousGoal: Bool?
    var percent: Double?
}
