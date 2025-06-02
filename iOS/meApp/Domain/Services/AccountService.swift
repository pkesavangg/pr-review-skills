import Foundation

@MainActor
final class AccountService: AccountServiceProtocol, ObservableObject {
    private let apiRepo: AccountRepositoryAPIProtocol = AccountRepositoryAPI()
    private let localRepo: AccountRepositoryProtocol = AccountRepository()
    
    @Published var currentLoggedInAccount: Account? = nil
    
    init() {
        // Load the current logged in account from local storage if available
        Task {
            do {
                currentLoggedInAccount = try await self.getActiveAccount();
            } catch {
                print("Failed to load current logged in account: \(error)")
            }
        }
    }
    
    // MARK: - Account Lifecycle
    func signUp(email: String, password: String, profile: Profile) async throws -> Account {
        do {
            let response = try await apiRepo.createAccount(email: email, password: password, profile: profile)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            try await localRepo.saveAccount(account)
            currentLoggedInAccount = try await self.getActiveAccount();
            return account
        } catch {
            throw error // No offline fallback for signup
        }
    }

    func logIn(email: String, password: String) async throws -> Account {
        do {
            let response = try await apiRepo.logIn(email: email, password: password)
            let account = Account(from: response.account)
            account.accessToken = response.accessToken
            account.refreshToken = response.refreshToken
            account.expiresAt = response.expiresAt
            account.isSynced = true
            account.isActiveAccount = true // New account is active by default
            account.isExpired = false // New account is not expired by default
            try await localRepo.saveAccount(account)
            currentLoggedInAccount = try await self.getActiveAccount();
            return account
        } catch {
            throw error // No offline fallback for login
        }
    }

    func logOut(accountId: String?) async throws {
        // Always try API, fallback to local only if network error
        // if accountId is nil, use current logged in account
        guard let accountId = accountId ?? currentLoggedInAccount?.accountId else {
            throw NSError(domain: "NoAccount", code: 0)
        }
        do {
            try await apiRepo.logOut(accountId: accountId, fcmToken: currentLoggedInAccount?.fcmToken)
            // Logout the account locally
            if let account = try await localRepo.fetchAccount(byId: accountId) {
                account.isLoggedIn = false
                try await localRepo.updateAccount(account)
            }
        } catch {
            if NetworkError.isNetworkError(error) {
                // Optionally mark as logged out locally
                if let account = try await localRepo.fetchAccount(byId: accountId) {
                    account.isLoggedIn = false
                    try await localRepo.updateAccount(account)
                }
            } else {
                throw error
            }
        }
    }

    func deleteAccount() async throws {
        // if accountId is nil, use current logged in account
        guard let accountId = currentLoggedInAccount?.accountId else {
            throw NSError(domain: "NoAccount", code: 0)
        }
        do {
            try await apiRepo.deleteAccount(accountId: accountId)
            try await localRepo.deleteAccount(byId: accountId)
        } catch {
            if NetworkError.isNetworkError(error) {
                // Mark as not synced, will retry delete on next sync
                if let account = try await localRepo.fetchAccount(byId: accountId) {
                    account.isSynced = false
                    try await localRepo.updateAccount(account)
                }
            } else {
                throw error
            }
        }
    }

    func switchAccount(to account: Account) async throws {
        try await setActiveAccount(account)
    }

    func setActiveAccount(_ account: Account) async throws {
        account.isActiveAccount = true
        // mark all other accounts as inactive and update them in local storage
        let allAccounts = try await localRepo.fetchAllAccounts()
        for acc in allAccounts where acc.accountId != account.accountId {
            acc.isActiveAccount = false
            try await localRepo.updateAccount(acc)
        }
        try await localRepo.updateAccount(account)
        currentLoggedInAccount = try await self.getActiveAccount();
    }

    // MARK: - Account State
    func getActiveAccount() async throws -> Account? {
        let all = try await localRepo.fetchAllAccounts()
        return all.first(where: { $0.isActiveAccount == true })
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

    func patchStoredAccount(_ updatedFields: [PartialKeyPath<Account>: Any]) async throws {
        // Not implemented: requires reflection or custom logic
        // You can implement this if you want partial updates
        throw NSError(domain: "NotImplemented", code: 0)
    }

    func updateProfile(_ profile: Profile) async throws -> Account {
        guard let accountId = currentLoggedInAccount?.accountId, let account = try await localRepo.fetchAccount(byId: accountId) else {
            throw NSError(domain: "NoAccount", code: 0)
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
        guard let accountId = currentLoggedInAccount?.accountId, let account = try await localRepo.fetchAccount(byId: accountId) else {
            throw NSError(domain: "NoAccount", code: 0)
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

    func updateTokens(_ tokens: Tokens) async throws {
        // Update token in current logged in account
        guard let account = currentLoggedInAccount else {
            throw NSError(domain: "NoAccount", code: 0)
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
        // Not implemented: depends on Integrations model
        throw NSError(domain: "NotImplemented", code: 0)
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
        guard let accountId = accountId ?? currentLoggedInAccount?.accountId else {
            throw NSError(domain: "NoAccount", code: 0)
        }
        let dto = try await apiRepo.fetchAccount(accountId: accountId)
        let account = Account(from: dto)
        account.isSynced = true
        try await localRepo.updateAccount(account)
        return account
    }

    func clearOfflineData(for account: Account) async throws {
        // Not implemented: depends on what offline data means
        throw NSError(domain: "NotImplemented", code: 0)
    }

    func deleteAllAccounts() async throws {
        try await localRepo.deleteAllAccounts()
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
} 
