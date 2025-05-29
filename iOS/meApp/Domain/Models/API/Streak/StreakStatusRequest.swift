import Foundation

/// Request DTO for updating streak status
struct StreakStatusRequest: Codable {
    /// Indicates if the streak is on
    let isStreakOn: Bool?
    /// The timestamp for the streak
    let streakTimestamp: String?
}
