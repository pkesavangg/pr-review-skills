/// Stores user account details such as personal info, tokens, and app settings.
///
/// | Column Name                               | Type    | Description                                       |
/// | ----------------------------------------- | ------- | ------------------------------------------------- |
/// | accountId                                | string  | Primary key for the account                       |
/// | accessToken                              | string  | OAuth or app-specific access token                |
/// | activityLevel                            | string  | User's activity level (e.g., low, moderate, high) |
/// | dashboardMetrics                         | string  | Metrics selected for dashboard display            |
/// | dashboardType                            | string  | Layout type of the user's dashboard               |
/// | dob                                      | string  | Date of birth                                     |
/// | email                                    | string  | User email address                                |
/// | expiresAt                                | string  | Access token expiration time                      |
/// | fcmToken                                 | string  | Firebase Cloud Messaging token                    |
/// | firstName                                | string  | First name of the user                            |
/// | gender                                   | string  | Gender of the user                                |
/// | goalType                                 | string  | Type of health/fitness goal (e.g., weight loss)   |
/// | goalWeight                               | string  | Target weight as defined by the user              |
/// | height                                   | string  | Height of the user                                |
/// | initialWeight                            | float   | Weight at account creation or goal start          |
/// | isActiveAccount                          | boolean | Indicates if the account is currently active      |
/// | isFitbitOn                               | boolean | Whether Fitbit integration is enabled             |
/// | isFitbitValid                            | boolean | Whether Fitbit integration is valid/authenticated |
/// | isGoogleFitOn                            | boolean | Whether Google Fit is enabled                     |
/// | isGoogleFitValid                         | boolean | Whether Google Fit integration is valid           |
/// | isHealthConnectOn                        | boolean | Whether Health Connect integration is enabled     |
/// | isHealthKitOn                            | boolean | Whether Apple HealthKit is enabled                |
/// | isLoggedIn                               | boolean | If the user is logged in with active session      |
/// | isExpired                                | boolean | Whether the account/session is expired            |
/// | isMfpOn                                  | boolean | Whether MyFitnessPal integration is enabled       |
/// | isMfpValid                               | boolean | Whether MFP integration is valid                  |
/// | isStreakOn                               | boolean | If streak tracking is enabled                     |
/// | isSynced                                 | boolean | Is account details are synced online              |
/// | isUaOn                                   | boolean | Under Armour connection enabled                   |
/// | isUaValid                                | boolean | Under Armour connection valid                     |
/// | isWeightlessOn                           | boolean | Weightless mode enabled (app-specific)            |
/// | lastActiveTime                           | string  | Timestamp of last activity                        |
/// | lastName                                 | string  | Last name of the user                             |
/// | metPreviousGoal                          | boolean | If the user achieved the last set goal            |
/// | percent                                  | float   | Goal completion or progress percent               |
/// | preferredInputMethod                     | string  | User's preferred data entry method                |
/// | refreshToken                             | string  | OAuth refresh token                               |
/// | shouldSendEntryNotifications             | boolean | Whether to send reminders for entries             |
/// | shouldSendWeightInEntryNotifications     | boolean | Whether to send reminders for weight-ins          |
/// | streakTimestamp                          | string  | Timestamp for streak tracking                     |
/// | weightUnit                               | string  | Unit of weight measurement (kg/lb)                |
/// | weightlessBodyFat                        | float   | Offline/stored body fat value                     |
/// | weightlessMuscle                         | float   | Offline/stored muscle mass value                  |
/// | weightlessTimestamp                      | string  | Last updated timestamp for weightless data        |
/// | weightlessWeight                         | float   | Offline/stored weight value                       |
/// | zipcode                                  | string  | User's zip/postal code                            |

import Foundation
import SwiftData

@Model
final class Account {
    /// Primary key for the account
    @Attribute(.unique) var accountId: String
    /// OAuth or app-specific access token
    var accessToken: String?
    /// User's activity level (e.g., low, moderate, high)
    var activityLevel: ActivityLevel?
    /// Metrics selected for dashboard display
    var dashboardMetrics: String?
    /// Layout type of the user's dashboard
    var dashboardType: DashboardType?
    /// Date of birth
    var dob: String?
    /// User email address
    var email: String
    /// Access token expiration time
    var expiresAt: String?
    /// Firebase Cloud Messaging token
    var fcmToken: String?
    /// First name of the user
    var firstName: String?
    /// Gender of the user
    var gender: Sex?
    /// Type of health/fitness goal (e.g., weight loss)
    var goalType: GoalType?
    /// Target weight as defined by the user
    var goalWeight: String?
    /// Height of the user
    var height: String?
    /// Weight at account creation or goal start
    var initialWeight: Double?
    /// Indicates if the account is currently active
    var isActiveAccount: Bool?
    /// Whether Fitbit integration is enabled
    var isFitbitOn: Bool?
    /// Whether Fitbit integration is valid/authenticated
    var isFitbitValid: Bool?
    /// Whether Google Fit is enabled
    var isGoogleFitOn: Bool?
    /// Whether Google Fit integration is valid
    var isGoogleFitValid: Bool?
    /// Whether Health Connect integration is enabled
    var isHealthConnectOn: Bool?
    /// Whether Apple HealthKit is enabled
    var isHealthKitOn: Bool?
    /// If the user is logged in with active session
    var isLoggedIn: Bool?
    /// Whether the account/session is expired
    var isExpired: Bool?
    /// Whether MyFitnessPal integration is enabled
    var isMfpOn: Bool?
    /// Whether MFP integration is valid
    var isMfpValid: Bool?
    /// If streak tracking is enabled
    var isStreakOn: Bool?
    /// Is account details are synced online
    var isSynced: Bool?
    /// Under Armour connection enabled
    var isUaOn: Bool?
    /// Under Armour connection valid
    var isUaValid: Bool?
    /// Weightless mode enabled (app-specific)
    var isWeightlessOn: Bool?
    /// Timestamp of last activity
    var lastActiveTime: String?
    /// Last name of the user
    var lastName: String?
    /// If the user achieved the last set goal
    var metPreviousGoal: Bool?
    /// Goal completion or progress percent
    var percent: Double?
    /// User's preferred data entry method
    var preferredInputMethod: String?
    /// OAuth refresh token
    var refreshToken: String?
    /// Whether to send reminders for entries
    var shouldSendEntryNotifications: Bool?
    /// Whether to send reminders for weight-ins
    var shouldSendWeightInEntryNotifications: Bool?
    /// Timestamp for streak tracking
    var streakTimestamp: String?
    /// Unit of weight measurement (kg/lb)
    var weightUnit: WeightUnit?
    /// Offline/stored body fat value
    var weightlessBodyFat: Double?
    /// Offline/stored muscle mass value
    var weightlessMuscle: Double?
    /// Last updated timestamp for weightless data
    var weightlessTimestamp: String?
    /// Offline/stored weight value
    var weightlessWeight: Double?
    /// User's zip/postal code
    var zipcode: String?

