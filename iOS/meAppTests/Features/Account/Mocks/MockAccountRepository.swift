//
//  MockAccountRepository.swift
//  meAppTests
//

import Foundation
@testable import meApp

/// Mock for AccountRepositoryProtocol used in AccountService unit tests.
@MainActor
final class MockAccountRepository: AccountRepositoryProtocol {

    // MARK: - In-memory store
    var storedAccounts: [Account] = []

    // MARK: - Configurable errors
    var fetchAccountByIdError: Error?
    var fetchAllAccountsError: Error?
    var saveAccountError: Error?
    var updateAccountError: Error?
    var deleteAccountError: Error?
    var deleteAllAccountsError: Error?

    // MARK: - Call tracking
    var saveAccountCallCount = 0
    var updateAccountCallCount = 0
    var deleteAccountCallCount = 0
    var deleteAllAccountsCallCount = 0

    // MARK: - AccountRepositoryProtocol

    func fetchAccount(byId id: String) async throws -> Account? {
        if let error = fetchAccountByIdError { throw error }
        return storedAccounts.first { $0.accountId == id }
    }

    func fetchAllAccounts() async throws -> [Account] {
        if let error = fetchAllAccountsError { throw error }
        return storedAccounts
    }

    func fetchAllAccountsSync() throws -> [Account] {
        if let error = fetchAllAccountsError { throw error }
        return storedAccounts
    }

    func saveAccount(_ account: Account) async throws {
        saveAccountCallCount += 1
        if let error = saveAccountError { throw error }
        storedAccounts.append(account)
    }

    func updateAccount(_ account: Account) async throws {
        updateAccountCallCount += 1
        if let error = updateAccountError { throw error }
        if let index = storedAccounts.firstIndex(where: { $0.accountId == account.accountId }) {
            storedAccounts[index] = account
        }
    }

    func deleteAccount(byId id: String) async throws {
        deleteAccountCallCount += 1
        if let error = deleteAccountError { throw error }
        storedAccounts.removeAll { $0.accountId == id }
    }

    func deleteAllAccounts() async throws {
        deleteAllAccountsCallCount += 1
        if let error = deleteAllAccountsError { throw error }
        storedAccounts.removeAll()
    }

    // MARK: - Helpers

    func seed(_ account: Account) {
        storedAccounts.append(account)
    }

    func reset() {
        storedAccounts.removeAll()
        fetchAccountByIdError = nil
        fetchAllAccountsError = nil
        saveAccountError = nil
        updateAccountError = nil
        deleteAccountError = nil
        deleteAllAccountsError = nil
        saveAccountCallCount = 0
        updateAccountCallCount = 0
        deleteAccountCallCount = 0
        deleteAllAccountsCallCount = 0
    }
}
