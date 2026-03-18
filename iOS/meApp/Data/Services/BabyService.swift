//
//  BabyService.swift
//  meApp
//

import Combine
import Foundation
import SwiftData

/// Concrete implementation of BabyServiceProtocol.
/// Thin wrapper over SwiftData CRUD on the Baby model.
///
/// NOTE: This is a preliminary implementation. The service API will evolve
/// as baby-related features are implemented in future tasks.
@MainActor
final class BabyService: ObservableObject, BabyServiceProtocol {
    static let shared = BabyService()

    @Published var babies: [Baby] = []

    var babiesPublisher: Published<[Baby]>.Publisher { $babies }
    var currentBabies: [Baby] { babies }

    private let context = PersistenceController.shared.context

    private init() {}

    func saveBaby(name: String, accountId: String, deviceId: String?) async throws -> Baby {
        let baby = Baby(accountId: accountId, name: name, deviceId: deviceId)
        context.insert(baby)
        try context.save()
        try await loadBabies(for: accountId)
        return baby
    }

    func updateBaby(_ baby: Baby, name: String) async throws {
        baby.name = name
        try context.save()
        try await loadBabies(for: baby.accountId)
    }

    func deleteBaby(_ baby: Baby) async throws {
        let accountId = baby.accountId
        context.delete(baby)
        try context.save()
        try await loadBabies(for: accountId)
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
}
