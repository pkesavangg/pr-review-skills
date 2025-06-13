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
    var weight: Double?
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
    /// Date of birth
    var dob: String?
    /// Metrics selected for dashboard display
    var dashboardMetrics: String?
    /// Layout type of the user's dashboard
    var dashboardType: DashboardType?
    
    // Relationship to WeightCompSettings
    @Relationship(deleteRule: .cascade) var weightSettings: WeightCompSettings?
    // Relationship to WeightCompSettings
    @Relationship(deleteRule: .cascade) var goalSettings: GoalSettings?
    init(from dto: AccountDTO) {
        self.accountId = dto.id
        self.email = dto.email
        self.firstName = dto.firstName
        self.lastName = dto.lastName
        self.gender = dto.gender
        self.zipcode = dto.zipcode
        self.dob = dto.dob
        self.goalWeight = dto.goalWeight.map { String($0) }
        self.goalType = dto.goalType
        self.weight = dto.weight
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
        
        // Create associated WeightCompSettings
        let settings = WeightCompSettings(
            accountId: dto.id,
            height: dto.height != nil ? String(dto.height) : nil,
            activityLevel: dto.activityLevel,
            weightUnit: dto.weightUnit
        )
        self.weightSettings = settings
    }

    func toAccountDTO() -> AccountDTO {
        return AccountDTO(
            id: self.accountId,
            email: self.email,
            firstName: self.firstName ?? "",
            lastName: self.lastName,
            gender: self.gender ?? .male,
            zipcode: self.zipcode,
            weightUnit: self.weightSettings?.weightUnit ?? .lb,
            isWeightlessOn: self.isWeightlessOn,
            preferredInputMethod: self.preferredInputMethod,
            height: Double(self.weightSettings?.height ?? "0") ?? 0.0,
            activityLevel: self.weightSettings?.activityLevel,
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
            weight: self.weight,
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

// MARK: - Update Methods
extension Account {
    func update(from response: AccountDTO) {
        self.accountId = response.id
        self.email = response.email
        self.firstName = response.firstName
        self.gender = response.gender
        self.height = String(response.height)
        self.dob = response.dob
        
        if let weightSettings = self.weightSettings {
            weightSettings.height = response.height != nil ? String(response.height) : nil
            weightSettings.activityLevel = response.activityLevel
            weightSettings.weightUnit = response.weightUnit
        } else {
            let settings = WeightCompSettings(
                accountId: response.id,
                height: response.height != nil ? String(response.height) : nil,
                activityLevel: response.activityLevel,
                weightUnit: response.weightUnit
            )
            self.weightSettings = settings
        }

        if let lastName = response.lastName {
            self.lastName = lastName
        }
        if let zipcode = response.zipcode {
            self.zipcode = zipcode
        }
        if let isWeightlessOn = response.isWeightlessOn {
            self.isWeightlessOn = isWeightlessOn
        }
        if let preferredInputMethod = response.preferredInputMethod {
            self.preferredInputMethod = preferredInputMethod
        }
        if let weightlessBodyFat = response.weightlessBodyFat {
            self.weightlessBodyFat = weightlessBodyFat
        }
        if let weightlessMuscle = response.weightlessMuscle {
            self.weightlessMuscle = weightlessMuscle
        }
        if let weightlessTimestamp = response.weightlessTimestamp {
            self.weightlessTimestamp = weightlessTimestamp
        }
        if let weightlessWeight = response.weightlessWeight {
            self.weightlessWeight = weightlessWeight
        }
        if let isStreakOn = response.isStreakOn {
            self.isStreakOn = isStreakOn
        }
        if let dashboardType = response.dashboardType {
            self.dashboardType = dashboardType
        }
        if let dashboardMetrics = response.dashboardMetrics {
            self.dashboardMetrics = dashboardMetrics.map { String(describing: $0) }.joined(separator: ",")
        }
        if let goalType = response.goalType {
            self.goalType = goalType
        }
        if let goalWeight = response.goalWeight {
            self.goalWeight = String(goalWeight)
        }
        if let initialWeight = response.weight {
            self.weight = initialWeight
        }
        if let shouldSendEntryNotifications = response.shouldSendEntryNotifications {
            self.shouldSendEntryNotifications = shouldSendEntryNotifications
        }
        if let shouldSendWeightInEntryNotifications = response.shouldSendWeightInEntryNotifications {
            self.shouldSendWeightInEntryNotifications = shouldSendWeightInEntryNotifications
        }
        if let isGoogleFitOn = response.isGoogleFitOn {
            self.isGoogleFitOn = isGoogleFitOn
        }
        if let isGoogleFitValid = response.isGoogleFitValid {
            self.isGoogleFitValid = isGoogleFitValid
        }
        if let isFitbitOn = response.isFitbitOn {
            self.isFitbitOn = isFitbitOn
        }
        if let isFitbitValid = response.isFitbitValid {
            self.isFitbitValid = isFitbitValid
        }
        if let isMFPOn = response.isMFPOn {
            self.isMfpOn = isMFPOn
        }
        if let isMFPValid = response.isMFPValid {
            self.isMfpValid = isMFPValid
        }
        if let isUAOn = response.isUAOn {
            self.isUaOn = isUAOn
        }
        if let isUAValid = response.isUAValid {
            self.isUaValid = isUAValid
        }
        if let isHealthKitOn = response.isHealthKitOn {
            self.isHealthKitOn = isHealthKitOn
        }
        if let isHealthConnectOn = response.isHealthConnectOn {
            self.isHealthConnectOn = isHealthConnectOn
        }
    }
    
    // Add a separate method for updating from AccountResponse
    func update(from response: AccountResponse) {
        // Update account data
        update(from: response.account)
        
        // Update tokens
        if let accessToken = response.accessToken {
            self.accessToken = accessToken
        }
        if let refreshToken = response.refreshToken {
            self.refreshToken = refreshToken
        }
        if let expiresAt = response.expiresAt {
            self.expiresAt = expiresAt
        }
        
        self.isSynced = true
    }
    
    // Add a method to update from Tokens
    func update(from tokens: Tokens) {
        self.accessToken = tokens.accessToken
        self.refreshToken = tokens.refreshToken
        self.expiresAt = tokens.expiresAt
    }
    
    // Add a method to update from Profile
    func update(from profile: Profile) {
        self.firstName = profile.firstName
        self.lastName = profile.lastName
        self.gender = profile.gender
        self.zipcode = profile.zipcode
        self.dob = profile.dob
        self.weightSettings?.weightUnit = profile.weightUnit
        self.weightSettings?.height = String(profile.height)
        self.weightSettings?.activityLevel = profile.activityLevel
        // Optionally update goalSettings if profile contains goal info (add logic if needed)
    }
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension Account: @unchecked Sendable {}
