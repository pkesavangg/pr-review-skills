import Foundation
import Combine

@MainActor
final class AccountService: AccountServiceProtocol, ObservableObject {
    static let shared: AccountService = AccountService()
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerService
    @Injector var bluetoothService: BluetoothService
    @Injector var keychainService: KeychainService
    @Injector var kvStorage: KvStorageService

    private let apiRepo: AccountRepositoryAPIProtocol = AccountRepositoryAPI()
    private let localRepo: AccountRepositoryProtocol = AccountRepository()
    private let networkMonitor: NetworkMonitor = NetworkMonitor.shared
    /// API repository for integration-related network calls
    private let integrationApiRepo: IntegrationRepositoryAPIProtocol = IntegrationAPIRepository()
    /// Migration service for Ionic app data
    private let migrationService = AccountMigrationService()

    @Published var activeAccount: Account? = nil
    @Published var allAccounts: [Account] = []

    var alertLang =  AlertStrings.self.ExpiredUserLogOutAlert
    var cancellables = Set<AnyCancellable>()
    private let tag = "AccountService"

    init() {
        // Asynchronously load active account from local storage to set theme early
        Task {
            do {
                if let activeAcct = try localRepo.fetchAllAccountsSync()
                    .first(where: { $0.isActiveAccount == true }) {
                    hydrateTokensInAccount(activeAcct)
                    self.activeAccount = activeAcct
                    Theme.shared.setActiveAccount(activeAcct.accountId)
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to fetch accounts on startup: \(error.localizedDescription)")
            }
        }
        
        // Load initial accounts from local storage
        Task {
            do {
                try await migrateTokensToKeychainIfNeeded()
                try await syncUnsyncedAccounts() // Try to sync any offline changes
                try await updatePublishedState()
                let _ = try await refreshAllAccounts()
                if activeAccount == nil {
                    /// migrate from ionic app if needed
                    try await migrateFromIonicAppIfNeeded()
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Error: \(error.localizedDescription)")
            }
            $activeAccount
                .sink(receiveValue: { data in
                    if data == nil {
                        ServiceRegistry.shared.deregisterSessionServices()
                    } else {
                        ServiceRegistry.shared.registerSessionServices()
                        Theme.shared.setActiveAccount(data?.accountId)
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
            logger.log(level: .info, tag: tag, message: "Sign up requested for email=\(maskedEmail(email))")
            // Check if maximum accounts reached
            try await checkIfMaxAccountsReached(email: email)
            let response = try await apiRepo.createAccount(email: email, password: password, profile: profile)
            
            // Check if account somehow already exists
            let existingAccount = try await localRepo.fetchAccount(byId: response.account.id)
            let isSameAccount = existingAccount?.accountId == activeAccount?.accountId
            
            // Disconnect connected scales if switching to a different account
            if !isSameAccount, activeAccount != nil {
                await bluetoothService.disconnectConnectedScales()
            }
            
            // Break reference to old object BEFORE any context changes
            if isSameAccount {
                activeAccount = nil
            }
            
            // TODO: Extract duplicated account preparation logic into private helper method
            // This logic is duplicated in signUp() and logIn() - consider refactoring into
            // prepareAuthenticatedAccount(from:existingAccount:) during future code quality improvements
            let account: Account
            if let existing = existingAccount {
                // Update existing account in place
                existing.update(from: response)
                existing.isLoggedIn = true
                existing.isActiveAccount = true
                existing.isExpired = false
                existing.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
                account = existing
            } else {
                // Create new account
                account = Account(from: response.account)
                account.isSynced = true
                account.isLoggedIn = true
                account.isActiveAccount = true
                account.isExpired = false
                account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
            }
            if let a = response.accessToken, let r = response.refreshToken, let e = response.expiresAt {
                keychainService.setTokens(Tokens(accessToken: a, refreshToken: r, expiresAt: e), for: account.accountId)
            }
            try await makeOtherAccountsInactive(except: account)
            if existingAccount == nil {
                try await saveAccountClearingTokens(account)
            } else {
                try await updateAccountClearingTokens(account)
            }
            try await updatePublishedState()
            logger.log(level: .success, tag: tag, message: "Sign up successful for accountId=\(account.accountId), email=\(maskedEmail(email))")
            return account
        } catch {
            logger.log(level: .error, tag: tag, message: "Sign up failed for email=\(maskedEmail(email)): \(error.localizedDescription)")
            throw error // No offline fallback for signup
        }
    }

    /// Logs in an existing account with the provided email and password.
    func logIn(email: String, password: String) async throws -> Account {
        do {
            logger.log(level: .info, tag: tag, message: "Login requested for email=\(maskedEmail(email))")
            // Check if maximum accounts reached
            try await checkIfMaxAccountsReached(email: email)
            let response = try await apiRepo.logIn(email: email, password: password)
            
            // Check if logging in with same account
            let existingAccount = try await localRepo.fetchAccount(byId: response.account.id)
            let isSameAccount = existingAccount?.accountId == activeAccount?.accountId
            
            // Disconnect connected scales if switching to a different account
            if !isSameAccount, activeAccount != nil {
                await bluetoothService.disconnectConnectedScales()
            }
            
            // Break reference to old object BEFORE any context changes
            if isSameAccount {
                activeAccount = nil
            }
            
            // TODO: Extract duplicated account preparation logic into private helper method
            // This logic is duplicated in signUp() and logIn() - consider refactoring into
            // prepareAuthenticatedAccount(from:existingAccount:) during future code quality improvements
            let account: Account
            if let existing = existingAccount {
                // Update existing account in place
                existing.update(from: response)
                existing.isLoggedIn = true
                existing.isActiveAccount = true
                existing.isExpired = false
                existing.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
                account = existing
            } else {
                // Create new account
                account = Account(from: response.account)
                account.isSynced = true
                account.isLoggedIn = true
                account.isActiveAccount = true
                account.isExpired = false
                account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
            }
            if let a = response.accessToken, let r = response.refreshToken, let e = response.expiresAt {
                keychainService.setTokens(Tokens(accessToken: a, refreshToken: r, expiresAt: e), for: account.accountId)
            }
            try await makeOtherAccountsInactive(except: account)
            if existingAccount == nil {
                try await saveAccountClearingTokens(account)
            } else {
                try await updateAccountClearingTokens(account)
            }
            try await updatePublishedState()
            try await refreshAccount()
            logger.log(level: .success, tag: tag, message: "Login successful for accountId=\(account.accountId), email=\(maskedEmail(email))")
            return account
        } catch {
            logger.log(level: .error, tag: tag, message: "Login failed for email=\(maskedEmail(email)): \(error.localizedDescription)")
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
        logger.log(level: .info, tag: tag, message: "Logout requested for accountId=\(accountId), isAutoLogout=\(isAutoLogout)")

        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }

        // Helper to perform the actual logout work (API + local updates)
        let performLogout: @Sendable () async throws -> Void = { [weak self] in
            guard let self else { return }
            try await self.executeLogout(on: localAccount, isAutoLogout: isAutoLogout, skipStateUpdate: false)
        }

        // Notify observers **before** performing the local logout so UI can react.
        if isAutoLogout, localAccount.isActiveAccount == true {
            logger.log(level: .info, tag: tag, message: "Auto-logout alert shown for accountId=\(accountId)")
            Task { try? await performLogout() }
            let userName = "\(localAccount.firstName ?? "")"
            let alert = AlertModel(
                title: alertLang.title(userName),
                message: alertLang.message,
                buttons: [
                    AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in
                        
                    }
                ]
            )
            notificationService.showAlert(alert)
        } else {
            try await performLogout()
        }
        logger.log(level: .info, tag: tag, message: "Logout completed for accountId=\(accountId)")
    }

    /// Deletes the current active account or a specific account by ID.
    func deleteAccount() async throws {
        // If no active account, throw error
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        do {
            logger.log(level: .info, tag: tag, message: "Delete account requested for accountId=\(accountId)")
            try await apiRepo.deleteAccount(accountId: accountId)
            keychainService.deleteTokens(for: accountId)
            keychainService.deleteFCMToken(for: accountId)
            try await localRepo.deleteAccount(byId: accountId)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Account deleted for accountId=\(accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Delete account failed for accountId=\(accountId): \(error.localizedDescription)")
            throw error // Handle any errors that occur during deletion
        }
    }

    /// Deletes all accounts locally.
    func deleteAllAccounts() async throws {
        do {
            logger.log(level: .info, tag: tag, message: "Delete all accounts requested")
            let accounts = try await localRepo.fetchAllAccounts()
            for account in accounts {
                keychainService.deleteTokens(for: account.accountId)
                keychainService.deleteFCMToken(for: account.accountId)
            }
            try await localRepo.deleteAllAccounts()
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "All accounts deleted locally")
        } catch {
            logger.log(level: .error, tag: tag, message: "Delete all accounts failed: \(error.localizedDescription)")
            throw error // Handle any errors that occur during deletion
        }
    }

    // MARK: - Account Switching
    /// Switches to a different account by setting it as the active account.
    func switchAccount(to account: Account) async throws {
        let fromAccountId = activeAccount?.accountId ?? "nil"
        let targetAccountId = account.accountId
        // Check network connectivity before switching
        guard networkMonitor.isConnected else {
            logger.log(level: .error, tag: tag, message: "Switch account blocked: no internet. fromAccountId=\(fromAccountId), targetAccountId=\(targetAccountId)")
            throw HTTPError.noInternet
        }
        do {
            logger.log(level: .info, tag: tag, message: "Switch account requested. fromAccountId=\(fromAccountId), targetAccountId=\(targetAccountId)")
            let responseAccount = try await refreshAccount(accountId: account.accountId)
            await bluetoothService.disconnectConnectedScales()
            activeAccount = nil
            try await setActiveAccount(responseAccount)
            logger.log(level: .success, tag: tag, message: "Switched active account successfully. fromAccountId=\(fromAccountId), targetAccountId=\(responseAccount.accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Switch account failed. fromAccountId=\(fromAccountId), targetAccountId=\(targetAccountId), error=\(error.localizedDescription)")
            throw error
        }
    }

    /// Sets the specified account as the active account and makes other accounts inactive.
    func setActiveAccount(_ account: Account) async throws {
        account.isActiveAccount = true
        account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
        try await makeOtherAccountsInactive(except: account)
        try await updateAccountClearingTokens(account)
        try await updatePublishedState()

        // Update theme with new active account
        Theme.shared.setActiveAccount(account.accountId)

        logger.log(level: .info, tag: tag, message: "Active account set to accountId=\(account.accountId)")
    }

    // MARK: - Account State
    /// Returns the currently active account, updating the published state first.
    func getActiveAccount() async throws -> Account? {
        return activeAccount
    }

    /// Returns all logged-in accounts, filtering out inactive or expired accounts.
    func getAllLoggedInAccounts() async throws -> [Account] {
        let all = try await localRepo.fetchAllAccounts()
        return all.filter { $0.isLoggedIn == true }
    }

    /// Fetches an account by its unique ID.
    /// Hydrates tokens from Keychain (and migrates from account if Keychain is empty).
    func fetchAccount(byId id: String) async throws -> Account? {
        guard let account = try await localRepo.fetchAccount(byId: id) else { return nil }
        hydrateTokensInAccount(account)
        return account
    }

    /// Fetches all accounts stored locally.
    /// Hydrates tokens from Keychain for each account (and migrates when Keychain is empty).
    func fetchAllAccounts() async throws -> [Account] {
        let accounts = try await localRepo.fetchAllAccounts()
        for account in accounts {
            hydrateTokensInAccount(account)
        }
        return accounts
    }

    // MARK: - Account Updates
    /// Updates the active account with the provided updated account data.
    func updateAccount(_ updatedAccount: Account) async throws -> Account {
        guard let localAccount = try await localRepo.fetchAccount(byId: updatedAccount.accountId) else {
            throw AccountError.accountNotFound(id: updatedAccount.accountId)
        }

        do {
            logger.log(level: .info, tag: tag, message: "Update account requested for accountId=\(updatedAccount.accountId)")
            let response = try await apiRepo.editAccount(updatedAccount)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Update account successful for accountId=\(updatedAccount.accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.update(from: updatedAccount.toAccountDTO())
                localAccount.isSynced = false
                try await updateAccountClearingTokens(updatedAccount)
                try await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Update account saved offline for accountId=\(updatedAccount.accountId), offline=true, reason=network_error")
                return updatedAccount
            }
            logger.log(level: .error, tag: tag, message: "Update account failed for accountId=\(updatedAccount.accountId): \(error.localizedDescription)")
            throw error
        }
    }

    @discardableResult
    func createGoal(_ goal: Goal) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            logger.log(level: .info, tag: tag, message: "Create goal requested for accountId=\(accountId)")
            let response = try await apiRepo.createGoal(goal)
            localAccount.update(from: response)
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            notifyActiveAccountChanged()
            logger.log(level: .info, tag: tag, message: "Create goal successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.goalSettings?.goalType = goal.type
                localAccount.goalSettings?.goalWeight = Double(goal.goalWeight)
                localAccount.goalSettings?.isSynced = false
                localAccount.goalSettings?.initialWeight = Double(goal.initialWeight)
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                notifyActiveAccountChanged()
                logger.log(level: .error, tag: tag, message: "Create goal saved offline for accountId=\(accountId), offline=true, reason=network_error, goalType=\(goal.goalType.rawValue), goalWeight=\(goal.goalWeight), initialWeight=\(goal.initialWeight)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Create goal failed for accountId=\(accountId): \(error.localizedDescription)")
                throw error
            }
        }
    }

    /// Updates the profile of the active account with the provided profile data.
    @discardableResult
    func updateProfile(_ profile: Profile, canSaveOffline: Bool = false) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            logger.log(level: .info, tag: tag, message: "Update profile requested for accountId=\(accountId)")
            let response = try await apiRepo.patchProfile(profile)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            notifyActiveAccountChanged()
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Update profile successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if canSaveOffline && HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.update(from: profile)
                try await updateAccountClearingTokens(localAccount)
                notifyActiveAccountChanged()

                try await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Update profile saved offline for accountId=\(accountId), offline=true, reason=network_error")
                return localAccount
            }
            logger.log(level: .error, tag: tag, message: "Update profile failed for accountId=\(accountId): \(error.localizedDescription)")
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
            logger.log(level: .info, tag: tag, message: "Update bodyComp requested for accountId=\(accountId)")
            let response = try await apiRepo.patchBodyComp(bodyComp)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            if let freshAccount = try await localRepo.fetchAccount(byId: localAccount.accountId) {
                if activeAccount?.accountId == freshAccount.accountId {
                    activeAccount = freshAccount
                }
            }
            try await updatePublishedState(forceRefresh: true)
            let finalWeightUnit = activeAccount?.weightSettings?.weightUnit?.rawValue ?? "nil"
            
            await MainActor.run {
                NotificationCenter.default.post(name: .accountWeightUnitChanged, object: nil, userInfo: ["weightUnit": finalWeightUnit])
            }
            
            logger.log(level: .info, tag: tag, message: "Update bodyComp successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.weightSettings?.weightUnit = bodyComp.weightUnit
                localAccount.weightSettings?.height = String(bodyComp.height)
                localAccount.weightSettings?.activityLevel = bodyComp.activityLevel
                try await updateAccountClearingTokens(localAccount)
                if activeAccount?.accountId == localAccount.accountId {
                    activeAccount = localAccount
                }
                try await updatePublishedState(forceRefresh: true)
                let finalWeightUnit = activeAccount?.weightSettings?.weightUnit?.rawValue ?? "nil"
                
                await MainActor.run {
                    NotificationCenter.default.post(name: .accountWeightUnitChanged, object: nil, userInfo: ["weightUnit": finalWeightUnit])
                }
                logger.log(level: .error, tag: tag, message: "Update bodyComp saved offline for accountId=\(accountId), offline=true, reason=network_error, weightUnit=\(bodyComp.weightUnit.rawValue), height=\(bodyComp.height), activityLevel=\(bodyComp.activityLevel.rawValue)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Update bodyComp failed for accountId=\(accountId): \(error.localizedDescription)")
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

        keychainService.setTokens(tokens, for: accountId)
        localAccount.update(from: tokens)
        try await updateAccountClearingTokens(localAccount)
        try await updatePublishedState()
        logger.log(level: .info, tag: tag, message: "Tokens updated for accountId=\(accountId)")
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
            // Persist canonical rawValue (e.g., "dashboard_12_metrics")
            localAccount.dashboardSettings?.dashboardType = type.rawValue
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Dashboard type updated for accountId=\(accountId) to \(type.rawValue)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Dashboard type saved offline for accountId=\(accountId)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Dashboard type update failed for accountId=\(accountId): \(error.localizedDescription)")
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
            logger.log(level: .info, tag: tag, message: "Update integration requested for accountId=\(accountId), type=\(integrationType.rawValue)")
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
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Integration updated for accountId=\(accountId), type=\(integrationType.rawValue)")
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
                try? await updateAccountClearingTokens(localAccount)
                try? await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Integration update saved offline for accountId=\(accountId), type=\(integrationType.rawValue), offline=true, reason=network_error")
                return localAccount
            }
            logger.log(level: .error, tag: tag, message: "Integration update failed for accountId=\(accountId), type=\(integrationType.rawValue): \(error.localizedDescription)")
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
            logger.log(level: .info, tag: tag, message: "Update notifications requested for accountId=\(accountId)")
            let response = try await apiRepo.patchNotification(notifications)
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            notifyActiveAccountChanged()
            logger.log(level: .info, tag: tag, message: "Update notifications successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.notificationSettings?.shouldSendEntryNotifications = notifications.shouldSendEntryNotifications
                localAccount.notificationSettings?.shouldSendWeightInEntryNotifications = notifications.shouldSendWeightInEntryNotifications
                localAccount.isSynced = false
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                notifyActiveAccountChanged()
                logger.log(level: .error, tag: tag, message: "Update notifications saved offline for accountId=\(accountId), offline=true, reason=network_error, shouldSendEntry=\(notifications.shouldSendEntryNotifications), shouldSendWeight=\(notifications.shouldSendWeightInEntryNotifications)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Update notifications failed for accountId=\(accountId): \(error.localizedDescription)")
                throw error
            }
        }
    }

    // MARK: - Password & Security
    /// Requests a password reset for the specified email.
    func requestPasswordReset(email: String) async throws {
        logger.log(level: .info, tag: tag, message: "Password reset requested")
        try await apiRepo.requestPasswordReset(email: email)
    }

    /// Updates the password for the active account or a specific account by ID.
    func updatePassword(oldPassword: String, newPassword: String) async throws {
        do {
            let tokens = try await apiRepo.updatePassword(oldPassword: oldPassword, newPassword: newPassword)
            try await self.updateTokens(tokens)
            logger.log(level: .info, tag: tag, message: "Password updated successfully")
        } catch {
            logger.log(level: .error, tag: tag, message: "Password update failed: \(error.localizedDescription)")
            throw error
        }
    }

    // MARK: - Sync & Offline
    /// Refreshes all accounts by fetching from API and updating local storage.
    func refreshAllAccounts() async throws {
        logger.log(level: .info, tag: tag, message: "Refresh all accounts requested")
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
                            try await updateAccountClearingTokens(expiredAccount)
                        }
                    } catch {
                        // Ignore errors during logout
                        continue
                    }
                }
            }
        }
        try await updatePublishedState()
        logger.log(level: .info, tag: tag, message: "Refresh all accounts completed")
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
            logger.log(level: .debug, tag: tag, message: "Refresh account requested for accountId=\(accountId)")
            
            // Try to fetch from API
            let dto = try await apiRepo.fetchAccount(accountId: localAccount.accountId)
            
            // Update account from API response
            localAccount.update(from: dto)
            
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .debug, tag: tag, message: "Refresh account successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                // On network error, return local account
                logger.log(level: .error, tag: tag, message: "Refresh account network error, returning local copy for accountId=\(accountId)")
                return localAccount
            }
            logger.log(level: .error, tag: tag, message: "Refresh account failed for accountId=\(accountId): \(error.localizedDescription)")
            throw error
        }
    }

    func clearOfflineData(for account: Account) async throws {
        throw AccountError.notImplemented
    }

    /// Deletes all accounts locally, logging out each account first.
    /// Logs out non-active accounts first, then the active account last to prevent
    /// premature navigation to the landing screen.
    /// Uses batch logout to prevent state updates until all accounts are logged out.
    func logOutAllAccounts() async throws {
        do {
            logger.log(level: .info, tag: tag, message: "Logout all accounts requested")
            let allAccounts = try await localRepo.fetchAllAccounts()
            
            // Separate accounts into active and non-active using isActiveAccount property
            let nonActiveAccounts = allAccounts.filter { $0.isActiveAccount != true }
            let activeAccountToLogout = allAccounts.first { $0.isActiveAccount == true }
            
            // Log out non-active accounts first (skip state updates to prevent navigation)
            for account in nonActiveAccounts {
                do {
                    guard let localAccount = try await localRepo.fetchAccount(byId: account.accountId) else {
                        continue
                    }
                    try await executeLogout(on: localAccount, isAutoLogout: false, skipStateUpdate: true)
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to logout non-active account \(account.accountId): \(error.localizedDescription)")
                    continue // Ignore errors during logout
                }
            }
            
            // Log out the active account last (skip state update to prevent navigation)
            if let activeAccount = activeAccountToLogout {
                do {
                    guard let localAccount = try await localRepo.fetchAccount(byId: activeAccount.accountId) else {
                        // If account not found, just update state
                        try await self.updatePublishedState()
                        return
                    }
                    try await executeLogout(on: localAccount, isAutoLogout: false, skipStateUpdate: true)
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to logout active account \(activeAccount.accountId): \(error.localizedDescription)")
                    // Continue even if active account logout fails
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Error during logout all accounts: \(error.localizedDescription)")
        }
        
        // Update state only once at the end to prevent premature navigation
        try await self.updatePublishedState()
        logger.log(level: .info, tag: tag, message: "Logout all accounts completed")
    }

    /// Synchronizes any unsynced accounts with the server.
    func syncUnsyncedAccounts() async throws {
        // Ensure we have connectivity before attempting to sync
        guard networkMonitor.isConnected else { return }
        logger.log(level: .info, tag: tag, message: "Sync unsynced account data requested")

        // Always fetch the account flagged as active from local storage. This avoids
        // relying on a potentially stale `activeAccount` reference.
        guard let localAccount = try await localRepo.fetchAllAccounts()
            .first(where: { $0.isActiveAccount == true }) else {
            return // No active account found locally – nothing to sync
        }

                // Keep the published `activeAccount` in sync with the freshly-fetched model
        self.activeAccount = localAccount
        let isSynced = localAccount.isSynced ?? true
        // Take an immutable snapshot of offline values BEFORE any network calls can mutate `localAccount`
        // Profile-related
        let offlineFirstName = localAccount.firstName
        let offlineLastName = localAccount.lastName
        let offlineEmail = localAccount.email
        let offlineGender = localAccount.gender
        let offlineZipcode = localAccount.zipcode
        let offlineDob = localAccount.dob
        // BodyComp-related
        let offlineWeightUnit = localAccount.weightSettings?.weightUnit
        let offlineHeightDouble = Double(localAccount.weightSettings?.height ?? "0")
        let offlineActivityLevel = localAccount.weightSettings?.activityLevel
        // Notification-related
        let offlineShouldSendEntry = localAccount.notificationSettings?.shouldSendEntryNotifications
        let offlineShouldSendWeightIn = localAccount.notificationSettings?.shouldSendWeightInEntryNotifications
        // Dashboard-related
        let offlineDashboardTypeRaw = localAccount.dashboardSettings?.dashboardType
        let offlineDashboardMetricsCsv = localAccount.dashboardSettings?.dashboardMetrics
        // Streak-related
        let offlineIsStreakOn = localAccount.streaksSettings?.isStreakOn
        let offlineStreakTimestamp = localAccount.streaksSettings?.streakTimestamp
        // Weightless-related
        let offlineIsWeightlessOn = localAccount.weightlessSettings?.isWeightlessOn
        let offlineWeightlessTimestamp = localAccount.weightlessSettings?.weightlessTimestamp
        let offlineWeightlessWeight = localAccount.weightlessSettings?.weightlessWeight
        // Goal-related
        let offlineGoalType = localAccount.goalSettings?.goalType
        let offlineInitialWeight = localAccount.goalSettings?.initialWeight
        let offlineGoalWeight = localAccount.goalSettings?.goalWeight
        let offlineGoalIsSynced = localAccount.goalSettings?.isSynced
        // Integrations-related
        let offlineIsHealthKitOn = localAccount.integrationSettings?.isHealthKitOn
        // Use the original localAccount for relationship access to avoid SwiftData backing data issues
        do {
            // Handle Profile updates
            if let firstName = offlineFirstName,
               let gender = offlineGender,
               let zipcode = offlineZipcode,
               let dob = offlineDob,
               let weightUnit = offlineWeightUnit,
               let height = offlineHeightDouble,
               let activityLevel = offlineActivityLevel,
               !isSynced {
                let profile = Profile(
                    firstName: firstName,
                    lastName: offlineLastName ?? "",
                    email: offlineEmail,
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
            if let weightUnit = offlineWeightUnit,
               let height = offlineHeightDouble,
               let activityLevel = offlineActivityLevel,
               !isSynced {
                let bodyComp = BodyComp(
                    weightUnit: weightUnit,
                    height: height,
                    activityLevel: activityLevel
                )
                try await updateBodyComp(bodyComp)
            }
            
            // Handle Notification Settings
            if let shouldSendEntry = offlineShouldSendEntry,
               let shouldSendWeightIn = offlineShouldSendWeightIn,
               !isSynced {
                let notifications = Notifications(
                    shouldSendEntryNotifications: shouldSendEntry,
                    shouldSendWeightInEntryNotifications: shouldSendWeightIn
                )
                try await updateNotifications(notifications: notifications)
            }

            
            // Handle Dashboard Type
            if let dashboardType = offlineDashboardTypeRaw,
               !isSynced {
                try await updateDashboardType(type: DashboardType(rawValue: dashboardType) ?? .dashboard4)
            }

            // Handle Dashboard Metrics
            if let metricsString = offlineDashboardMetricsCsv,
               !isSynced {
                let metrics = metricsString.split(separator: ",").map(String.init)
                try await updateDashboardMetrics(metrics: metrics)
            }

            // Handle Streak Status
            if let isStreakOn = offlineIsStreakOn,
               let streakTimestamp = offlineStreakTimestamp,
               !isSynced {
                try await updateStreak(isStreakOn: isStreakOn, streakTimestamp: streakTimestamp)
            }

            // Handle Weightless Mode
            if let isWeightlessOn = offlineIsWeightlessOn,
               let weightlessTimestamp = offlineWeightlessTimestamp,
               let weightlessWeight = offlineWeightlessWeight,
               !isSynced {
                try await updateWeightless(isWeightlessOn: isWeightlessOn, weightlessTimestamp: weightlessTimestamp, weightlessWeight: Double(weightlessWeight))
            }

            // Handle Goal Settings
            if let goalType = offlineGoalType,
               let initialWeight = offlineInitialWeight,
               let goalWeight = offlineGoalWeight,
               offlineGoalIsSynced == false {
                let goal = Goal(
                    type: goalType,
                    goalWeight: Int(goalWeight),
                    initialWeight: Int(initialWeight),
                    goalType: goalType
                )
                try await createGoal(goal)
            }


            // Handle Integration Settings
            if localAccount.integrationSettings != nil,
               !isSynced {
                // • Apple Health (HealthKit)
                if offlineIsHealthKitOn == true {
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
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Sync unsynced account data completed for accountId=\(localAccount.accountId)")

        } catch {
            if !HTTPError.isNetworkError(error) {
                logger.log(level: .error, tag: tag, message: "Sync unsynced account data failed: \(error.localizedDescription)")
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
            logger.log(level: .info, tag: tag, message: "Delete integration requested for accountId=\(accountId), type=\(type.rawValue)")
            try await integrationApiRepo.deleteHealthIntegration(deviceId: deviceId)
            try await refreshAccount()
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Integration deleted for accountId=\(accountId), type=\(type.rawValue)")
        } catch {
            if HTTPError.isNetworkError(error) {
                if type == .healthKit {
                    localAccount.integrationSettings?.isHealthKitOn = false
                    localAccount.isSynced = false
                }
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Delete integration saved offline for accountId=\(accountId), type=\(type.rawValue), offline=true, reason=network_error")
            }
            logger.log(level: .error, tag: tag, message: "Delete integration failed for accountId=\(accountId), type=\(type.rawValue): \(error.localizedDescription)")
            throw error
        }
    }

    @discardableResult
    func updateDashboardMetrics(metrics: [String]) async throws -> Account {
        return try await updateMetrics(metrics, type: "dashboard", apiCall: apiRepo.patchDashboardMetrics)
    }
    
    @discardableResult
    func updateProgressMetrics(metrics: [String]) async throws -> Account {
        return try await updateMetrics(metrics, type: "progress", apiCall: apiRepo.patchProgressMetrics)
    }
    
    private func updateMetrics(
        _ metrics: [String],
        type: String,
        apiCall: ([String]) async throws -> AccountResponse
    ) async throws -> Account {
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        
        do {
            let response = try await apiCall(metrics)
            
            // Store the sent order before updating from response to prevent order loss
            let sentOrderString = metrics.joined(separator: ",")
            
            // Update other account fields from response
            localAccount.update(from: response)
            
            // Preserve the order we sent instead of using API response order
            // This matches Android behavior where the sent order is stored directly
            if type == "dashboard", let dashboardSettings = localAccount.dashboardSettings {
                dashboardSettings.dashboardMetrics = sentOrderString
            } else if type == "progress", let dashboardSettings = localAccount.dashboardSettings {
                dashboardSettings.progressMetrics = sentOrderString
            }
            
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Update \(type) metrics successful: accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                try? await updateAccountClearingTokens(localAccount)
                logger.log(level: .error, tag: tag, message: "Update \(type) metrics saved offline for accountId=\(accountId), offline=true, reason=network_error, metrics=\(metrics)")
            }
            logger.log(level: .error, tag: tag, message: "Failed to update \(type) metrics: \(error)")
            throw error
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
            logger.log(level: .info, tag: tag, message: "Update streak requested for accountId=\(accountId), isOn=\(isStreakOn)")
            let response = try await apiRepo.patchStreak(isStreakOn, streakTimestamp)
            localAccount.update(from: response)
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Update streak successful for accountId=\(accountId)")
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
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                logger.log(level: .error, tag: tag, message: "Update streak saved offline for accountId=\(accountId), offline=true, reason=network_error, isStreakOn=\(isStreakOn)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Update streak failed for accountId=\(accountId): \(error.localizedDescription)")
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
            logger.log(level: .info, tag: tag, message: "Update weightless requested for accountId=\(accountId), isOn=\(isWeightlessOn)")
            let response = try await apiRepo.patchWeightless(isWeightlessOn, weightlessTimestamp, Int(weightlessWeight))
            localAccount.update(from: response)
            localAccount.isSynced = true
            try await updateAccountClearingTokens(localAccount)
            try await updatePublishedState()
            notifyActiveAccountChanged()
            logger.log(level: .info, tag: tag, message: "Update weightless successful for accountId=\(accountId)")
            return localAccount
        } catch {
            if HTTPError.isNetworkError(error) {
                localAccount.isSynced = false
                localAccount.weightlessSettings?.isWeightlessOn = isWeightlessOn
                localAccount.weightlessSettings?.weightlessTimestamp = weightlessTimestamp
                localAccount.weightlessSettings?.weightlessWeight = isWeightlessOn ? weightlessWeight : nil
                try await updateAccountClearingTokens(localAccount)
                try await updatePublishedState()
                notifyActiveAccountChanged()
                logger.log(level: .error, tag: tag, message: "Update weightless saved offline for accountId=\(accountId), offline=true, reason=network_error, isWeightlessOn=\(isWeightlessOn), weightlessWeight=\(weightlessWeight)")
                return localAccount
            } else {
                logger.log(level: .error, tag: tag, message: "Update weightless failed for accountId=\(accountId): \(error.localizedDescription)")
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

        guard let account = account else {
            throw AccountError.noActiveAccount
        }
        let refreshToken = keychainService.getTokens(for: account.accountId)?.refreshToken ?? account.refreshToken
        guard let refreshToken = refreshToken else {
            throw AccountError.noActiveAccount
        }

        logger.log(level: .info, tag: tag, message: "Refresh tokens requested for accountId=\(account.accountId)")
        return try await apiRepo.refreshToken(
            refreshToken: refreshToken,
            accountId: account.accountId
        )
    }

    /// Gets the active tokens (access and refresh) for the current active account.
    /// Prefers Keychain; falls back to account fields for migration.
    func getActiveTokens() async throws -> Tokens {
        guard let account = activeAccount else {
            throw AccountError.noActiveAccount
        }
        let accountId = account.accountId
        if let tokens = keychainService.getTokens(for: accountId) {
            logger.log(level: .info, tag: tag, message: "Get active tokens requested for accountId=\(accountId)")
            return tokens
        }
        let accessToken = account.accessToken ?? ""
        let refreshToken = account.refreshToken ?? ""
        let expiresAt = account.expiresAt ?? ""
        logger.log(level: .info, tag: tag, message: "Get active tokens requested for accountId=\(accountId) (fallback from account)")
        return Tokens(
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt
        )
    }

    /// Updates the published state of active and all accounts.
    /// - Parameter forceRefresh: If true, always assign activeAccount even if accountId hasn't changed
    func updatePublishedState(forceRefresh: Bool = false) async throws {
        allAccounts = try await localRepo.fetchAllAccounts()
        let nextActive = allAccounts.first { $0.isActiveAccount == true }

        
        if forceRefresh || activeAccount?.accountId != nextActive?.accountId {
            activeAccount = nextActive
            // Always update theme when active account changes (including logout)
            Theme.shared.setActiveAccount(nextActive?.accountId)
        }
        logger.log(level: .debug, tag: tag, message: "Published state updated. total=\(allAccounts.count), active=\(activeAccount?.accountId ?? "nil"), forceRefresh=\(forceRefresh)")
    }
    
    /// Forces the activeAccount publisher to notify subscribers of changes to account properties.
    /// Call this after updating account properties that don't trigger automatic @Published notifications.
    func notifyActiveAccountChanged() {
        Task {
            try await updatePublishedState(forceRefresh: true)
        }
    }

    // MARK: - Private Helpers
    /// Deletes the account locally by ID and updates the published state.
    private func deleteAccountLocally(accountId: String) async throws {
        do {
            // delete the account from local storage
            try await localRepo.deleteAccount(byId: accountId)
            try await updatePublishedState()
            logger.log(level: .info, tag: tag, message: "Deleted account locally for accountId=\(accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Delete account locally failed for accountId=\(accountId): \(error.localizedDescription)")
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
                logger.log(level: .error, tag: tag, message: "Max accounts reached. Blocking new account creation for email=\(maskedEmail(email))")
                throw AccountError.maxAccountsReached
            }
        }
    }

    /// Hydrates account token fields from Keychain (in-memory only; tokens are not persisted to SwiftData).
    private func hydrateTokensInAccount(_ account: Account) {
        if let tokens = keychainService.getTokens(for: account.accountId) {
            account.accessToken = tokens.accessToken
            account.refreshToken = tokens.refreshToken
            account.expiresAt = tokens.expiresAt
        }
    }

    /// One-time migration: copy tokens from SwiftData to Keychain, then clear on Account so SwiftData never persists them again.
    private func migrateTokensToKeychainIfNeeded() async throws {
        let key = KvStorageKeys.tokensMigratedToKeychain.rawValue
        if kvStorage.getValue(forKey: key) as? Bool == true {
            return
        }
        let accounts = try await localRepo.fetchAllAccounts()
        for account in accounts {
            guard let a = account.accessToken, let r = account.refreshToken, let e = account.expiresAt,
                  !a.isEmpty, !r.isEmpty else { continue }
            keychainService.setTokens(Tokens(accessToken: a, refreshToken: r, expiresAt: e), for: account.accountId)
            account.accessToken = nil
            account.refreshToken = nil
            account.expiresAt = nil
            try await updateAccountClearingTokens(account)
        }
        kvStorage.setValue(true, forKey: key)
        logger.log(level: .info, tag: tag, message: "Tokens migrated from SwiftData to Keychain for \(accounts.count) account(s)")
    }

    /// Clears token fields on account before persist so tokens are never stored in SwiftData (Keychain only).
    private func clearTokenFieldsBeforeSave(_ account: Account) {
        account.accessToken = nil
        account.refreshToken = nil
        account.expiresAt = nil
    }

    /// Saves account to local store without persisting token fields (Keychain is source of truth).
    private func saveAccountClearingTokens(_ account: Account) async throws {
        clearTokenFieldsBeforeSave(account)
        try await localRepo.saveAccount(account)
    }

    /// Updates account in local store without persisting token fields (Keychain is source of truth).
    private func updateAccountClearingTokens(_ account: Account) async throws {
        clearTokenFieldsBeforeSave(account)
        try await localRepo.updateAccount(account)
    }

    private func maskedEmail(_ email: String) -> String {
        let parts = email.split(separator: "@", maxSplits: 1)
        guard parts.count == 2 else { return "***" }
        let local = String(parts[0])
        let domain = String(parts[1])
        guard let first = local.first else { return "***@\(domain)" }
        return "\(first)***@\(domain)"
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
            try await updateAccountClearingTokens(acc)
        }
    }

    // MARK: - Private Helpers
    /// Performs the actual logout logic: API call (ignored if it fails) + local flag updates + state refresh.
    /// - Parameter skipStateUpdate: If true, skips the state update to allow batch operations. Defaults to false.
    private func executeLogout(on localAccount: Account, isAutoLogout: Bool, skipStateUpdate: Bool = false) async throws {
        let fcmToken = PushNotificationService.shared.getStoredFCMToken(for: localAccount.accountId)
        do {
            logger.log(level: .info, tag: tag, message: "Executing logout (API) for accountId=\(localAccount.accountId)")
            try await apiRepo.logOut(fcmToken: fcmToken, accountId: localAccount.accountId)
        } catch {
            // Ignore API errors during logout
            logger.log(level: .error, tag: tag, message: "Logout API call failed for accountId=\(localAccount.accountId): \(error.localizedDescription)")
        }

        do {
            keychainService.deleteTokens(for: localAccount.accountId)
            keychainService.deleteFCMToken(for: localAccount.accountId)
            // Logout the account locally (happens regardless of API success/failure)
            localAccount.isLoggedIn = (localAccount.isLoggedIn ?? false) ? isAutoLogout : false
            localAccount.isActiveAccount = false
            localAccount.isExpired = isAutoLogout
            try await updateAccountClearingTokens(localAccount)
            logger.log(level: .info, tag: tag, message: "Local logout flags updated for accountId=\(localAccount.accountId)")
        } catch {
            // Ignore local persistence errors during logout
            logger.log(level: .error, tag: tag, message: "Local logout flag update failed for accountId=\(localAccount.accountId): \(error.localizedDescription)")
        }

        // Attempt to refresh published state; propagate any errors to the caller
        // Skip state update during batch operations to prevent premature navigation
        if !skipStateUpdate {
            try await updatePublishedState()
        }
    }

    // MARK: - Migration
    /// Migrates account data from Ionic app if needed
    /// Should be called once on app startup before other operations
    private func migrateFromIonicAppIfNeeded() async throws {
        guard migrationService.isMigrationNeeded() else {
            LoggerService.shared.log(level: .info, tag: tag, message: "No Ionic app migration needed (already completed or no data found)")
            return
        }

        do {
            LoggerService.shared.log(level: .info, tag: tag, message: "Starting comprehensive Ionic app migration (account + scales)")

            let (migratedAccount, scalesCount) = try await migrationService.migrateAccountAndScaleData()

            if let migratedAccount = migratedAccount {
                LoggerService.shared.log(level: .info, tag: tag, message: "Ionic app migration completed for account: \(migratedAccount.email) with \(scalesCount) scales")

                // Update published state to reflect the migrated account
                try await updatePublishedState()

                LoggerService.shared.log(level: .info, tag: tag, message: "Ionic app migration process completed!")
            } else {
                LoggerService.shared.log(level: .info, tag: tag, message: "No account data was migrated from Ionic app")
            }

        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Ionic app migration failed: \(error.localizedDescription)")
            // Don't throw the error - continue with normal app initialization
        }
    }

    deinit {
      cancellables.forEach { $0.cancel() }
      cancellables.removeAll()
    }
}
