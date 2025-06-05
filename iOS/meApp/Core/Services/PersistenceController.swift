//
//  PersistenceController.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//


import SwiftData

/// `PersistenceController` is a singleton class that provides a shared SwiftData `ModelContainer` and `ModelContext`
/// to be used throughout the app. This ensures all data models operate on the same persistent store,
/// avoiding schema conflicts and enabling consistent data access.
///
/// The container is initialized with a combined schema (e.g., `[Account.self]`), and is configured
/// for persistent on-disk storage (`isStoredInMemoryOnly: false`). The shared context is scoped to
/// the main actor, making it safe for use in SwiftUI and other main-thread-bound components.
///
/// Use `PersistenceController.shared.context` to perform fetch, insert, and save operations across repositories.

/// ## Usage Example:
/// ```swift
/// // Accessing the shared context
/// let context = PersistenceController.shared.context
///
/// // Using in a repository
/// final class AccountRepository {
///     private let context = PersistenceController.shared.context
///
///     func fetchAccounts() async throws -> [Account] {
///         let descriptor = FetchDescriptor<Account>()
///         return try context.fetch(descriptor)
///     }
/// }

@MainActor
final class PersistenceController {
    static let shared = PersistenceController()

    let container: ModelContainer
    let context: ModelContext

    private init() {
        let schema = Schema([Account.self, Device.self, BathScale.self, Entry.self, R4ScalePreference.self, BathScaleMetric.self, DeviceMetaData.self, BathScaleEntry.self, LogEntry.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        do {
            self.container = try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Failed to initialize ModelContainer: \(error.localizedDescription)")
        }
        self.context = ModelContext(container)
    }
}
