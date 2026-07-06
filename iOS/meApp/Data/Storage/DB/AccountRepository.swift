import Foundation
import SwiftData

/// Concrete implementation of AccountRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Account entities in a thread-safe manner.
@MainActor
final class AccountRepository: AccountRepositoryProtocol {
    // MARK: - Properties
    private let context: ModelContext

    init(context: ModelContext? = nil) {
        self.context = context ?? PersistenceController.shared.context
    }

    /// Fetches an account by its unique ID.
    /// - Parameter id: The ID of the account to fetch.
    /// - Returns: The Account object, or nil if not found.
    func fetchAccount(byId id: String) async throws -> Account? {
        try findAccount(byId: id)
    }

    /// Fetches all accounts stored locally.
    /// - Returns: An array of all Account objects.
    func fetchAllAccounts() async throws -> [Account] {
        try loadAllAccounts()
    }

    /// Saves a new account to the local data store.
    /// - Parameter account: The Account object to save.
    func saveAccount(_ account: Account) async throws {
        if let existing = try findAccount(byId: account.accountId), existing !== account {
            mergeAccount(from: account, into: existing)
        } else if try findAccount(byId: account.accountId) == nil {
            context.insert(account)
        }

        let duplicates = try fetchAccounts(byEmail: account.email)
            .filter { $0.accountId != account.accountId }
        for duplicate in duplicates {
            context.delete(duplicate)
        }

        try saveClearingTokens()
    }

    /// Updates an existing account in the local data store.
    /// - Parameter account: The updated Account object.
    func updateAccount(_ account: Account) async throws {
        if let existing = try findAccount(byId: account.accountId) {
            if existing !== account {
                mergeAccount(from: account, into: existing)
            }
            try saveClearingTokens()
            return
        }

        try await saveAccount(account)
    }

