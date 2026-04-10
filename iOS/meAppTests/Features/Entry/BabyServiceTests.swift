//
//  BabyServiceTests.swift
//  meAppTests
//

import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BabyServiceTests {
    private let sut = BabyService.shared
    private let context = PersistenceController.shared.context
    private let testAccountId = "test-account-\(UUID().uuidString)"

    // MARK: - Helpers

    private func cleanupBabies() async throws {
        try await sut.loadBabies(for: testAccountId)
        for baby in sut.currentBabies {
            context.delete(baby)
        }
        try context.save()
    }

    // MARK: - saveBaby

    @Test("saveBaby success: creates baby and publishes updated list")
    func saveBabyCreatesAndPublishes() async throws {
        defer { try? Task.detached { @MainActor in try await self.cleanupBabies() }.cancel() }

        let baby = try await sut.saveBaby(
            name: "Lily",
            accountId: testAccountId,
            deviceId: "dev-1",
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )

        #expect(baby.name == "Lily")
        #expect(baby.accountId == testAccountId)
        #expect(baby.deviceId == "dev-1")
        #expect(sut.currentBabies.contains { $0.id == baby.id })

        try await cleanupBabies()
    }

    @Test("saveBaby success: stores optional profile fields")
    func saveBabyWithProfileFields() async throws {
        let birthday = Date(timeIntervalSince1970: 1_700_000_000)

        let baby = try await sut.saveBaby(
            name: "Max",
            accountId: testAccountId,
            deviceId: nil,
            birthday: birthday,
            biologicalSex: "male",
            birthLengthInches: 20.5,
            birthWeightLbs: 7.0,
            birthWeightOz: 8.0
        )

        #expect(baby.birthday == birthday)
        #expect(baby.biologicalSex == "male")
        #expect(baby.birthLengthInches == 20.5)
        #expect(baby.birthWeightLbs == 7.0)
        #expect(baby.birthWeightOz == 8.0)

        try await cleanupBabies()
    }

    // MARK: - updateBaby

    @Test("updateBaby success: updates baby name and reloads list")
    func updateBabyName() async throws {
        let baby = try await sut.saveBaby(
            name: "OldName",
            accountId: testAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )

        try await sut.updateBaby(baby, name: "NewName")

        #expect(baby.name == "NewName")
        #expect(sut.currentBabies.contains { $0.name == "NewName" })

        try await cleanupBabies()
    }

    // MARK: - updateBabyProfile

    @Test("updateBabyProfile success: updates all profile fields")
    func updateBabyProfile() async throws {
        let baby = try await sut.saveBaby(
            name: "Emma",
            accountId: testAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )

        let newBirthday = Date(timeIntervalSince1970: 1_600_000_000)
        try await sut.updateBabyProfile(
            baby,
            name: "Emma Updated",
            birthday: newBirthday,
            biologicalSex: "female",
            birthLengthInches: 19.0,
            birthWeightLbs: 6.0,
            birthWeightOz: 12.0
        )

        #expect(baby.name == "Emma Updated")
        #expect(baby.birthday == newBirthday)
        #expect(baby.biologicalSex == "female")
        #expect(baby.birthLengthInches == 19.0)
        #expect(baby.birthWeightLbs == 6.0)
        #expect(baby.birthWeightOz == 12.0)

        try await cleanupBabies()
    }

    // MARK: - deleteBaby

    @Test("deleteBaby success: removes baby and updates list")
    func deleteBabyRemoves() async throws {
        let baby = try await sut.saveBaby(
            name: "ToDelete",
            accountId: testAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
        let babyId = baby.id

        try await sut.deleteBaby(baby)

        #expect(!sut.currentBabies.contains { $0.id == babyId })
    }

    // MARK: - loadBabies

    @Test("loadBabies success: loads babies for the given account")
    func loadBabiesForAccount() async throws {
        let baby = try await sut.saveBaby(
            name: "LoadTest",
            accountId: testAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )

        try await sut.loadBabies(for: testAccountId)

        #expect(sut.currentBabies.contains { $0.id == baby.id })
        #expect(sut.currentBabies.allSatisfy { $0.accountId == testAccountId })

        try await cleanupBabies()
    }

    @Test("loadBabies success: filters by accountId and excludes other accounts")
    func loadBabiesFiltersByAccount() async throws {
        let otherAccountId = "other-account-\(UUID().uuidString)"

        let myBaby = try await sut.saveBaby(
            name: "MyBaby",
            accountId: testAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
        let otherBaby = try await sut.saveBaby(
            name: "OtherBaby",
            accountId: otherAccountId,
            deviceId: nil,
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )

        try await sut.loadBabies(for: testAccountId)

        #expect(sut.currentBabies.contains { $0.id == myBaby.id })
        #expect(!sut.currentBabies.contains { $0.id == otherBaby.id })

        // Cleanup both accounts
        context.delete(otherBaby)
        try context.save()
        try await cleanupBabies()
    }
}
