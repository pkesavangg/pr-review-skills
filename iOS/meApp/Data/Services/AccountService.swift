import Foundation
import Combine

@MainActor
final class AccountService: AccountServiceProtocol, ObservableObject {
    static let shared: AccountService = AccountService()
    @Injector var notificationService: NotificationHelperService
    
    private let apiRepo: AccountRepositoryAPIProtocol = AccountRepositoryAPI()
    private let localRepo: AccountRepositoryProtocol = AccountRepository()
    private let networkMonitor: NetworkMonitor = NetworkMonitor.shared
    /// API repository for integration-related network calls
    private let integrationApiRepo: IntegrationRepositoryAPIProtocol = IntegrationAPIRepository()
    
    @Published var activeAccount: Account? = nil
    @Published var allAccounts: [Account] = []
    
    var alertLang =  AlertStrings.self.ExpiredUserLogOutAlert
    var cancellables = Set<AnyCancellable>()
    
    init() {
        // Load initial accounts from local storage
        Task {
            do {
                try await syncUnsyncedAccounts() // Try to sync any offline changes
                try await updatePublishedState()
                let _ = try await refreshAllAccounts()
            } catch {
                
            }
            $activeAccount
                .sink(receiveValue: { data in
                    if data == nil {
                        ServiceRegistry.shared.deregisterSessionServices()
                    } else {
                        ServiceRegistry.shared.registerSessionServices()
                    }
                })
                .store(in: &cancellables)
            try await updatePublishedState()
        }
    }
    
    // MARK: - Account Lifecycle
    