    /// Fetches the currently active account.
    /// - Returns: The active Account object, or nil if no active account exists.
    func fetchActiveAccount() async throws -> Account? {
        let descriptor = FetchDescriptor<Account>(predicate: #Predicate { $0.isActiveAccount == true })
        return try context.fetch(descriptor).first
    }

    /// Fetches all accounts marked as logged in.
    /// - Returns: All logged-in accounts.
    func fetchLoggedInAccounts() async throws -> [Account] {
        let descriptor = FetchDescriptor<Account>(predicate: #Predicate { $0.isLoggedIn == true })
        return try context.fetch(descriptor)
    }

    /// Marks one account as active and clears the flag on all others.
    /// - Parameters:
    ///   - id: The account ID to activate.
    ///   - lastActiveTime: Optional timestamp to persist on the activated account.
    func activateAccount(withId id: String, lastActiveTime: String? = nil) async throws {
        let allAccounts = try loadAllAccounts()
        var didFindAccount = false

        for account in allAccounts {
            let isTarget = account.accountId == id
            if isTarget {
                didFindAccount = true
                account.isActiveAccount = true
                if let lastActiveTime {
                    account.lastActiveTime = lastActiveTime
                }
            } else if account.isActiveAccount == true {
                account.isActiveAccount = false
            }
        }

        guard didFindAccount else {
            throw AccountError.accountNotFound(id: id)
        }

        try saveClearingTokens()
    }

    /// Deletes an account by its unique ID.
    /// - Parameter id: The ID of the account to delete.
    func deleteAccount(byId id: String) async throws {
        if let account = try await fetchAccount(byId: id) {
            context.delete(account)
            try context.save()
        }
    }

    /// Deletes all accounts from the local data store.
    func deleteAllAccounts() async throws {
        let all = try await fetchAllAccounts()
        for account in all {
            context.delete(account)
        }
        try context.save()
    }
    
    /// Synchronously fetches all accounts for early initialization (use sparingly)
    /// - Returns: An array of all Account objects.
    func fetchAllAccountsSync() throws -> [Account] {
        let descriptor = FetchDescriptor<Account>()
        return try context.fetch(descriptor)
    }

    /// Saves the context after scrubbing auth-token columns from every account it holds.
    ///
    /// This is the real enforcement of the Keychain-only token invariant on the repository's
    /// direct-save paths. `Account.willSave()` cannot enforce it: SwiftData computes the pending
    /// write *before* calling `willSave()`, so a model nil-ing its own token columns there is not
    /// folded into that same save (verified against an on-disk store — the token still landed on
    /// disk). Clearing explicitly *before* `save()` makes the nils part of the write, matching what
    /// `AccountService.clearTokenFieldsBeforeSave` already does on its wrapper paths. The context
    /// query reflects pending inserts, so freshly inserted accounts are covered too.
    private func saveClearingTokens() throws {
        for account in try loadAllAccounts() {
            if account.accessToken != nil { account.accessToken = nil }
            if account.refreshToken != nil { account.refreshToken = nil }
            if account.expiresAt != nil { account.expiresAt = nil }
        }
        try context.save()
    }

    private func findAccount(byId id: String) throws -> Account? {
        let descriptor = FetchDescriptor<Account>(predicate: #Predicate { $0.accountId == id })
        return try context.fetch(descriptor).first
    }

    private func loadAllAccounts() throws -> [Account] {
        let descriptor = FetchDescriptor<Account>()
        return try context.fetch(descriptor)
    }

    private func fetchAccounts(byEmail email: String) throws -> [Account] {
        let descriptor = FetchDescriptor<Account>(predicate: #Predicate { $0.email == email })
        return try context.fetch(descriptor)
    }

    private func mergeAccount(from source: Account, into target: Account) {
        target.accountId = source.accountId
        target.email = source.email
        target.firstName = source.firstName
        target.lastName = source.lastName
        target.gender = source.gender
        target.height = source.height
        target.dob = source.dob
        target.zipcode = source.zipcode
        target.isLoggedIn = source.isLoggedIn
        target.isExpired = source.isExpired
        target.isActiveAccount = source.isActiveAccount
        target.accessToken = source.accessToken
        target.refreshToken = source.refreshToken
        target.expiresAt = source.expiresAt
        target.fcmToken = source.fcmToken
        target.lastActiveTime = source.lastActiveTime
        target.isSynced = source.isSynced

        mergeWeightSettings(from: source.weightSettings, into: target)
        mergeGoalSettings(from: source.goalSettings, into: target)
        mergeStreaksSettings(from: source.streaksSettings, into: target)
        mergeWeightlessSettings(from: source.weightlessSettings, into: target)
        mergeNotificationSettings(from: source.notificationSettings, into: target)
        mergeDashboardSettings(from: source.dashboardSettings, into: target)
        mergeIntegrationSettings(from: source.integrationSettings, into: target)
    }

    private func mergeWeightSettings(from source: WeightCompSettings?, into account: Account) {
        guard let source else {
            account.weightSettings = nil
            return
        }

        let target = account.weightSettings ?? WeightCompSettings(
            accountId: account.accountId,
            height: source.height,
            activityLevel: source.activityLevel,
            weightUnit: source.weightUnit
        )
        target.accountId = account.accountId
        target.height = source.height
        target.activityLevel = source.activityLevel
        target.weightUnit = source.weightUnit
        target.isSynced = source.isSynced
        account.weightSettings = target
    }

    private func mergeGoalSettings(from source: GoalSettings?, into account: Account) {
        guard let source else {
            account.goalSettings = nil
            return
        }

        let target = account.goalSettings ?? GoalSettings(
            accountId: account.accountId,
            goalType: source.goalType,
            initialWeight: source.initialWeight,
            goalWeight: source.goalWeight,
            goalPercent: source.goalPercent,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.goalType = source.goalType
        target.initialWeight = source.initialWeight
        target.goalWeight = source.goalWeight
        target.goalPercent = source.goalPercent
        target.isSynced = source.isSynced
        account.goalSettings = target
    }

    private func mergeStreaksSettings(from source: StreaksSettings?, into account: Account) {
        guard let source else {
            account.streaksSettings = nil
            return
        }

        let target = account.streaksSettings ?? StreaksSettings(
            accountId: account.accountId,
            isStreakOn: source.isStreakOn,
            streakTimestamp: source.streakTimestamp,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.isStreakOn = source.isStreakOn
        target.streakTimestamp = source.streakTimestamp
        target.isSynced = source.isSynced
        account.streaksSettings = target
    }

    private func mergeWeightlessSettings(from source: WeightlessSettings?, into account: Account) {
        guard let source else {
            account.weightlessSettings = nil
            return
        }

        let target = account.weightlessSettings ?? WeightlessSettings(
            accountId: account.accountId,
            isWeightlessOn: source.isWeightlessOn,
            weightlessTimestamp: source.weightlessTimestamp,
            weightlessWeight: source.weightlessWeight,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.isWeightlessOn = source.isWeightlessOn
        target.weightlessTimestamp = source.weightlessTimestamp
        target.weightlessWeight = source.weightlessWeight
        target.isSynced = source.isSynced
        account.weightlessSettings = target
    }

    private func mergeNotificationSettings(from source: NotificationSettings?, into account: Account) {
        guard let source else {
            account.notificationSettings = nil
            return
        }

        let target = account.notificationSettings ?? NotificationSettings(
            accountId: account.accountId,
            shouldSendEntryNotifications: source.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: source.shouldSendWeightInEntryNotifications,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.shouldSendEntryNotifications = source.shouldSendEntryNotifications
        target.shouldSendWeightInEntryNotifications = source.shouldSendWeightInEntryNotifications
        target.isSynced = source.isSynced
        account.notificationSettings = target
    }

    private func mergeDashboardSettings(from source: DashboardSettings?, into account: Account) {
        guard let source else {
            account.dashboardSettings = nil
            return
        }

        let target = account.dashboardSettings ?? DashboardSettings(
            accountId: account.accountId,
            dashboardMetrics: source.dashboardMetrics,
            progressMetrics: source.progressMetrics,
            dashboardType: source.dashboardType,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.dashboardMetrics = source.dashboardMetrics
        target.progressMetrics = source.progressMetrics
        target.dashboardType = source.dashboardType
        target.isSynced = source.isSynced
        account.dashboardSettings = target
    }

    private func mergeIntegrationSettings(from source: IntegrationSettings?, into account: Account) {
        guard let source else {
            account.integrationSettings = nil
            return
        }

        let target = account.integrationSettings ?? IntegrationSettings(
            accountId: account.accountId,
            isFitbitOn: source.isFitbitOn,
            isFitbitValid: source.isFitbitValid,
            isHealthConnectOn: source.isHealthConnectOn,
            isHealthKitOn: source.isHealthKitOn,
            isMfpOn: source.isMfpOn,
            isMfpValid: source.isMfpValid,
            isSynced: source.isSynced
        )
        target.accountId = account.accountId
        target.isFitbitOn = source.isFitbitOn
        target.isFitbitValid = source.isFitbitValid
        target.isHealthConnectOn = source.isHealthConnectOn
        target.isHealthKitOn = source.isHealthKitOn
        target.isMfpOn = source.isMfpOn
        target.isMfpValid = source.isMfpValid
        target.isSynced = source.isSynced
        account.integrationSettings = target
    }
} 
