import Foundation
import SwiftData

/// Concrete implementation of AccountRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Account entities in a thread-safe manner.
@MainActor
final class AccountRepository: AccountRepositoryProtocol {
    // MARK: - Properties
    private let container: ModelContainer
    private let context: ModelContext

    /// Initializes the repository with a private SwiftData container.
    init() {
        let schema = Schema([Account.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        self.container = try! ModelContainer(for: schema, configurations: [config])
        self.context = ModelContext(container)
    }


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
