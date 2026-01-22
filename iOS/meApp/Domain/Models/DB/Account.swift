/// Stores user account details such as personal info, tokens, and app settings.
///
/// | Column Name      | Type    | Description                                 |
/// |-----------------|---------|---------------------------------------------|
/// | accountId       | string  | Primary key for the account                 |
/// | email           | string  | User email address                          |
/// | firstName       | string  | First name of the user                      |
/// | lastName        | string  | Last name of the user                       |
/// | gender          | Sex?    | Gender of the user                          |
/// | height          | string? | Height (legacy, see WeightCompSettings)     |
/// | dob             | string? | Date of birth                               |
/// | zipcode         | string? | User's zip/postal code                      |
/// | isLoggedIn      | bool?   | If the user is logged in                    |
/// | isExpired       | bool?   | Whether the account/session is expired      |
/// | isActiveAccount | bool?   | Indicates if the account is active          |
/// | accessToken     | string? | OAuth or app-specific access token           |
/// | refreshToken    | string? | OAuth refresh token                         |
/// | expiresAt       | string? | Access token expiration time                |
/// | fcmToken        | string? | Firebase Cloud Messaging token              |
/// | lastActiveTime  | string? | Timestamp of last activity                  |
/// | isSynced        | bool?   | Whether account is synced online            |
///
/// | Relationship Name      | Model/Class           | Description                                  |
/// |-----------------------|-----------------------|----------------------------------------------|
/// | weightSettings        | WeightCompSettings    | Weight, height, activity, weight unit, etc.  |
/// | goalSettings          | GoalSettings          | Goal type, initial weight, goal weight, etc. |
/// | streaksSettings       | StreaksSettings       | Streak tracking info                         |
/// | weightlessSettings    | WeightlessSettings    | Weightless mode info                         |
/// | notificationSettings  | NotificationSettings  | Notification preferences                     |
/// | dashboardSettings     | DashboardSettings     | Dashboard metrics and type                   |
/// | integrationSettings   | IntegrationSettings   | 3rd-party integration flags                  |

import Foundation
import SwiftData

@Model
final class Account {
    /// Primary key for the account
    @Attribute(.unique) var accountId: String
    /// User email address
    var email: String
    /// First name of the user
    var firstName: String?
    /// Last name of the user
    var lastName: String?
    /// Gender of the user
    var gender: Sex?
    /// Height of the user
    var height: String?
    /// Date of birth
    var dob: String?
    /// User's zip/postal code
    var zipcode: String?
    /// If the user is logged in with active session
    var isLoggedIn: Bool?
    /// Whether the account/session is expired
    var isExpired: Bool?
    /// Indicates if the account is currently active
    var isActiveAccount: Bool?
    /// OAuth or app-specific access token
    var accessToken: String?
    /// OAuth refresh token
    var refreshToken: String?
    /// Access token expiration time
    var expiresAt: String?
    /// Firebase Cloud Messaging token
    var fcmToken: String?
    /// Timestamp of last activity
    var lastActiveTime: String?
    /// Whether account is updated and synced online
    var isSynced: Bool?
    
