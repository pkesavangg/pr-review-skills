import Foundation

@MainActor
final class AccountService: AccountServiceProtocol, ObservableObject {
    static let shared: AccountService = AccountService()
    
    private let apiRepo: AccountRepositoryAPIProtocol = AccountRepositoryAPI()
    private let localRepo: AccountRepositoryProtocol = AccountRepository()
    private let networkMonitor: NetworkMonitor = NetworkMonitor.shared
    
    @Published var activeAccount: Account? = nil
    @Published var allAccounts: [Account] = []

    
    init() {
        // Load initial accounts from local storage
        Task {
            do {
                try await updatePublishedState()
                let _ = try await refreshAccount()
                let _ = try await refreshAllAccounts()
            } catch {
               
            }
        }
    }
    
    // MARK: - Account Lifecycle
    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        do {
            // Check if maximum accounts reached
            if try await hasReachedMaxAccounts() {
                throw AccountError.maxAccountsReached
            }
            let response = try await apiRepo.createAccount(email: email, password: password, profile: profile)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            try await makeOtherAccountsInactive(except: account)
            try await localRepo.saveAccount(account)
            try await updatePublishedState()
            return account
        } catch {
            throw error // No offline fallback for signup
        }
    }
    
    func logIn(email: String, password: String) async throws -> Account {
        do {
            // Check if maximum accounts reached
            if try await hasReachedMaxAccounts() {
                throw AccountError.maxAccountsReached
            }
            let response = try await apiRepo.logIn(email: email, password: password)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            try await makeOtherAccountsInactive(except: account)
            try await localRepo.saveAccount(account)
            try await updatePublishedState()
            return account
        } catch {
            throw error // No offline fallback for login
        }
    }
    
    func logOut(accountId: String?) async throws {
        // Always try API, fallback to local only if network error
        // if accountId is nil, use current logged in account
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        do {
            // Get the FCM token from local repo using the accountId
            let account = try await localRepo.fetchAccount(byId: accountId)
            try await apiRepo.logOut(fcmToken: activeAccount?.fcmToken, accessToken: account?.accessToken)
        } catch {
            // Continue with local logout even if API call fails
        }
        // Logout the account locally (happens regardless of API success/failure)
        try await deleteAccountLocally(accountId: accountId)
        try await updatePublishedState()
    }
    
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
            throw NetworkError.noInternet
        }
        do {
            let _ = try await refreshAccount(accountId: account.accountId)
            try await setActiveAccount(account)
        } catch {
            throw error
        }
    }
    
    func setActiveAccount(_ account: Account) async throws {
        account.isActiveAccount = true
        try await makeOtherAccountsInactive(except: account)
        try await localRepo.updateAccount(account)
        try await updatePublishedState()
    }
    
    // MARK: - Account State
    func getActiveAccount() async throws -> Account? {
        try await updatePublishedState()
        return activeAccount
    }
    
    func getAllLoggedInAccounts() async throws -> [Account] {
        let all = try await localRepo.fetchAllAccounts()
        return all.filter { $0.isLoggedIn == true }
    }
    
    func fetchAccount(byId id: String) async throws -> Account? {
        return try await localRepo.fetchAccount(byId: id)
    }
    
    func fetchAllAccounts() async throws -> [Account] {
        return try await localRepo.fetchAllAccounts()
    }
    
    // MARK: - Account Updates
    func updateAccount(_ updatedAccount: Account) async throws -> Account {
        do {
            let response = try await apiRepo.editAccount(updatedAccount)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            try await localRepo.updateAccount(account)
            try await updatePublishedState()
            return account
        } catch {
            if NetworkError.isNetworkError(error) {
                updatedAccount.isSynced = false
                try await localRepo.updateAccount(updatedAccount)
                try await updatePublishedState()
                return updatedAccount
            } else {
                throw error
            }
        }
    }
    
    func updateProfile(_ profile: Profile) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let account = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            let response = try await apiRepo.patchProfile(profile)
            let updated = Account(from: response.account)
            updated.accessToken = response.accessToken
            updated.refreshToken = response.refreshToken
            updated.expiresAt = response.expiresAt
            updated.isSynced = true
            try await localRepo.updateAccount(updated)
            try await updatePublishedState()
            return updated
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                // Optionally update local profile fields
                try await localRepo.updateAccount(account)
                try await updatePublishedState()
                return account
            } else {
                throw error
            }
        }
    }
    
    func updateBodyComp(_ bodyComp: BodyComp) async throws -> Account {
        guard let accountId = activeAccount?.accountId, let account = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.noActiveAccount
        }
        do {
            let response = try await apiRepo.patchBodyComp(bodyComp)
            let updated = Account(from: response.account)
            updated.accessToken = response.accessToken
            updated.refreshToken = response.refreshToken
            updated.expiresAt = response.expiresAt
            updated.isSynced = true
            try await localRepo.updateAccount(updated)
            try await updatePublishedState()
            return updated
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
                try await updatePublishedState()
                return account
            } else {
                throw error
            }
        }
    }
    
    func updateTokens( _ tokens: Tokens, _ accountId: String? = nil) async throws {
        // Update tokens for the active account if accountId is nil
        guard let account = accountId == nil ? activeAccount : try await localRepo.fetchAccount(byId: accountId!) else {
            throw AccountError.noActiveAccount
        }
        account.accessToken = tokens.accessToken
        account.refreshToken = tokens.refreshToken
        account.expiresAt = tokens.expiresAt
        try await localRepo.updateAccount(account)
        try await updatePublishedState()
    }
    
    func updateDashboardType(accountId: String, type: DashboardType) async throws {
        guard let account = try await localRepo.fetchAccount(byId: accountId) else { return }
        do {
            let response = try await apiRepo.patchDashboardType(type)
            let updated = Account(from: response.account)
            updated.isSynced = true
            try await localRepo.updateAccount(updated)
            try await updatePublishedState()
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
                try await updatePublishedState()
            } else {
                throw error
            }
        }
    }
    
    func updateIntegrations(accountId: String, integrations: Integrations) async throws {
        throw AccountError.notImplemented
    }
    
    func updateNotifications(accountId: String, notifications: Notifications) async throws {
        guard let account = try await localRepo.fetchAccount(byId: accountId) else { return }
        do {
            let response = try await apiRepo.patchNotification(notifications)
            let updated = Account(from: response.account)
            updated.isSynced = true
            try await localRepo.updateAccount(updated)
            try await updatePublishedState()
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
                try await updatePublishedState()
            } else {
                throw error
            }
        }
    }
    
    // MARK: - Password & Security
    func requestPasswordReset(email: String) async throws {
        try await apiRepo.requestPasswordReset(email: email)
    }
    
    func updatePassword(oldPassword: String, newPassword: String) async throws {
        do {
            let tokens =  try await apiRepo.updatePassword(oldPassword: oldPassword, newPassword: newPassword)
            try await self.updateTokens(tokens)
        } catch {
            throw error
        }
    }
    
    // MARK: - Sync & Offline
    func refreshAllAccounts() async throws {
        let accounts = try await localRepo.fetchAllAccounts()
        for account in accounts {
            // Skip active account to avoid unnecessary refresh
            if account.isActiveAccount ?? false {
                continue
            }
            
            do {
                _ = try await refreshAccount(accountId: account.accountId)
            } catch {
                if NetworkError.isNetworkError(error) {
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
    
    func refreshAccount(accountId: String? = nil) async throws -> Account {
        // If accountId is nil, use current logged in account
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        // First get the local account
        guard let localAccount = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        
        do {
            // Try to fetch from API
            let dto = try await apiRepo.fetchAccount(accessToken: localAccount.accessToken)
            let account = Account(from: dto)
            account.isSynced = true
            try await localRepo.updateAccount(account)
            try await updatePublishedState()
            return account
        } catch {
            if NetworkError.isNetworkError(error) {
                // On network error, return local account
                return localAccount
            }
            throw error
        }
    }
    
    func clearOfflineData(for account: Account) async throws {
        throw AccountError.notImplemented
    }
    
    func deleteAllAccountsLocally() async throws {
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
    
    // Call this on app launch to sync unsynced accounts
    func syncUnsyncedAccounts() async throws {
        let all = try await localRepo.fetchAllAccounts()
        let unsynced = all.filter { $0.isSynced == false }
        for account in unsynced {
            do {
                let response = try await apiRepo.editAccount(account)
                let updated = Account(from: response.account)
                updated.accessToken = response.accessToken
                updated.refreshToken = response.refreshToken
                updated.expiresAt = response.expiresAt
                updated.isSynced = true
                try await localRepo.updateAccount(updated)
            } catch {
                // If still network error, leave as unsynced
                continue
            }
        }
        try await updatePublishedState()
    }
    
    private func deleteAccountLocally(accountId: String) async throws {
        do {
            // delete the account from local storage
            try await localRepo.deleteAccount(byId: accountId)
            try await updatePublishedState()
        } catch {
            throw error
        }
    }
    
    private func hasReachedMaxAccounts() async throws -> Bool {
        let count = try await getAccountCount()
        return count >= AppConstants.Account.maxAccounts
    }
    
    private func getAccountCount() async throws -> Int {
        let accounts = try await localRepo.fetchAllAccounts()
        return accounts.count
    }
    
    private func makeOtherAccountsInactive(except account: Account) async throws {
        let allAccounts = try await localRepo.fetchAllAccounts()
        for acc in allAccounts where acc.accountId != account.accountId {
            acc.isActiveAccount = false
            try await localRepo.updateAccount(acc)
        }
    }
    
    private func updatePublishedState() async throws {
        allAccounts = try await localRepo.fetchAllAccounts()
        activeAccount = allAccounts.first(where: { $0.isActiveAccount == true })
    }
}
