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

    // swiftlint:disable:next function_parameter_count
    func saveBaby(
        name: String,
        accountId: String,
        deviceId: String?,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) async throws -> Baby

    /// Update a baby's name.
    func updateBaby(_ baby: Baby, name: String) async throws

    // swiftlint:disable:next function_parameter_count
    func updateBabyProfile(
        _ baby: Baby,
        name: String,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) async throws

    /// Delete a baby record.
    func deleteBaby(_ baby: Baby) async throws

    /// Load babies for the given account from SwiftData.
    func loadBabies(for accountId: String) async throws

    /// Reconciles locally-queued baby profile changes with the server (MOB-1527): pushes pending
    /// deletes, creates (with client-id → server-id remapping and lost-reply dedupe), and edits,
    /// then merges the server list into the local store without dropping not-yet-synced records.
    /// Runs before entry sync so an offline baby has its server id before its entries are pushed.
    /// - Parameter accountId: The account whose baby profiles to reconcile.
    func syncBabies(for accountId: String) async
}

/// Convenience extension preserving the original 3-parameter signature.
extension BabyServiceProtocol {
    func saveBaby(name: String, accountId: String, deviceId: String?) async throws -> Baby {
        try await saveBaby(
            name: name,
            accountId: accountId,
            deviceId: deviceId,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
    }
}
