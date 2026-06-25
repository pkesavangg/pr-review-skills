//
//  BabyService.swift
//  meApp
//

import Combine
import Foundation
import SwiftData

/// Concrete implementation of BabyServiceProtocol.
///
/// Backs the local SwiftData `Baby` table with the remote Baby Profile API (MOB-386):
/// mutations write to the server first, then mirror locally; `loadBabies(for:)` pulls the
/// server list and merges it into the local store. ProductTypes are kept in sync — `"baby"`
/// is appended on first create and stripped again once the last baby profile is deleted.
@MainActor
final class BabyService: ObservableObject, BabyServiceProtocol {
    static let shared = BabyService()

    @Injector private var accountService: AccountServiceProtocol

    @Published var babies: [Baby] = []

    var babiesPublisher: Published<[Baby]>.Publisher { $babies }
    var currentBabies: [Baby] { babies }

    private let context = PersistenceController.shared.context
    private let remoteRepo: BabyRepositoryAPIProtocol
    private let tag = "BabyService"

    init(remoteRepo: BabyRepositoryAPIProtocol? = nil) {
        self.remoteRepo = remoteRepo ?? BabyRepositoryAPI()
    }


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
    ) async throws -> Baby {
        // Create remotely first so the server assigns the canonical id and adds "baby" to productTypes.
        let request = BabyRequest(
            name: name,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
        let response = try await remoteRepo.createBaby(request)

        let baby = Baby(
            id: response.id,
            accountId: accountId,
            name: name,
            deviceId: deviceId,
            isSynced: true,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
        context.insert(baby)
        try context.save()
        try reloadLocalBabies(for: accountId)
        try await appendBabyProductTypeIfNeeded()
        return baby
    }

    func updateBaby(_ baby: Baby, name: String) async throws {
        let request = BabyRequest(
            name: name,
            birthday: baby.birthday,
            biologicalSex: baby.biologicalSex,
            birthLengthInches: baby.birthLengthInches,
            birthWeightLbs: baby.birthWeightLbs,
            birthWeightOz: baby.birthWeightOz
        )
        _ = try await remoteRepo.updateBaby(baby.id, request)
        baby.name = name
        baby.isSynced = true
        try context.save()
        try reloadLocalBabies(for: baby.accountId)
    }

    // swiftlint:disable:next function_parameter_count
    func updateBabyProfile(
        _ baby: Baby,
        name: String,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) async throws {
        let request = BabyRequest(
            name: name,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
        _ = try await remoteRepo.updateBaby(baby.id, request)
        baby.name = name
        baby.birthday = birthday
        baby.biologicalSex = biologicalSex
        baby.birthLengthInches = birthLengthInches
        baby.birthWeightLbs = birthWeightLbs
        baby.birthWeightOz = birthWeightOz
        baby.isSynced = true
        try context.save()
        try reloadLocalBabies(for: baby.accountId)
    }

    func deleteBaby(_ baby: Baby) async throws {
        let accountId = baby.accountId
        try await remoteRepo.deleteBaby(baby.id)
        context.delete(baby)
        try context.save()
        try reloadLocalBabies(for: accountId)
        try await removeBabyProductTypeIfLast()
    }

    func loadBabies(for accountId: String) async throws {
        // Pull the server list and merge it into the local store. Best-effort: an offline /
        // failed fetch falls back to whatever is cached locally so the UI still renders.
        do {
            let remoteBabies = try await remoteRepo.listBabies()
            try mergeRemoteBabies(remoteBabies, accountId: accountId)
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "Failed to fetch remote babies for accountId=\(accountId): \(error.localizedDescription)"
            )
        }
        try reloadLocalBabies(for: accountId)
    }

    // MARK: - Local Store Helpers

    /// Reloads `babies` from the local SwiftData store for the given account (no network).
    private func reloadLocalBabies(for accountId: String) throws {
        let predicate = #Predicate<Baby> { baby in
            baby.accountId == accountId
        }
        let descriptor = FetchDescriptor<Baby>(
            predicate: predicate,
            sortBy: [SortDescriptor(\.name)]
        )
        babies = try context.fetch(descriptor)
    }

    /// Upserts server baby profiles into the local store, keyed by id,
    /// then tombstone-deletes any local babies for this account that are
    /// no longer present on the server (e.g. removed from another device).
    private func mergeRemoteBabies(_ responses: [BabyResponse], accountId: String) throws {
        let remoteIds = Set(responses.map { $0.id })
        for response in responses {
            let babyId = response.id
            let descriptor = FetchDescriptor<Baby>(
                predicate: #Predicate<Baby> { $0.id == babyId && $0.accountId == accountId }
            )
            if let existing = try context.fetch(descriptor).first {
                existing.name = response.name
                existing.birthday = response.birthdayDate
                existing.biologicalSex = response.sex
                existing.birthLengthInches = response.birthLengthInchesValue
                if let weight = response.birthWeightLbsOz {
                    existing.birthWeightLbs = weight.lbs
                    existing.birthWeightOz = weight.oz
                }
                existing.isSynced = true
            } else {
                context.insert(response.toBaby(accountId: accountId))
            }
        }
        // Delete local babies for this account that were not in the server response.
        let staleDescriptor = FetchDescriptor<Baby>(
            predicate: #Predicate<Baby> { $0.accountId == accountId }
        )
        let localBabies = try context.fetch(staleDescriptor)
        for baby in localBabies where !remoteIds.contains(baby.id) {
            context.delete(baby)
        }
        try context.save()
    }

    // MARK: - ProductTypes Sync

    /// Appends "baby" to the active account's productTypes if not already present.
    private func appendBabyProductTypeIfNeeded() async throws {
        guard let snapshot = accountService.activeAccount,
              !snapshot.productTypes.contains("baby") else { return }
        try await accountService.updateProductTypes(snapshot.productTypes + ["baby"])
        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Appended baby to productTypes for accountId=\(snapshot.accountId)"
        )
    }

    /// Removes "baby" from the active account's productTypes once no baby profiles remain.
    private func removeBabyProductTypeIfLast() async throws {
        guard babies.isEmpty,
              let snapshot = accountService.activeAccount,
              snapshot.productTypes.contains("baby") else { return }
        try await accountService.updateProductTypes(snapshot.productTypes.filter { $0 != "baby" })
        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Removed baby from productTypes for accountId=\(snapshot.accountId)"
        )
    }

}