    /// Signs up a new account with the provided email, password, and profile.
    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        do {
            // Check if maximum accounts reached
            try await checkIfMaxAccountsReached(email: email)
            let response = try await apiRepo.createAccount(email: email, password: password, profile: profile)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isLoggedIn = true // New account is logged in by default
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
            try await makeOtherAccountsInactive(except: account)
            try await localRepo.saveAccount(account)
            try await updatePublishedState()
            return account
        } catch {
            throw error // No offline fallback for signup
        }
    }
    
    /// Logs in an existing account with the provided email and password.
    func logIn(email: String, password: String) async throws -> Account {
        do {
            // Check if maximum accounts reached
            try await checkIfMaxAccountsReached(email: email)
            let response = try await apiRepo.logIn(email: email, password: password)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isLoggedIn = true // New account is logged in by default
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
            try await makeOtherAccountsInactive(except: account)
            try await localRepo.saveAccount(account)
            try await updatePublishedState()
            try await refreshAccount()
            return account
        } catch {
            throw error // No offline fallback for login
        }
    }
    
    /// Logs out the current active account or a specific account by ID.
    func logOut(accountId: String? = nil, isAutoLogout: Bool = false) async throws {
        // Always try API, fallback to local only if network error
        // if accountId is nil, use current logged in account
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        
        // Helper to perform the actual logout work (API + local updates)
        let performLogout: @Sendable () async throws -> Void = { [weak self] in
            guard let self else { return }
            try await self.executeLogout(on: localAccount, isAutoLogout: isAutoLogout)
        }
        
        // Notify observers **before** performing the local logout so UI can react.
        if isAutoLogout, localAccount.isActiveAccount == true {
            let userName = "\(localAccount.firstName ?? "")"
            let alert = AlertModel(
                title: alertLang.title(userName),
                message: alertLang.message,
                buttons: [
                    AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in
                        Task { try? await performLogout() }
                    }
                ]
            )
            notificationService.showAlert(alert)
        } else {
            try await performLogout()
        }
    }
    
    /// Deletes the current active account or a specific account by ID.
    func deleteAccount() async throws {
        // If no active account, throw error
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        do {
            try await apiRepo.deleteAccount(accountId: accountId)
            try await localRepo.deleteAccount(byId: accountId)
            try await updatePublishedState()
        } catch {
            throw error // Handle any errors that occur during deletion
        }
    }
    
    // MARK: - Account Switching
    /// Switches to a different account by setting it as the active account.
    func switchAccount(to account: Account) async throws {
        // Check network connectivity before switching
        guard networkMonitor.isConnected else {
            throw HTTPError.noInternet
        }
        do {
            let responseAccount = try await refreshAccount(accountId: account.accountId)
            try await setActiveAccount(responseAccount)
        } catch {
            throw error
        }
    }
    
    /// Sets the specified account as the active account and makes other accounts inactive.
    func setActiveAccount(_ account: Account) async throws {
        account.isActiveAccount = true
        account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
        try await makeOtherAccountsInactive(except: account)
        try await localRepo.updateAccount(account)
        try await updatePublishedState()
    }
    
    // MARK: - Account State
    /// Returns the currently active account, updating the published state first.
    func getActiveAccount() async throws -> Account? {
        try await updatePublishedState()
        return activeAccount
    }
    
    /// Returns all logged-in accounts, filtering out inactive or expired accounts.
    func getAllLoggedInAccounts() async throws -> [Account] {
        let all = try await localRepo.fetchAllAccounts()
        return all.filter { $0.isLoggedIn == true }
    }
    
    /// Fetches an account by its unique ID.
    func fetchAccount(byId id: String) async throws -> Account? {
        return try await localRepo.fetchAccount(byId: id)
    }
    
    /// Fetches all accounts stored locally.
    func fetchAllAccounts() async throws -> [Account] {
        return try await localRepo.fetchAllAccounts()
    }
    
    // MARK: - Account Updates
    /// Updates the active account with the provided updated account data.
    func updateAccount(_ updatedAccount: Account) async throws -> Account {
        guard let localAccount = try await localRepo.fetchAccount(byId: updatedAccount.accountId) else {
            throw AccountError.accountNotFound(id: updatedAccount.accountId)
        }
        
        do {
            let response = try await apiRepo.editAccount(updatedAccount)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.update(from: updatedAccount.toAccountDTO())
                localAccount.isSynced = false
                try await localRepo.updateAccount(updatedAccount)
                try await updatePublishedState()
                return updatedAccount
            }
            throw error
        }
    }
    
    func createGoal(_ goal: Goal) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            let response = try await apiRepo.createGoal(goal)
            localAccount.update(from: response)
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.goalSettings?.goalType = goal.type
                localAccount.goalSettings?.goalWeight = Double(goal.goalWeight)
                localAccount.goalSettings?.isSynced = false
                localAccount.goalSettings?.initialWeight = Double(goal.initialWeight)
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
            }
            throw error
        }
    }
    
    /// Updates the profile of the active account with the provided profile data.
    @discardableResult
    func updateProfile(_ profile: Profile, canSaveOffline: Bool = false) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            let response = try await apiRepo.patchProfile(profile)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if canSaveOffline && HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.update(from: profile)
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            }
            throw error
        }
    }
    
    /// Updates the body composition of the active account with the provided bodyComp data.
    @discardableResult
    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            let response = try await apiRepo.patchBodyComp(bodyComp)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.weightSettings?.weightUnit = bodyComp.weightUnit
                localAccount.weightSettings?.height = String(bodyComp.height)
                localAccount.weightSettings?.activityLevel = bodyComp.activityLevel
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    /// Updates the tokens for the active account or a specific account by ID.
    func updateTokens( _ tokens: Tokens, _ accountId: String? = nil) async throws {
        // Update tokens for the active account if accountId is nil
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        
        localAccount.update(from: tokens)
        try await localRepo.updateAccount(localAccount)
        try await updatePublishedState()
    }
    
    /// Updates the dashboard type for the active account or a specific account by ID.
    @discardableResult
    func updateDashboardType(type: DashboardType) async throws -> Account {
        // use current logged in account
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else { throw AccountError.accountNotFound(id: accountId) }
        do {
            localAccount.dashboardSettings?.dashboardType = String(describing: type)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    // MARK: - Integrations (HealthKit)
    
    /// Creates (or updates) an Apple Health (HealthKit) integration for the active account.
    ///
    /// This wraps the `POST /integrations/health` endpoint (see `IntegrationAPIRepository.createHealthIntegration`).
    /// For the initial implementation we forward an **empty** preferences dictionary.
    /// - Parameter deviceId: The HealthKit pseudo-device identifier.
    /// - Returns: The `HealthIntegrationResponse` returned by the backend.
    @discardableResult
    func updateIntegrations(integrationType: IntegrationType, preferences: [String: AnyCodable] = [:]) async throws -> Account {
        guard let accountId = activeAccount?.accountId,
              let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        let deviceId = DeviceInfoHelper.getDeviceId()
        do {
            let _ = try await integrationApiRepo.createHealthIntegration(
                deviceId: deviceId,
                type: integrationType,
                preferences: preferences
            )
            
            if localAccount.integrationSettings == nil {
                localAccount.integrationSettings = IntegrationSettings(
                    accountId: accountId,
                    isHealthKitOn: true,
                    isSynced: true
                )
            } else {
                localAccount.integrationSettings?.isHealthKitOn = true
            }
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                if localAccount.integrationSettings == nil {
                    localAccount.integrationSettings = IntegrationSettings(
                        accountId: accountId,
                        isHealthKitOn: true,
                        isSynced: false
                    )
                } else {
                    localAccount.integrationSettings?.isHealthKitOn = true
                    localAccount.isSynced = false
                }
                try? await localRepo.updateAccount(localAccount)
                try? await updatePublishedState()
                return localAccount
            }
            throw error
        }
    }
    
    /// Updates the notification settings for the active account or a specific account by ID.
    @discardableResult
    func updateNotifications(notifications: Notifications) async throws -> Account {
        // use current logged in account
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else { throw AccountError.accountNotFound(id: accountId) }
        do {
            let response = try await apiRepo.patchNotification(notifications)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.notificationSettings?.shouldSendEntryNotifications = notifications.shouldSendEntryNotifications
                localAccount.notificationSettings?.shouldSendWeightInEntryNotifications = notifications.shouldSendWeightInEntryNotifications
                localAccount.isSynced = false
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    // MARK: - Password & Security
    /// Requests a password reset for the specified email.
    func requestPasswordReset(email: String) async throws {
        try await apiRepo.requestPasswordReset(email: email)
    }
    
    /// Updates the password for the active account or a specific account by ID.
    func updatePassword(oldPassword: String, newPassword: String) async throws {
        do {
            let tokens = try await apiRepo.updatePassword(oldPassword: oldPassword, newPassword: newPassword)
            try await self.updateTokens(tokens)
        } catch {
            throw error
        }
    }
    
    // MARK: - Sync & Offline
    /// Refreshes all accounts by fetching from API and updating local storage.
    func refreshAllAccounts() async throws {
        let accounts = try await localRepo.fetchAllAccounts()
        for account in accounts {
            do {
                _ = try await refreshAccount(accountId: account.accountId)
            } catch {
                if HTTPError.isNetworkError(error) {
                    continue
                } else {
                    do {
                        // Mark account as expired for error
                        if let expiredAccount = try await localRepo.fetchAccount(byId: account.accountId) {
                            expiredAccount.isExpired = true
                            try await localRepo.updateAccount(expiredAccount)
                        }
                    } catch {
                        // Ignore errors during logout
                        continue
                    }
                }
            }
        }
        try await updatePublishedState()
    }
    
    /// Refreshes a specific account by fetching from API and updating local storage.
    @discardableResult
    func refreshAccount(accountId: String? = nil) async throws -> Account {
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        
        do {
            // Try to fetch from API
            let dto = try await apiRepo.fetchAccount(accountId: localAccount.accountId)
            localAccount.update(from: dto)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                // On network error, return local account
                return localAccount
            }
            throw error
        }
    }
    
    func clearOfflineData(for account: Account) async throws {
        throw AccountError.notImplemented
    }
    
    /// Deletes all accounts locally, logging out each account first.
    func logOutAllAccounts() async throws {
        do {
            let allAccounts = try await localRepo.fetchAllAccounts()
            for account in allAccounts {
                do {
                    try await logOut(accountId: account.accountId)
                } catch  {
                    continue // Ignore errors during logout
                }
            }
        } catch {}
        try await self.updatePublishedState()
    }
    
    /// Synchronizes any unsynced accounts with the server.
    func syncUnsyncedAccounts() async throws {
        // Ensure we have connectivity before attempting to sync
        guard networkMonitor.isConnected else { return }
        
        // Always fetch the account flagged as active from local storage. This avoids
        // relying on a potentially stale `activeAccount` reference.
        guard let localAccount = try await localRepo.fetchAllAccounts()
            .first(where: { $0.isActiveAccount == true }) else {
            return // No active account found locally – nothing to sync
        }
        
        // Make an independent snapshot so subsequent mutations on `localAccount`
        // (triggered by network responses) **do not** mutate the values we are
        // about to evaluate for unsynced-state detection.
        let account = Account(from: localAccount.toAccountDTO())
        
        // Keep the published `activeAccount` in sync with the freshly-fetched model
        self.activeAccount = localAccount
        let isSynced =  localAccount.isSynced ?? true
        do {
            // Handle Profile updates
            if let firstName = account.firstName,
               let gender = account.gender,
               let zipcode = account.zipcode,
               let dob = account.dob,
               let weightUnit = account.weightSettings?.weightUnit,
               let height = Double(account.weightSettings?.height ?? "0"),
               let activityLevel = account.weightSettings?.activityLevel,
               !isSynced {
                let profile = Profile(
                    firstName: firstName,
                    lastName: account.lastName ?? "",
                    email: account.email,
                    gender: gender,
                    zipcode: zipcode,
                    dob: dob,
                    weightUnit: weightUnit,
                    height: height,
                    activityLevel: activityLevel
                )
                try await updateProfile(profile)
            }
            // Handle Body Composition updates
            if let weightUnit = account.weightSettings?.weightUnit,
               let height = Double(account.weightSettings?.height ?? "0"),
               let activityLevel = account.weightSettings?.activityLevel,
               !isSynced {
                let bodyComp = BodyComp(
                    weightUnit: weightUnit,
                    height: height,
                    activityLevel: activityLevel
                )
                try await updateBodyComp(bodyComp)
            }
            // Handle Notification Settings
            if let shouldSendEntry = account.notificationSettings?.shouldSendEntryNotifications,
               let shouldSendWeightIn = account.notificationSettings?.shouldSendWeightInEntryNotifications,
               !isSynced {
                let notifications = Notifications(
                    shouldSendEntryNotifications: shouldSendEntry,
                    shouldSendWeightInEntryNotifications: shouldSendWeightIn
                )
                try await updateNotifications(notifications: notifications)
            }
            
            // Handle Dashboard Type
            if let dashboardType = account.dashboardSettings?.dashboardType,
               !isSynced {
                try await updateDashboardType(type: DashboardType(rawValue: dashboardType) ?? .dashboard4)
            }
            
            // Handle Dashboard Metrics
            if let metricsString = account.dashboardSettings?.dashboardMetrics,
               !isSynced {
                let metrics = metricsString.split(separator: ",").map(String.init)
                try await updateDashboardMetrics(metrics: metrics)
            }
            
            // Handle Streak Status
            if let isStreakOn = account.streaksSettings?.isStreakOn,
               let streakTimestamp = account.streaksSettings?.streakTimestamp,
               !isSynced {
                try await updateStreak(isStreakOn: isStreakOn, streakTimestamp: streakTimestamp)
            }
            
            // Handle Weightless Mode
            if let isWeightlessOn = account.weightlessSettings?.isWeightlessOn,
               let weightlessTimestamp = account.weightlessSettings?.weightlessTimestamp,
               let weightlessWeight = account.weightlessSettings?.weightlessWeight,
               !isSynced {
                try await updateWeightless(isWeightlessOn: isWeightlessOn, weightlessTimestamp: weightlessTimestamp, weightlessWeight: Double(weightlessWeight))
            }
            // Handle Integration Settings
            if let integrationSettings = account.integrationSettings,
               !isSynced {
                // • Apple Health (HealthKit)
                if integrationSettings.isHealthKitOn {
                    // Fire-and-forget; the helper already updates the local store & published state.
                    _ = try await updateIntegrations(
                        integrationType: .healthKit
                    )
                } else {
                    // If HealthKit is off, ensure it's marked as unsynced
                    _ = try await deleteHealthIntegration(.healthKit)
                }
            }
            
            // Mark **local** account (the one in persistence) as synced
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            
        } catch {
            if !HTTPError.isNetworkError(error) {
                throw error
            }
            // If it's a network error, keep the account marked as unsynced
        }
    }
    
    // MARK: - Delete Health Integration
    /// Deletes the health integration for the active account or a specific account by ID.
    /// - Parameter type: The type of integration to delete (e.g., HealthKit).
    /// - Throws: An error if the deletion fails or if the account is not found.
    /// - Returns: The updated account after deletion.
    func deleteHealthIntegration(_ type: IntegrationType) async throws {
        let deviceId = DeviceInfoHelper.getDeviceId()
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        do {
            try await integrationApiRepo.deleteHealthIntegration(deviceId: deviceId)
            try await refreshAccount()
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
        } catch {
            if HTTPError.isNetworkError(error) {
                if type == .healthKit {
                    localAccount.integrationSettings?.isHealthKitOn = false
                    localAccount.isSynced = false
                }
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
            }
            throw error
        }
    }
    
    /// Updates the dashboard metrics for the active account or a specific account by ID.
    @discardableResult
    func updateDashboardMetrics(metrics: [String]) async throws -> Account {
        // use current logged in account
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else { throw AccountError.accountNotFound(id: accountId) }
        do {
            let response = try await apiRepo.patchDashboardMetrics(metrics)
            localAccount.update(from: response)
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.dashboardSettings?.dashboardMetrics = metrics.joined(separator: ",")
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    /// Updates the streak status for the active account or a specific account by ID.
    @discardableResult
    func updateStreak(isStreakOn: Bool, streakTimestamp: String) async throws -> Account {
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        do {
            let response = try await apiRepo.patchStreak(isStreakOn, streakTimestamp)
            localAccount.update(from: response)
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                if let streaksSettings = localAccount.streaksSettings {
                    streaksSettings.isStreakOn = isStreakOn
                    streaksSettings.streakTimestamp = streakTimestamp
                    localAccount.streaksSettings = streaksSettings
                } else {
                    localAccount.streaksSettings = StreaksSettings(
                        accountId: localAccount.accountId,
                        isStreakOn: isStreakOn,
                        streakTimestamp: streakTimestamp,
                        isSynced: false
                    )
                }
                localAccount.isSynced = false
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    /// Updates the weightless mode for the active account or a specific account by ID.
    @discardableResult
    func updateWeightless(isWeightlessOn: Bool, weightlessTimestamp: String, weightlessWeight: Double) async throws -> Account {
        // use current logged in account
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else { throw AccountError.accountNotFound(id: accountId)
        }
        do {
            let response = try await apiRepo.patchWeightless(isWeightlessOn, weightlessTimestamp, Int(weightlessWeight))
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await localRepo.updateAccount(localAccount)
            try await updatePublishedState()
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                try await localRepo.updateAccount(localAccount)
                try await updatePublishedState()
                return localAccount
            } else {
                throw error
            }
        }
    }
    
    // MARK: - Token Management
    /// Refreshes the access token using the refresh token of the active account or a specific account by ID.
    func refreshTokens(accountId: String? = nil) async throws -> Tokens {
        let account = accountId != nil ?
        try await fetchAccount(byId: accountId!) :
        activeAccount
        
        guard let account = account,
              let refreshToken = account.refreshToken else {
            throw AccountError.noActiveAccount
        }
        
        return try await apiRepo.refreshToken(
            refreshToken: refreshToken,
            accountId: account.accountId
        )
    }
    
    /// Gets the active tokens (access and refresh) for the current active account.
    func getActiveTokens() async throws -> Tokens {
        guard let account = activeAccount else {
            throw AccountError.noActiveAccount
        }
        return Tokens(
            accessToken: account.accessToken ?? "",
            refreshToken: account.refreshToken ?? "",
            expiresAt: account.expiresAt ?? ""
        )
    }
    
    /// Updates the published state of active and all accounts.
    func updatePublishedState() async throws {
        allAccounts = try await localRepo.fetchAllAccounts()
        activeAccount = allAccounts.first(where: { $0.isActiveAccount == true})
    }
    
    // MARK: - Private Helpers
    /// Deletes the account locally by ID and updates the published state.
    private func deleteAccountLocally(accountId: String) async throws {
        do {
            // delete the account from local storage
            try await localRepo.deleteAccount(byId: accountId)
            try await updatePublishedState()
        } catch {
            throw error
        }
    }
    
    /// Checks whether the maximum number of allowed accounts has been reached.
    /// If the limit is exceeded and the email does not match any existing logged-in account,
    /// it throws a `maxAccountsReached` error to prevent adding a new account.
    private func checkIfMaxAccountsReached(email: String) async throws {
        if try await hasReachedMaxAccounts() {
            allAccounts = try await localRepo.fetchAllAccounts().filter { $0.isLoggedIn == true }
            if !(allAccounts.contains { $0.email == email } ) {
                throw AccountError.maxAccountsReached
            }
        }
    }
    
    /// Checks if the maximum number of accounts has been reached.
    private func hasReachedMaxAccounts() async throws -> Bool {
        let count = try await getAccountCount()
        return count >= AppConstants.Account.maxAccounts
    }
    
    /// Gets the count of all accounts stored locally.
    private func getAccountCount() async throws -> Int {
        let accounts = try await localRepo.fetchAllAccounts()
            .filter { $0.isLoggedIn == true }
        return accounts.count
    }
    
    /// Makes all accounts inactive except the specified account.
    private func makeOtherAccountsInactive(except account: Account) async throws {
        let allAccounts = try await localRepo.fetchAllAccounts()
        for acc in allAccounts where acc.accountId != account.accountId {
            acc.isActiveAccount = false
            try await localRepo.updateAccount(acc)
        }
    }
    
    // MARK: - Private Helpers
    /// Performs the actual logout logic: API call (ignored if it fails) + local flag updates + state refresh.
    private func executeLogout(on localAccount: Account, isAutoLogout: Bool) async throws {
        do {
            try await apiRepo.logOut(fcmToken: localAccount.fcmToken, accountId: localAccount.accountId)
        } catch {
            // Ignore API errors during logout
        }
        
        do {
            // Logout the account locally (happens regardless of API success/failure)
            localAccount.isLoggedIn = (localAccount.isLoggedIn ?? false) ? isAutoLogout : false
            localAccount.isActiveAccount = false
            localAccount.isExpired = isAutoLogout
            try await localRepo.updateAccount(localAccount)
        } catch {
            // Ignore local persistence errors during logout
        }
        
        // Attempt to refresh published state; propagate any errors to the caller
        try await updatePublishedState()
    }
    
    deinit {
      cancellables.forEach { $0.cancel() }
      cancellables.removeAll()
    }
}
