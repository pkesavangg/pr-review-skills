import Foundation

struct AccountDTO: Codable {
    let id: String
    let email: String
    let firstName: String
    let lastName: String?
    let gender: Sex?
    let zipcode: String?
    let weightUnit: WeightUnit
    let isWeightlessOn: Bool?
    let height: Double?
    let activityLevel: ActivityLevel?
    let dob: String?
    let weightlessTimestamp: String?
    let weightlessWeight: Double?
    let isStreakOn: Bool?
    let streakTimestamp: String?
    let dashboardType: DashboardType?
    let dashboardMetrics: [BodyMetric]?
    let progressMetrics: [String]?
    let goalType: GoalType?
    let goalWeight: Double?
    let goalPercent: Double?
    let initialWeight: Double?
    let shouldSendEntryNotifications: Bool?
    let shouldSendWeightInEntryNotifications: Bool?
    let isFitbitOn: Bool?
    let isFitbitValid: Bool?
    let isMFPOn: Bool?
    let isMFPValid: Bool?
    let isHealthKitOn: Bool?
    let isHealthConnectOn: Bool?
    /// Products the user has access to (auto-managed by the server).
    /// Allowed: "weight", "blood_pressure", "baby". Absent on legacy responses.
    let productTypes: [String]?
    /// Preferred measurement units: "metric", "imperialLbOz", "imperialLbDecimal".
    /// May be null until the user sets it (e.g. baby product).
    let measurementUnits: String?
}
