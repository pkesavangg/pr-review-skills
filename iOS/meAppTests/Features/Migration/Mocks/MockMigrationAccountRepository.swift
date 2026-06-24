import Foundation
@testable import meApp

@MainActor
final class MockMigrationAccountRepository: AccountRepositoryProtocol {
    var saveAccountError: Error?
    private(set) var saveAccountCalls = 0
    private(set) var lastSavedAccount: Account?

    func fetchAccount(byId id: String) async throws -> Account? { return nil }
    func fetchAllAccounts() async throws -> [Account] { return [] }
    func fetchAllAccountsSync() throws -> [Account] { return [] }

    func saveAccount(_ account: Account) async throws {
        saveAccountCalls += 1
        lastSavedAccount = account
        if let error = saveAccountError { throw error }
    }

    func updateAccount(_ account: Account) async throws {}
    func deleteAccount(byId id: String) async throws {}
    func deleteAllAccounts() async throws {}
}
