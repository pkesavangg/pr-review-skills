//
//  BabyService.swift
//  meApp
//

import Combine
import Foundation
import SwiftData

/// Concrete implementation of BabyServiceProtocol.
/// Thin wrapper over SwiftData CRUD on the Baby model.
@MainActor
final class BabyService: ObservableObject, BabyServiceProtocol {
    static let shared = BabyService()

    @Injector private var accountService: AccountServiceProtocol

    @Published var babies: [Baby] = []

    var babiesPublisher: Published<[Baby]>.Publisher { $babies }
    var currentBabies: [Baby] { babies }

    private let context = PersistenceController.shared.context
    private let tag = "BabyService"

    private init() {}

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
        let baby = Baby(
            accountId: accountId,
            name: name,
            deviceId: deviceId,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
        context.insert(baby)
        try context.save()
        try await loadBabies(for: accountId)
        appendBabyProductTypeIfNeeded()
        return baby
    }

    func updateBaby(_ baby: Baby, name: String) async throws {
        baby.name = name
        try context.save()
        try await loadBabies(for: baby.accountId)
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
        baby.name = name
        baby.birthday = birthday
        baby.biologicalSex = biologicalSex
        baby.birthLengthInches = birthLengthInches
        baby.birthWeightLbs = birthWeightLbs
        baby.birthWeightOz = birthWeightOz
        try context.save()
        try await loadBabies(for: baby.accountId)
    }

    func deleteBaby(_ baby: Baby) async throws {
        let accountId = baby.accountId
        context.delete(baby)
        try context.save()
        try await loadBabies(for: accountId)
        removeBabyProductTypeIfLastDeleted()
    }

    func loadBabies(for accountId: String) async throws {
        let predicate = #Predicate<Baby> { baby in
            baby.accountId == accountId
        }
        let descriptor = FetchDescriptor<Baby>(
            predicate: predicate,
            sortBy: [SortDescriptor(\.name)]
        )
        babies = try context.fetch(descriptor)
    }

    // MARK: - ProductTypes Sync

    /// Appends "baby" to the active account's productTypes if not already present.
    private func appendBabyProductTypeIfNeeded() {
        guard let account = accountService.activeAccount,
              !account.productTypes.contains("baby") else { return }
        account.productTypes.append("baby")
        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Appended baby to productTypes for accountId=\(account.accountId)"
        )
    }

    /// Removes "baby" from the active account's productTypes when no babies remain.
    private func removeBabyProductTypeIfLastDeleted() {
        guard let account = accountService.activeAccount,
              babies.isEmpty,
              account.productTypes.contains("baby") else { return }
        account.productTypes.removeAll { $0 == "baby" }
        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Removed baby from productTypes for accountId=\(account.accountId)"
        )
    }
}
