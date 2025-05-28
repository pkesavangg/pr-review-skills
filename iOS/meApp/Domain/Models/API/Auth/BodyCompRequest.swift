import Foundation

struct BodyCompRequest: Codable {
    let height: Double
    let activityLevel: ActivityLevel?
    let weightUnit: String
}
