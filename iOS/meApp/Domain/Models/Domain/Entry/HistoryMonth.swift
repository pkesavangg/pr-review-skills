import Foundation

/// Represents a summary of entries for a given month, including average weight and body metrics.
struct HistoryMonth: Codable, Equatable, Identifiable, Hashable {
    /// Unique identifier for the month (could be the first entry's id or a composite key)
    let id: String
    /// Average or representative weight for the month
    let weight: Double?
    /// The month in 'YYYY-MM' format
    let entryTimestamp: String
    /// Number of entries in the month
    let count: Int?
    /// Raw weights and timestamps as a concatenated string (e.g., "70|2024-06-01,71|2024-06-02")
    let weights: String?
    /// Change in weight over the month (as a string, e.g., "+1.2")
    let change: String?
    /// Body fat percentage
    let bodyFat: Double?
    /// Muscle mass percentage
    let muscleMass: Double?
    /// Water percentage
    let water: Double?
    /// BMI
    let bmi: Double?
    /// Date of the month
    let date: String?
    /// Time of the month
    let time: String?
    /// Month of the year
    let month: String?
    /// Year of the month
    let year: String?
    /// Min weight of the month
    let min: Double?
    /// Max weight of the month
    let max: Double?
}
