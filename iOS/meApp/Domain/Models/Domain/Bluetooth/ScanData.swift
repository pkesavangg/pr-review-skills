import Foundation
import SwiftUI

/// ScanData is used to represent user profile data needed for scale pairing and scanning.
struct ScanData: Codable {
    /// The biological sex of the user (e.g., "male", "female")
    let sex: String
    /// The height of the user in centimeters
    let height: Double
    /// The age of the user in years
    let age: Int
    /// Whether the user is an athlete
    let isAthlete: Bool
    /// The unit of measurement (e.g., "kg", "lb")
    let unit: String
    /// The user's goal weight (optional)
    let goalWeight: Double?
    /// Any additional information received with the scan data
    let additionalInfo: [String: AnyCodable]?
}

/// PairData extends ScanData with pairing-specific fields.
struct PairData: Codable {
    let userName: String
    let userNumber: Int
    // Inherit all fields from ScanData
    let scanData: ScanData
}