    // Relationship to WeightCompSettings
    @Relationship(deleteRule: .cascade) var weightSettings: WeightCompSettings?
    // Relationship to WeightCompSettings
    @Relationship(deleteRule: .cascade) var goalSettings: GoalSettings?
    // Relationship to StreaksSettings
    @Relationship(deleteRule: .cascade) var streaksSettings: StreaksSettings?
    // Relationship to WeightlessSettings
    @Relationship(deleteRule: .cascade) var weightlessSettings: WeightlessSettings?
    // Relationship to NotificationSettings
    @Relationship(deleteRule: .cascade) var notificationSettings: NotificationSettings?
    // Relationship to DashboardSettings
    @Relationship(deleteRule: .cascade) var dashboardSettings: DashboardSettings?
    // Relationship to IntegrationSettings
    @Relationship(deleteRule: .cascade) var integrationSettings: IntegrationSettings?
    init(from dto: AccountDTO) {
        self.accountId = dto.id
        self.email = dto.email
        self.firstName = dto.firstName
        self.lastName = dto.lastName
        self.gender = dto.gender
        self.zipcode = dto.zipcode
        self.dob = dto.dob
        self.isLoggedIn = nil
        self.isExpired = nil
        self.lastActiveTime = nil
        self.fcmToken = nil
        self.isActiveAccount = nil
        self.accessToken = nil
        self.refreshToken = nil
        self.expiresAt = nil
        self.isSynced = nil
        
        // Create associated WeightCompSettings
        let settings = WeightCompSettings(
            accountId: dto.id,
            height: String(dto.height),
            activityLevel: dto.activityLevel,
            weightUnit: dto.weightUnit
        )
        self.weightSettings = settings
        
        // Create associated GoalSettings
        let goalSettings = GoalSettings(
            accountId: dto.id,
            goalType: dto.goalType,
            initialWeight: dto.initialWeight,
            goalWeight: dto.goalWeight,
            goalPercent: nil,
            isSynced: false
        )
        self.goalSettings = goalSettings
        
        // Create associated StreaksSettings
        let streaksSettings = StreaksSettings(
            accountId: dto.id,
            isStreakOn: dto.isStreakOn ?? false,
            streakTimestamp: dto.streakTimestamp,
            isSynced: false
        )
        self.streaksSettings = streaksSettings
        
        // Create associated WeightlessSettings
        let weightlessSettings = WeightlessSettings(
            accountId: dto.id,
            isWeightlessOn: dto.isWeightlessOn ?? false,
            weightlessTimestamp: dto.weightlessTimestamp,
            weightlessWeight: dto.weightlessWeight != nil ? Double(dto.weightlessWeight!) : nil,
            isSynced: false
        )
        self.weightlessSettings = weightlessSettings
        
        // Create associated NotificationSettings
        let notificationSettings = NotificationSettings(
            accountId: dto.id,
            shouldSendEntryNotifications: dto.shouldSendEntryNotifications ?? true,
            shouldSendWeightInEntryNotifications: dto.shouldSendWeightInEntryNotifications ?? false,
            isSynced: false
        )
        self.notificationSettings = notificationSettings
        
        // Create associated DashboardSettings
        let dashboardSettings = DashboardSettings(
            accountId: dto.id,
            dashboardMetrics: dto.dashboardMetrics?.map { String(describing: $0) }.joined(separator: ","),
            progressMetrics: dto.progressMetrics?.joined(separator: ","),
            dashboardType: dto.dashboardType != nil ? String(describing: dto.dashboardType!) : nil,
            isSynced: false
        )
        self.dashboardSettings = dashboardSettings
        
        // Create associated IntegrationSettings
        let integrationSettings = IntegrationSettings(
            accountId: dto.id,
            isFitbitOn: dto.isFitbitOn ?? false,
            isFitbitValid: dto.isFitbitValid ?? false,
            isHealthConnectOn: dto.isHealthConnectOn ?? false,
            isHealthKitOn: dto.isHealthKitOn ?? false,
            isMfpOn: dto.isMFPOn ?? false,
            isMfpValid: dto.isMFPValid ?? false,
            isSynced: false
        )
        self.integrationSettings = integrationSettings
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
            isWeightlessOn: self.weightlessSettings?.isWeightlessOn,
            height: Double(self.weightSettings?.height ?? "0") ?? 0.0,
            activityLevel: self.weightSettings?.activityLevel,
            dob: self.dob ?? "",
            weightlessTimestamp: self.weightlessSettings?.weightlessTimestamp,
            weightlessWeight: self.weightlessSettings?.weightlessWeight != nil ? Double(self.weightlessSettings!.weightlessWeight!) : nil,
            isStreakOn: self.streaksSettings?.isStreakOn,
            streakTimestamp: self.streaksSettings?.streakTimestamp,
            dashboardType: self.dashboardSettings?.dashboardType.flatMap { DashboardType(rawValue: $0) },
            dashboardMetrics: self.dashboardSettings?.dashboardMetrics?.split(separator: ",").compactMap { BodyMetric(rawValue: String($0)) },
            progressMetrics: self.dashboardSettings?.progressMetrics?.split(separator: ",").map { String($0) },
            goalType: self.goalSettings?.goalType,
            goalWeight: self.goalSettings?.goalWeight.flatMap { Double($0) },
            goalPercent: self.goalSettings?.goalPercent,
            initialWeight: self.goalSettings?.initialWeight,
            shouldSendEntryNotifications: self.notificationSettings?.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: self.notificationSettings?.shouldSendWeightInEntryNotifications,
            isFitbitOn: self.integrationSettings?.isFitbitOn,
            isFitbitValid: self.integrationSettings?.isFitbitValid,
            isMFPOn: self.integrationSettings?.isMfpOn,
            isMFPValid: self.integrationSettings?.isMfpValid,
            isHealthKitOn: self.integrationSettings?.isHealthKitOn,
            isHealthConnectOn: self.integrationSettings?.isHealthConnectOn
        )
    }
}

