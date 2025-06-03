//
//  DataStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//


import SwiftData

/// `DataStore` is a singleton class that provides a shared SwiftData `ModelContainer` and `ModelContext`
/// to be used throughout the app. This ensures all data models operate on the same persistent store,
/// avoiding schema conflicts and enabling consistent data access.
///
/// The container is initialized with a combined schema (e.g., `[Account.self]`), and is configured
/// for persistent on-disk storage (`isStoredInMemoryOnly: false`). The shared context is scoped to
/// the main actor, making it safe for use in SwiftUI and other main-thread-bound components.
///
/// Use `DataStore.shared.context` to perform fetch, insert, and save operations across repositories.

/// ## Usage Example:
/// ```swift
/// // Accessing the shared context
/// let context = DataStore.shared.context
///
/// // Using in a repository
/// final class AccountRepository {
///     private let context = DataStore.shared.context
///
///     func fetchAccounts() async throws -> [Account] {
///         let descriptor = FetchDescriptor<Account>()
///         return try context.fetch(descriptor)
///     }
/// }

@MainActor
final class DataStore {
    static let shared = DataStore()

    let container: ModelContainer
    let context: ModelContext

    private init() {
        let schema = Schema([Account.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        self.container = try! ModelContainer(for: schema, configurations: [config])
        self.context = ModelContext(container)
    }
}
