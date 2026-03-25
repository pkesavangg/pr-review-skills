//
//  BabyServiceProtocol.swift
//  meApp
//

import Combine
import Foundation

/// Manages baby records in the local SwiftData Baby table.
/// ProductTypeStore subscribes to babiesPublisher to rebuild the dropdown
/// whenever a baby is added, removed, or renamed.
@MainActor
protocol BabyServiceProtocol: AnyObject {
    /// Emits the full list of Baby records whenever it changes.
    var babiesPublisher: Published<[Baby]>.Publisher { get }

    /// Synchronous last-known value (for ProductTypeStore.rebuild()).
    var currentBabies: [Baby] { get }

    /// Save a new baby with full profile fields (called at the end of baby scale setup flow).
    func saveBaby(name: String, accountId: String, deviceId: String?,
                  birthday: Date?, biologicalSex: String?,
                  birthLengthInches: Double?, birthWeightLbs: Double?, birthWeightOz: Double?) async throws -> Baby

    /// Update a baby's name.
    func updateBaby(_ baby: Baby, name: String) async throws

    /// Delete a baby record.
    func deleteBaby(_ baby: Baby) async throws

    /// Load babies for the given account from SwiftData.
    func loadBabies(for accountId: String) async throws
}

/// Convenience extension preserving the original 3-parameter signature.
extension BabyServiceProtocol {
    func saveBaby(name: String, accountId: String, deviceId: String?) async throws -> Baby {
        try await saveBaby(name: name, accountId: accountId, deviceId: deviceId,
                           birthday: nil, biologicalSex: nil,
                           birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil)
    }
}
