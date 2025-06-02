import Foundation

@MainActor
final class AccountService: AccountServiceProtocol, ObservableObject {
    private let apiRepo: AccountRepositoryAPIProtocol = AccountRepositoryAPI()
    private let localRepo: AccountRepositoryProtocol = AccountRepository()
    
    @Published var activeAccount: Account? = nil
    
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
            activeAccount = try await self.getActiveAccount();
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
            activeAccount = try await self.getActiveAccount();
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
            let activeAccount = try await localRepo.fetchAccount(byId: accountId)
            try await apiRepo.logOut(accountId: accountId, fcmToken: activeAccount?.fcmToken)
        } catch {
            // Continue with local logout even if API call fails
        }
        // Logout the account locally (happens regardless of API success/failure)
        try await logOutLocally(accountId: accountId)
    }
    
    func deleteAccount() async throws {
        // If no active account, throw error
        guard let accountId = activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        do {
            try await apiRepo.deleteAccount(accountId: accountId)
            try await localRepo.deleteAccount(byId: accountId)
        } catch {
            throw error // Handle any errors that occur during deletion
        }
    }
    
    func removeAccountLocally(accountId: String) async throws {
        // Get the account to ensure it exists
        guard let account = try await localRepo.fetchAccount(byId: accountId) else {
            throw AccountError.accountNotFound(id: accountId)
        }
        do {
            // Logout the account
            try await logOut(accountId: accountId)
        } catch {
            
        }
        // Remove the account locally
        try await localRepo.deleteAccount(byId: accountId)
    }
    
    // MARK: - Account Switching
    /// Switches to a different account by setting it as the active account.
    func switchAccount(to account: Account) async throws {
        try await setActiveAccount(account)
    }
    
    func setActiveAccount(_ account: Account) async throws {
        account.isActiveAccount = true
        try await makeOtherAccountsInactive(except: account)
        try await localRepo.updateAccount(account)
        activeAccount = try await self.getActiveAccount();
    }
    
    // MARK: - Account State
    func getActiveAccount() async throws -> Account? {
        let all = try await localRepo.fetchAllAccounts()
        activeAccount =  all.first(where: { $0.isActiveAccount == true })
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
    func updateAccount(_ updatedAccount: Account) async throws {
        do {
            let response = try await apiRepo.editAccount(updatedAccount)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            try await localRepo.updateAccount(account)
        } catch {
            if NetworkError.isNetworkError(error) {
                updatedAccount.isSynced = false
                try await localRepo.updateAccount(updatedAccount)
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
            return updated
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                // Optionally update local profile fields
                try await localRepo.updateAccount(account)
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
            return updated
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
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
    }
    
    func updateDashboardType(accountId: String, type: DashboardType) async throws {
        guard let account = try await localRepo.fetchAccount(byId: accountId) else { return }
        do {
            let response = try await apiRepo.patchDashboardType(type)
            let updated = Account(from: response.account)
            updated.isSynced = true
            try await localRepo.updateAccount(updated)
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
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
        } catch {
            if NetworkError.isNetworkError(error) {
                account.isSynced = false
                try await localRepo.updateAccount(account)
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
    func refreshAccount(accountId: String?) async throws -> Account {
        // If accountId is nil, use current logged in account
        guard let accountId = accountId ?? activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        let dto = try await apiRepo.fetchAccount(accountId: accountId)
        let account = Account(from: dto)
        account.isSynced = true
        try await localRepo.updateAccount(account)
        return account
    }
    
    func clearOfflineData(for account: Account) async throws {
        throw AccountError.notImplemented
    }
    
    func deleteAllAccountsLocally() async throws {
        do {
            // Loop all local accounts and log out each one
            do {
                let allAccounts = try await localRepo.fetchAllAccounts()
                for account in allAccounts {
                    try await logOut(accountId: account.accountId)
                }
            } catch {}
            // Now delete all accounts locally
            try await localRepo.deleteAllAccounts()
        } catch {
            throw error // Handle any errors that occur during deletion
        }
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
    }
    
    private func logOutLocally(accountId: String) async throws {
        do {
            if let account = try await localRepo.fetchAccount(byId: accountId) {
                account.isLoggedIn = false
                account.isActiveAccount = false
                try await localRepo.updateAccount(account)
            }
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
}
