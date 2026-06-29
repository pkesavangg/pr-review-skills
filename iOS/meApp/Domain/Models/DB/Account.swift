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
/// | accessToken     | string? | Keychain is source of truth; persisted col cleared on save (5.0.3 migration only) |
/// | refreshToken    | string? | Keychain is source of truth; persisted col cleared on save (5.0.3 migration only) |
/// | expiresAt       | string? | Keychain is source of truth; persisted col cleared on save (5.0.3 migration only) |
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
    /// OAuth or app-specific access token. Keychain is the source of truth; this column is
    /// persisted (not `@Transient`) only so the one-time `migrateTokensToKeychainIfNeeded()`
    /// pass can read the value an upgrading 5.0.3 store still holds. It is cleared to `nil`
    /// before every save (see `clearTokenFieldsBeforeSave`), so it never re-persists a token.
    var accessToken: String?
    /// OAuth refresh token. Persisted only for the one-time 5.0.3 → Keychain migration; cleared on save.
    var refreshToken: String?
    /// Access token expiration time. Persisted only for the one-time 5.0.3 → Keychain migration; cleared on save.
    var expiresAt: String?
    /// Firebase Cloud Messaging token
    var fcmToken: String?
    /// Timestamp of last activity
    var lastActiveTime: String?
    /// Whether account is updated and synced online
    var isSynced: Bool?
    /// Product types the user has selected (e.g. "myWeight", "myBloodPressure", "baby").
    /// Defaults to `["myWeight"]` so SwiftData lightweight migration can infer this
    /// non-optional column when upgrading from 5.0.3 (where it did not exist) and so
    /// existing weight-only accounts back-fill to weight rather than an empty selection.
    var productTypes: [String] = ["myWeight"]
    /// Preferred measurement units ("metric", "imperialLbOz", "imperialLbDecimal").
    /// Sourced from the server `measurementUnits` field; nil until set.
    var measurementUnits: String?

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
    // swiftlint:disable:next function_body_length
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
        self.productTypes = dto.productTypes ?? []
        self.measurementUnits = dto.measurementUnits

        // Create associated WeightCompSettings
        let settings = WeightCompSettings(
            accountId: dto.id,
            height: dto.height.map { String($0) },
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
            weightlessWeight: dto.weightlessWeight.flatMap { Double($0) },
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
            dashboardType: dto.dashboardType.map { String(describing: $0) },
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
            weightlessWeight: self.weightlessSettings?.weightlessWeight.flatMap { Double($0) },
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
            isHealthConnectOn: self.integrationSettings?.isHealthConnectOn,
            productTypes: self.productTypes,
            measurementUnits: self.measurementUnits
        )
    }

    /// SwiftData lifecycle hook — defense-in-depth for the Keychain-only token invariant.
    ///
    /// `accessToken`/`refreshToken`/`expiresAt` are persisted columns purely so the one-time
    /// `migrateTokensToKeychainIfNeeded()` pass can read tokens an upgrading 5.0.3 store still
    /// holds. Keychain is the source of truth; a token must never be (re)written to the unencrypted
    /// on-disk store. `AccountService` clears these via `clearTokenFieldsBeforeSave` before its
    /// save/update wrappers, but `AccountRepository.activateAccount`/`mergeAccount` (and any future
    /// caller) call `context.save()` directly. Clearing here guarantees the invariant holds on
    /// *every* persist path rather than relying on each writer remembering to use the wrapper.
    ///
    /// The migration reads tokens from the freshly fetched (unsaved) object before this fires, so
    /// the one-time copy to Keychain is unaffected. Each field is nil-guarded so this never re-marks
    /// the model dirty, avoiding repeated `willSave()` invocations.
    func willSave() {
        if accessToken != nil { accessToken = nil }
        if refreshToken != nil { refreshToken = nil }
        if expiresAt != nil { expiresAt = nil }
    }
}

// MARK: - Update Methods
extension Account {
    /// Updates account from AccountDTO response.
    /// This method intentionally has high complexity to handle all account update scenarios in one place for maintainability.
    func update(from response: AccountDTO) { // swiftlint:disable:this cyclomatic_complexity function_body_length
        self.accountId = response.id
        self.email = response.email
        self.firstName = response.firstName
        self.gender = response.gender
        self.height = response.height.map { String($0) }
        self.dob = response.dob

        // Only populate productTypes from server when the local value is unset.
        // updateProductTypes() has no server API call, so productTypes are client-managed.
        // A server refresh returning the account's default (e.g. ["myWeight"]) must not
        // overwrite a signup selection already written locally (e.g. ["baby"]).
        if let productTypes = response.productTypes, self.productTypes.isEmpty {
            self.productTypes = productTypes
        }
        if let measurementUnits = response.measurementUnits {
            self.measurementUnits = measurementUnits
        }

        if let weightSettings = self.weightSettings {
            weightSettings.height = response.height.map { String($0) }
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

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
