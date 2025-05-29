import Foundation

/// Model representing user progress statistics
struct Progress: Codable {
    /// The count of operations
    let count: Int?
    /// The current streak count
    let currentStreak: Int
    /// The initial operation for the year
    let initYear: BathScaleOperationDTO?
    /// The initial operation for the month
    let initMonth: BathScaleOperationDTO?
    /// The initial operation for the week
    let initWeek: BathScaleOperationDTO?
    /// The initial weight
    let initWt: Double
    /// The latest operation
    let latest: BathScaleOperationDTO?
    /// The longest streak count
    let longestStreak: Int
    /// The current month number
    let month: Int
    /// The percent progress
    let percent: Double?
    /// The total progress value
    let total: Double?
    /// The current week number
    let week: Int
    /// The current year number
    let year: Int
}
