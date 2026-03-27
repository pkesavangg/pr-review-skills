//
//  BabyServiceProtocol.swift
//  meApp
//

import Combine
import Foundation

/// Manages baby records in the local SwiftData Baby table.
/// ProductTypeStore subscribes to babiesPublisher to rebuild the dropdown
/// whenever a baby is added, removed, or renamed.
///
/// NOTE: This is a preliminary service API. It will evolve as baby-related
/// features are implemented in future tasks (MA-3474, MA-3471).
@MainActor
protocol BabyServiceProtocol: AnyObject {
    /// Emits the full list of Baby records whenever it changes.
    var babiesPublisher: Published<[Baby]>.Publisher { get }

    /// Synchronous last-known value (for ProductTypeStore.rebuild()).
    var currentBabies: [Baby] { get }

    /// Save a new baby (called at the end of baby scale setup flow).
    func saveBaby(name: String, accountId: String, deviceId: String?) async throws -> Baby

    /// Update a baby's name.
    func updateBaby(_ baby: Baby, name: String) async throws

    /// Delete a baby record.
    func deleteBaby(_ baby: Baby) async throws

    /// Load babies for the given account from SwiftData.
    func loadBabies(for accountId: String) async throws
}
