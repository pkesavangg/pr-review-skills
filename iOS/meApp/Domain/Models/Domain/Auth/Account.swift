import Foundation
import SwiftData

@Model
final class Account {
    @Attribute(.unique) var id: String
    var email: String
    var firstName: String
    var lastName: String?
    var gender: Sex
    var zipcode: String?
    var dob: String
    var weightUnit: String
    var height: Double
    var activityLevel: ActivityLevel?

    // Goal
    var goalWeight: Double?
    var goalType: GoalType?
    var type: GoalType?
    var initialWeight: Double?
    var metPreviousGoal: Bool?
    var percent: Double?

    // Weightless
    var isWeightlessOn: Bool?
    var weightlessWeight: Double?
    var weightlessTimestamp: String?

    // Notifications
    var shouldSendEntryNotifications: Bool?
    var shouldSendWeightInEntryNotifications: Bool?

    // Integrations
    var isFitbitOn: Bool?
    var isGoogleFitOn: Bool?
    var isMFPOn: Bool?
    var isUAOn: Bool?
    var isFitbitValid: Bool?
    var isGoogleFitValid: Bool?
    var isMFPValid: Bool?
    var isUAValid: Bool?
    var healthkit: Bool?
    var isHealthConnectOn: Bool?

    // Tokens
    var accessToken: String?
    var refreshToken: String?
    var expiresAt: String?

    // StreakStatus
    var isStreakOn: Bool?
    var streakTimestamp: String?

    // DashboardMetrics
    var dashboardMetrics: [BodyMetric]?

    // UpdateDashboardType
    var dashboardType: DashboardType?

    // Account-specific properties
    var loggedIn: Int?
    var isActiveAccount: Bool?
    var isLoggedIn: Bool?
    var isExpired: Bool?
    var lastActiveTime: String?
    var fcmToken: String?

    init(from dto: AccountDTO) {
        self.id = dto.id
        self.email = dto.email
        self.firstName = dto.firstName
        self.lastName = dto.lastName
        self.gender = dto.gender
        self.zipcode = dto.zipcode
        self.dob = dto.dob
        self.weightUnit = dto.weightUnit
        self.height = dto.height
        self.activityLevel = dto.activityLevel
        self.goalWeight = dto.goalWeight
        self.goalType = dto.goalType
        self.type = dto.goalType // or dto.type if present
        self.initialWeight = dto.initialWeight
        self.metPreviousGoal = nil // Not present in DTO
        self.percent = nil // Not present in DTO
        self.isWeightlessOn = dto.isWeightlessOn
        self.weightlessWeight = dto.weightlessWeight
        self.weightlessTimestamp = dto.weightlessTimestamp
        self.shouldSendEntryNotifications = dto.shouldSendEntryNotifications
        self.shouldSendWeightInEntryNotifications = dto.shouldSendWeightInEntryNotifications
        self.isFitbitOn = dto.isFitbitOn
        self.isGoogleFitOn = dto.isGoogleFitOn
        self.isMFPOn = dto.isMFPOn
        self.isUAOn = dto.isUAOn
        self.isFitbitValid = dto.isFitbitValid
        self.isGoogleFitValid = dto.isGoogleFitValid
        self.isMFPValid = dto.isMFPValid
        self.isUAValid = dto.isUAValid
        self.isHealthKitOn = nil // Not present in DTO
        self.isHealthConnectOn = nil // Not present in DTO
        self.accessToken = nil // Not present in DTO
        self.refreshToken = nil // Not present in DTO
        self.expiresAt = nil // Not present in DTO
        self.isStreakOn = dto.isStreakOn
        self.streakTimestamp = nil // Not present in DTO
        self.dashboardMetrics = dto.dashboardMetrics
        self.dashboardType = dto.dashboardType
        self.loggedIn = nil // Not present in DTO
        self.isActiveAccount = nil // Not present in DTO
        self.isLoggedIn = nil // Not present in DTO
        self.isExpired = nil // Not present in DTO
        self.lastActiveTime = nil // Not present in DTO
        self.fcmToken = nil // Not present in DTO
    }

    toAccountDTO() -> AccountDTO {
        return AccountDTO(
            id: self.id,
            email: self.email,
            firstName: self.firstName,
            lastName: self.lastName,
            gender: self.gender,
            zipcode: self.zipcode,
            dob: self.dob,
            weightUnit: self.weightUnit,
            height: self.height,
            activityLevel: self.activityLevel,
            goalWeight: self.goalWeight,
            goalType: self.goalType,
            initialWeight: self.initialWeight,
            isWeightlessOn: self.isWeightlessOn,
            weightlessWeight: self.weightlessWeight,
            weightlessTimestamp: self.weightlessTimestamp,
            shouldSendEntryNotifications: self.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: self.shouldSendWeightInEntryNotifications,
            isFitbitOn: self.isFitbitOn,
            isGoogleFitOn: self.isGoogleFitOn,
            isMFPOn: self.isMFPOn,
            isUAOn: self.isUAOn,
            isFitbitValid: self.isFitbitValid,
            isGoogleFitValid: self.isGoogleFitValid,
            isMFPValid: self.isMFPValid,
            isUAValid: self.isUAValid,
        )
    }
}
