//
//  MockBabyService.swift
//  meAppTests
//

import Combine
import Foundation
@testable import meApp

@MainActor
final class MockBabyService: BabyServiceProtocol {
    @Published var babies: [Baby] = []

    var babiesPublisher: Published<[Baby]>.Publisher { $babies }
    var currentBabies: [Baby] { babies }

    // MARK: - Call Tracking

    private(set) var saveBabyCalls = 0
    private(set) var updateBabyCalls = 0
    private(set) var updateBabyProfileCalls = 0
    private(set) var deleteBabyCalls = 0
    private(set) var loadBabiesCalls = 0

    private(set) var lastSavedName: String?
    private(set) var lastSavedAccountId: String?
    private(set) var lastSavedDeviceId: String?
    private(set) var lastDeletedBaby: Baby?
    private(set) var lastUpdatedBaby: Baby?
    private(set) var lastLoadAccountId: String?

    // MARK: - Configurable Results

    var saveBabyError: Error?
    var updateBabyError: Error?
    var updateBabyProfileError: Error?
    var deleteBabyError: Error?
    var loadBabiesError: Error?

    // MARK: - Protocol Methods

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
        saveBabyCalls += 1
        lastSavedName = name
        lastSavedAccountId = accountId
        lastSavedDeviceId = deviceId
        if let error = saveBabyError { throw error }
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
        return baby
    }

    func updateBaby(_ baby: Baby, name: String) async throws {
        updateBabyCalls += 1
        lastUpdatedBaby = baby
        if let error = updateBabyError { throw error }
        baby.name = name
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
        updateBabyProfileCalls += 1
        lastUpdatedBaby = baby
        if let error = updateBabyProfileError { throw error }
        baby.name = name
        baby.birthday = birthday
        baby.biologicalSex = biologicalSex
        baby.birthLengthInches = birthLengthInches
        baby.birthWeightLbs = birthWeightLbs
        baby.birthWeightOz = birthWeightOz
    }

    func deleteBaby(_ baby: Baby) async throws {
        deleteBabyCalls += 1
        lastDeletedBaby = baby
        if let error = deleteBabyError { throw error }
    }

    func loadBabies(for accountId: String) async throws {
        loadBabiesCalls += 1
        lastLoadAccountId = accountId
        if let error = loadBabiesError { throw error }
    }

    private(set) var syncBabiesCalls = 0
    func syncBabies(for accountId: String) async {
        syncBabiesCalls += 1
        lastLoadAccountId = accountId
    }
}
