/// Stores user account details such as personal info, tokens, and app settings.
///
/// | Column Name                       | Type    | Description                                             |
/// |----------------------------------|---------|---------------------------------------------------------|
/// | id                               | string  | Primary key for the account                            |
/// | accessToken                      | string  | OAuth or app-specific access token                     |
/// | activityLevel                    | string  | User's activity level (e.g., low, moderate, high)      |
/// | dashboardMetrics                 | string  | Metrics selected for dashboard display                 |
/// | dashboardType                    | string  | Layout type of the user's dashboard                    |
/// | dob                              | string  | Date of birth                                          |
/// | email                            | string  | User email address                                     |
/// | expiresAt                        | string  | Access token expiration time                           |
/// | fcmToken                         | string  | Firebase Cloud Messaging token                         |
/// | firstName                        | string  | First name of the user                                 |
/// | gender                           | string  | Gender of the user                                     |
/// | goalType                         | string  | Type of health/fitness goal (e.g., weight loss)        |
/// | goalWeight                       | string  | Target weight as defined by the user                   |

/// | height                           | string  | Height of the user                                     |
/// | initialWeight                    | float   | Weight at account creation or goal start               |
/// | isActiveAccount                  | boolean | Indicates if the account is currently active           |
/// | isFitbitOn                       | boolean | Whether Fitbit integration is enabled                  |
/// | isFitbitValid                    | boolean | Whether Fitbit integration is valid/authenticated      |
/// | isGoogleFitOn                    | boolean | Whether Google Fit is enabled                          |
/// | isGoogleFitValid                 | boolean | Whether Google Fit integration is valid                |
/// | isHealthConnectOn                | boolean | Whether Health Connect integration is enabled          |
/// | isHealthKitOn                    | boolean | Whether Apple HealthKit is enabled                     |
/// | isLoggedIn                       | boolean | If the user is logged in with active session           |
/// | isExpired                        | boolean | Whether the account/session is expired                 |
/// | isMFPOn                          | boolean | Whether MyFitnessPal integration is enabled            |
/// | isMFPValid                       | boolean | Whether MFP integration is valid                       |
/// | isStreakOn                       | boolean | If streak tracking is enabled                          |
/// | isSynced                         | boolean | Is account details are synced online                   |
/// | isUAOn                           | boolean | Under Armour connection enabled                        |
/// | isUAValid                        | boolean | Under Armour connection valid                          |
/// | isWeightlessOn                   | boolean | Weightless mode enabled (app-specific)                 |
/// | lastActiveTime                   | string  | Timestamp of last activity                             |
/// | lastName                         | string  | Last name of the user                                  |
/// | metPreviousGoal                  | boolean | If the user achieved the last set goal                 |
/// | percent                          | float   | Goal completion or progress percent                    |
/// | preferredInputMethod             | string  | User's preferred data entry method                     |
/// | refreshToken                     | string  | OAuth refresh token                                    |
/// | shouldSendEntryNotifications     | boolean | Whether to send reminders for entries                  |
/// | shouldSendWeightInEntryNotifications | boolean | Whether to send reminders for weight-ins           |
/// | streakTimestamp                  | string  | Timestamp for streak tracking                          |
/// | type                             | string  | Account type or role                                   |
/// | weightUnit                       | string  | Unit of weight measurement (kg/lb)                     |
/// | weightlessBodyFat                | float   | Offline/stored body fat value                          |
/// | weightlessMuscle                 | float   | Offline/stored muscle mass value                       |
/// | weightlessTimestamp              | string  | Last updated timestamp for weightless data             |
/// | weightlessWeight                 | float   | Offline/stored weight value                            |
/// | zipcode                          | string  | User's zip/postal code                                 |

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
    var isHealthKitOn: Bool?
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
    var isSynced: Bool?

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

    func toAccountDTO() -> AccountDTO {
        return AccountDTO(
            id: self.id,
            email: self.email,
            firstName: self.firstName,
            lastName: self.lastName,
            gender: self.gender,
            zipcode: self.zipcode,
            weightUnit: self.weightUnit,
            isWeightlessOn: self.isWeightlessOn,
            preferredInputMethod: nil, // Update if you have this property
            height: self.height,
            activityLevel: self.activityLevel,
            dob: self.dob,
            weightlessBodyFat: nil, // Update if you have this property
            weightlessMuscle: nil, // Update if you have this property
            weightlessTimestamp: self.weightlessTimestamp,
            weightlessWeight: self.weightlessWeight,
            isStreakOn: self.isStreakOn,
            dashboardType: self.dashboardType,
            dashboardMetrics: self.dashboardMetrics,
            goalType: self.goalType,
            goalWeight: self.goalWeight,
            initialWeight: self.initialWeight,
            shouldSendEntryNotifications: self.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: self.shouldSendWeightInEntryNotifications,
            isGoogleFitOn: self.isGoogleFitOn,
            isGoogleFitValid: self.isGoogleFitValid,
            isFitbitOn: self.isFitbitOn,
            isFitbitValid: self.isFitbitValid,
            isMFPOn: self.isMFPOn,
            isMFPValid: self.isMFPValid,
            isUAOn: self.isUAOn,
            isUAValid: self.isUAValid
        )
    }
}
