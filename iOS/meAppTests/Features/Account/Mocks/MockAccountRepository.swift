import Foundation
@testable import meApp

@MainActor
final class MockAccountRepository: AccountRepositoryProtocol {
    private var accountsById: [String: Account] = [:]

    private(set) var fetchAccountCalls = 0
    private(set) var fetchAllAccountsCalls = 0
    private(set) var saveAccountCalls = 0
    private(set) var updateAccountCalls = 0
    private(set) var deleteAccountCalls = 0
    private(set) var deleteAllAccountsCalls = 0

    var saveAccountError: Error?
    var updateAccountError: Error?
    var deleteAccountError: Error?
    var deleteAllAccountsError: Error?

    func fetchAccount(byId id: String) async throws -> Account? {
        fetchAccountCalls += 1
        return accountsById[id]
    }

    func fetchAllAccounts() async throws -> [Account] {
        fetchAllAccountsCalls += 1
        return Array(accountsById.values)
    }

    func fetchAllAccountsSync() throws -> [Account] {
        Array(accountsById.values)
    }

    func saveAccount(_ account: Account) async throws {
        saveAccountCalls += 1
        if let saveAccountError {
            throw saveAccountError
        }
        accountsById[account.accountId] = account
    }

    func updateAccount(_ account: Account) async throws {
        updateAccountCalls += 1
        if let updateAccountError {
            throw updateAccountError
        }
        accountsById[account.accountId] = account
    }

    func deleteAccount(byId id: String) async throws {
        deleteAccountCalls += 1
        if let deleteAccountError {
            throw deleteAccountError
        }
        accountsById.removeValue(forKey: id)
    }

    func deleteAllAccounts() async throws {
        deleteAllAccountsCalls += 1
        if let deleteAllAccountsError {
            throw deleteAllAccountsError
        }
        accountsById.removeAll()
    }

    func seed(_ accounts: [Account]) {
        for account in accounts {
            accountsById[account.accountId] = account
        }
    }

    func containsAccount(id: String) -> Bool {
        accountsById[id] != nil
    }

    func all() -> [Account] {
        Array(accountsById.values)
    }
}