// MARK: - Update Methods
// swiftlint:disable cyclomatic_complexity
extension Account {
    /// Updates account from AccountDTO response.
    /// This method intentionally has high complexity to handle all account update scenarios in one place for maintainability.
    func update(from response: AccountDTO) {
        self.accountId = response.id
        self.email = response.email
        self.firstName = response.firstName
        self.gender = response.gender
        self.height = String(response.height)
        self.dob = response.dob
        
        if let weightSettings = self.weightSettings {
            weightSettings.height = String(response.height)
            weightSettings.activityLevel = response.activityLevel
            weightSettings.weightUnit = response.weightUnit
        }
        if let lastName = response.lastName {
            self.lastName = lastName
        }
        if let zipcode = response.zipcode {
            self.zipcode = zipcode
        }
            
        // Consolidated weightless settings update logic
        if let isWeightlessOn = response.isWeightlessOn, isWeightlessOn == false {
            // Explicitly turned off: clear weight and timestamp
            self.weightlessSettings?.isWeightlessOn = false
            self.weightlessSettings?.weightlessWeight = nil
            self.weightlessSettings?.weightlessTimestamp = nil
        } else if let weightlessWeight = response.weightlessWeight {
            // Weight provided and not explicitly off: store weight and set state
            self.weightlessSettings?.weightlessWeight = weightlessWeight
            self.weightlessSettings?.isWeightlessOn = response.isWeightlessOn ?? false
            // Set timestamp if provided
            if let weightlessTimestamp = response.weightlessTimestamp {
                self.weightlessSettings?.weightlessTimestamp = weightlessTimestamp
            }
        } else {
            // No weight provided and not explicitly off: default to off
            self.weightlessSettings?.weightlessWeight = nil
            self.weightlessSettings?.isWeightlessOn = false
            self.weightlessSettings?.weightlessTimestamp = nil
        }
        if let isStreakOn = response.isStreakOn {
            self.streaksSettings?.isStreakOn = isStreakOn
        }
        if let streakTimestamp = response.streakTimestamp {
            self.streaksSettings?.streakTimestamp = streakTimestamp
        }
        if let dashboardSettings = self.dashboardSettings {
            if let dashboardMetrics = response.dashboardMetrics {
                dashboardSettings.dashboardMetrics = dashboardMetrics.map { String(describing: $0) }.joined(separator: ",")
            }
            if let progressMetrics = response.progressMetrics {
                dashboardSettings.progressMetrics = progressMetrics.joined(separator: ",")
            }
            if let dashboardType = response.dashboardType {
                dashboardSettings.dashboardType = String(describing: dashboardType)
            }
        }
        if let notificationSettings = self.notificationSettings {
            if let shouldSendEntryNotifications = response.shouldSendEntryNotifications {
                notificationSettings.shouldSendEntryNotifications = shouldSendEntryNotifications
            }
            if let shouldSendWeightInEntryNotifications = response.shouldSendWeightInEntryNotifications {
                notificationSettings.shouldSendWeightInEntryNotifications = shouldSendWeightInEntryNotifications
            }
        }
        if let integrationSettings = self.integrationSettings {
            if let isFitbitOn = response.isFitbitOn { integrationSettings.isFitbitOn = isFitbitOn }
            if let isFitbitValid = response.isFitbitValid { integrationSettings.isFitbitValid = isFitbitValid }
            if let isHealthConnectOn = response.isHealthConnectOn { integrationSettings.isHealthConnectOn = isHealthConnectOn }
            if let isHealthKitOn = response.isHealthKitOn { integrationSettings.isHealthKitOn = isHealthKitOn }
            if let isMFPOn = response.isMFPOn { integrationSettings.isMfpOn = isMFPOn }
            if let isMFPValid = response.isMFPValid { integrationSettings.isMfpValid = isMFPValid }
        }
        if let goalSettings = self.goalSettings {
            goalSettings.goalType = response.goalType
            goalSettings.initialWeight = response.initialWeight
            goalSettings.goalWeight = response.goalWeight
            goalSettings.goalPercent = response.goalPercent
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
    
    // Add a method to update from GoalSettings
    func update(from goalSettings: GoalResponse) {
        self.goalSettings?.goalType = goalSettings.type
        self.goalSettings?.initialWeight = goalSettings.initialWeight
        self.goalSettings?.goalWeight = goalSettings.goalWeight
        self.goalSettings?.isSynced = true
    }
}

// MARK: - Update Methods
// swiftlint:disable cyclomatic_complexity
extension Account {
    // swiftlint:enable cyclomatic_complexity
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension Account: @unchecked Sendable {}
