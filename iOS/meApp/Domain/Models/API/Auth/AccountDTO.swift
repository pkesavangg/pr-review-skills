import Foundation

struct AccountDTO: Codable {
    let id: String
    let email: String
    let firstName: String
    let lastName: String?
    let gender: Sex
    let zipcode: String?
    let weightUnit: WeightUnit
    let isWeightlessOn: Bool?
    let preferredInputMethod: String?
    let height: Double
    let activityLevel: ActivityLevel?
    let dob: String
    let weightlessBodyFat: Double?
    let weightlessMuscle: Double?
    let weightlessTimestamp: String?
    let weightlessWeight: Double?
    let isStreakOn: Bool?
    let dashboardType: DashboardType?
    let dashboardMetrics: [BodyMetric]?
    let goalType: GoalType?
    let goalWeight: Double?
    let initialWeight: Double?
    let shouldSendEntryNotifications: Bool?
    let shouldSendWeightInEntryNotifications: Bool?
    let isGoogleFitOn: Bool?
    let isGoogleFitValid: Bool?
    let isFitbitOn: Bool?
    let isFitbitValid: Bool?
    let isMFPOn: Bool?
    let isMFPValid: Bool?
    let isUAOn: Bool?
    let isUAValid: Bool?
    let isHealthKitOn: Bool?
    let isHealthConnectOn: Bool?
}
