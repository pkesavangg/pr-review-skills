import Foundation
import SwiftData

/// Concrete implementation of AccountRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Account entities in a thread-safe manner.
@MainActor
final class AccountRepository: AccountRepositoryProtocol {
    // MARK: - Properties
    private let context: ModelContext = PersistenceController.shared.context


    /// Fetches an account by its unique ID.
    /// - Parameter id: The ID of the account to fetch.
    /// - Returns: The Account object, or nil if not found.
    func fetchAccount(byId id: String) async throws -> Account? {
        let descriptor = FetchDescriptor<Account>(predicate: #Predicate { $0.accountId == id })
        return try context.fetch(descriptor).first
    }

    /// Fetches all accounts stored locally.
    /// - Returns: An array of all Account objects.
    func fetchAllAccounts() async throws -> [Account] {
        let descriptor = FetchDescriptor<Account>()
        return try context.fetch(descriptor)
    }

    /// Saves a new account to the local data store.
    /// - Parameter account: The Account object to save.
    func saveAccount(_ account: Account) async throws {
        // Remove any existing accounts that have the same email to avoid duplicates
        let existingEmail = account.email // capture email as a value to use in the predicate
        let duplicateDescriptor = FetchDescriptor<Account>(predicate: #Predicate<Account> { $0.email == existingEmail })
        let duplicates = try context.fetch(duplicateDescriptor)
        for dup in duplicates {
            context.delete(dup)
        }
        // Now insert the new/updated account and persist the context
        context.insert(account)
        try context.save()
    }

    /// Updates an existing account in the local data store.
    /// - Parameter account: The updated Account object.
    func updateAccount(_ account: Account) async throws {
        // SwiftData tracks changes automatically; just save context
        try context.save()
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
} 
