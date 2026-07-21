//
//  AccountRepositoryProtocol.swift
//  meApp
//
//  Created by Barath Chittibabu on 29/05/25.
//

/// Protocol for abstracting all local (or general) account data access and operations.
///
/// This protocol defines the contract for interacting with account data sources (e.g., local database, cache).
/// It includes CRUD operations for accounts, as well as bulk deletion.
@MainActor
protocol AccountRepositoryProtocol {
    /// Fetches an account by its unique ID.
    /// - Parameter id: The ID of the account to fetch.
    /// - Returns: The Account object, or nil if not found.
    func fetchAccount(byId id: String) async throws -> Account?

    /// Fetches all accounts stored locally.
    /// - Returns: An array of all Account objects.
    func fetchAllAccounts() async throws -> [Account]
    
    /// Synchronously fetches all accounts for early initialization (use sparingly).
    /// **Blocks the caller thread; invoke only during controlled startup on the main thread or from a background context known to be safe for the underlying storage.**
    /// - Returns: An array of all Account objects.
    func fetchAllAccountsSync() throws -> [Account]

    /// Saves a new account to the local data store.
    /// - Parameter account: The Account object to save.
    func saveAccount(_ account: Account) async throws

    /// Updates an existing account in the local data store.
    /// - Parameter account: The updated Account object.
    func updateAccount(_ account: Account) async throws

    /// Clears the active flag on every account except the given one, in a single transaction.
    ///
    /// Batched replacement for deactivating other accounts one-at-a-time (one disk commit each).
    /// - Parameter activeId: The account ID to keep active — its flag is not modified here.
    func deactivateAccounts(exceptId activeId: String) async throws

    /// Deletes an account by its unique ID.
    /// - Parameter id: The ID of the account to delete.
    func deleteAccount(byId id: String) async throws

    /// Deletes all accounts from the local data store.
    func deleteAllAccounts() async throws
}