    init(from dto: AccountDTO) {
        self.accountId = dto.id
        self.email = dto.email
        self.firstName = dto.firstName
        self.lastName = dto.lastName
        self.gender = dto.gender
        self.zipcode = dto.zipcode
        self.dob = dto.dob
        self.weightUnit = dto.weightUnit
        self.height = String(dto.height)
        self.activityLevel = dto.activityLevel
        self.goalWeight = dto.goalWeight.map { String($0) }
        self.goalType = dto.goalType
        self.initialWeight = dto.initialWeight
        self.metPreviousGoal = nil
        self.percent = nil
        self.isWeightlessOn = dto.isWeightlessOn
        self.weightlessWeight = dto.weightlessWeight
        self.weightlessTimestamp = dto.weightlessTimestamp
        self.weightlessBodyFat = dto.weightlessBodyFat
        self.weightlessMuscle = dto.weightlessMuscle
        self.shouldSendEntryNotifications = dto.shouldSendEntryNotifications
        self.shouldSendWeightInEntryNotifications = dto.shouldSendWeightInEntryNotifications
        self.isFitbitOn = dto.isFitbitOn
        self.isGoogleFitOn = dto.isGoogleFitOn
        self.isMfpOn = dto.isMFPOn
        self.isUaOn = dto.isUAOn
        self.isFitbitValid = dto.isFitbitValid
        self.isGoogleFitValid = dto.isGoogleFitValid
        self.isMfpValid = dto.isMFPValid
        self.isUaValid = dto.isUAValid
        self.isHealthKitOn = nil
        self.isHealthConnectOn = nil
        self.accessToken = nil
        self.refreshToken = nil
        self.expiresAt = nil
        self.isStreakOn = dto.isStreakOn
        self.dashboardMetrics = dto.dashboardMetrics?.map { String(describing: $0) }.joined(separator: ",")
        self.dashboardType = dto.dashboardType
        self.isLoggedIn = nil
        self.isActiveAccount = nil
        self.isExpired = nil
        self.lastActiveTime = nil
        self.fcmToken = nil
        self.isSynced = nil
    }

    func toAccountDTO() -> AccountDTO {
        return AccountDTO(
            id: self.accountId,
            email: self.email,
            firstName: self.firstName ?? "",
            lastName: self.lastName,
            gender: self.gender ?? .male,
            zipcode: self.zipcode,
            weightUnit: self.weightUnit ?? .lb,
            isWeightlessOn: self.isWeightlessOn,
            preferredInputMethod: self.preferredInputMethod,
            height: Double(self.height ?? "0") ?? 0.0,
            activityLevel: self.activityLevel,
            dob: self.dob ?? "",
            weightlessBodyFat: self.weightlessBodyFat,
            weightlessMuscle: self.weightlessMuscle,
            weightlessTimestamp: self.weightlessTimestamp,
            weightlessWeight: self.weightlessWeight,
            isStreakOn: self.isStreakOn,
            dashboardType: self.dashboardType,
            dashboardMetrics: self.dashboardMetrics?.split(separator: ",").compactMap { BodyMetric(rawValue: String($0)) },
            goalType: self.goalType,
            goalWeight: self.goalWeight.flatMap { Double($0) },
            initialWeight: self.initialWeight,
            shouldSendEntryNotifications: self.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: self.shouldSendWeightInEntryNotifications,
            isGoogleFitOn: self.isGoogleFitOn,
            isGoogleFitValid: self.isGoogleFitValid,
            isFitbitOn: self.isFitbitOn,
            isFitbitValid: self.isFitbitValid,
            isMFPOn: self.isMfpOn,
            isMFPValid: self.isMfpValid,
            isUAOn: self.isUaOn,
            isUAValid: self.isUaValid,
            isHealthKitOn: self.isHealthKitOn,
            isHealthConnectOn: self.isHealthConnectOn
        )
    }
}
