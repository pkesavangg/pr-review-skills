//
//  BabyServiceTests.swift
//  meAppTests
//
//  MOB-386: BabyService now wires the remote Baby Profile API. Tests inject a
//  MockBabyRepositoryAPI and a MockAccountService, persisting locally to the shared context.
//

import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BabyServiceTests {
    private let context = PersistenceController.shared.context

    // MARK: - SUT

    private func makeSUT(
        productTypes: [String] = ["myWeight"]
    ) -> (BabyService, MockBabyRepositoryAPI, MockAccountService, String) {
        let accountId = "test-account-\(UUID().uuidString)"
        let repo = MockBabyRepositoryAPI()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: accountId, email: "baby@example.com", isActiveAccount: true, productTypes: productTypes
        )

        TestDependencyContainer.reset()
        DependencyContainer.shared.register(account as AccountServiceProtocol)

        let sut = BabyService(remoteRepo: repo)
        return (sut, repo, account, accountId)
    }

    private func cleanup(_ sut: BabyService, accountId: String) async throws {
        try await sut.loadBabies(for: accountId)
        for baby in sut.currentBabies {
            context.delete(baby)
        }
        try context.save()
    }

    // MARK: - saveBaby

    @Test("saveBaby: creates remotely, persists locally with server id, publishes list")
    func saveBabyCreatesRemotelyAndLocally() async throws {
        let (sut, repo, _, accountId) = makeSUT()
        repo.createResult = BabyResponse(
            id: "srv-1", name: "Lily", birthdate: nil, sex: nil,
            birthWeightDecigrams: nil, birthLengthMillimeters: nil
        )

        let baby = try await sut.saveBaby(
            name: "Lily", accountId: accountId, deviceId: "dev-1",
            birthday: nil, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )

        #expect(repo.createCalls == 1)
        #expect(repo.lastCreateRequest?.name == "Lily")
        #expect(baby.id == "srv-1")
        #expect(baby.isSynced == true)
        #expect(sut.currentBabies.contains { $0.id == "srv-1" })

        try await cleanup(sut, accountId: accountId)
    }

    @Test("saveBaby: converts profile fields into the wire request (decigrams / mm / date)")
    func saveBabyConvertsProfileFields() async throws {
        let (sut, repo, _, accountId) = makeSUT()
        let birthday = Date(timeIntervalSince1970: 1_700_000_000)

        _ = try await sut.saveBaby(
            name: "Max", accountId: accountId, deviceId: nil,
            birthday: birthday, biologicalSex: "male",
            birthLengthInches: 20.0, birthWeightLbs: 7.0, birthWeightOz: 8.0
        )

        let request = try #require(repo.lastCreateRequest)
        #expect(request.sex == "male")
        #expect(request.birthdate != nil)
        #expect(request.birthLengthMillimeters == ConversionTools.convertBabyInchesToMm(20.0))
        #expect(request.birthWeightDecigrams == ConversionTools.convertBabyLbsOzToDecigrams(lbs: 7, oz: 8.0))

        try await cleanup(sut, accountId: accountId)
    }

    @Test("saveBaby: appends 'baby' to productTypes when absent")
    func saveBabyAppendsProductType() async throws {
        let (sut, _, account, accountId) = makeSUT(productTypes: ["myWeight"])

        _ = try await sut.saveBaby(
            name: "Lily", accountId: accountId, deviceId: nil,
            birthday: nil, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )

        #expect(account.updateProductTypesCalls == 1)
        #expect(account.lastUpdatedProductTypes?.contains("baby") == true)

        try await cleanup(sut, accountId: accountId)
    }

    @Test("saveBaby: remote failure propagates and skips local persistence")
    func saveBabyRemoteFailure() async {
        let (sut, repo, _, accountId) = makeSUT()
        repo.createError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            _ = try await sut.saveBaby(
                name: "Lily", accountId: accountId, deviceId: nil,
                birthday: nil, biologicalSex: nil,
                birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
            )
        }
        #expect(sut.currentBabies.isEmpty)
    }

    // MARK: - updateBabyProfile

    @Test("updateBabyProfile: PUTs to remote and updates local fields")
    func updateBabyProfileUpdatesRemoteAndLocal() async throws {
        let (sut, repo, _, accountId) = makeSUT()
        let baby = try await sut.saveBaby(
            name: "Emma", accountId: accountId, deviceId: nil,
            birthday: nil, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )
        let newBirthday = Date(timeIntervalSince1970: 1_600_000_000)

        try await sut.updateBabyProfile(
            baby, name: "Emma Updated", birthday: newBirthday, biologicalSex: "female",
            birthLengthInches: 19.0, birthWeightLbs: 6.0, birthWeightOz: 12.0
        )

        #expect(repo.updateCalls == 1)
        #expect(repo.lastUpdateId == baby.id)
        #expect(repo.lastUpdateRequest?.name == "Emma Updated")
        #expect(baby.name == "Emma Updated")
        #expect(baby.biologicalSex == "female")

        try await cleanup(sut, accountId: accountId)
    }

    // MARK: - deleteBaby

    @Test("deleteBaby: DELETEs remotely, removes locally, drops 'baby' when last")
    func deleteBabyRemovesAndDropsProductType() async throws {
        let (sut, repo, account, accountId) = makeSUT(productTypes: ["myWeight", "baby"])
        let baby = try await sut.saveBaby(
            name: "ToDelete", accountId: accountId, deviceId: nil,
            birthday: nil, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )
        let babyId = baby.id

        try await sut.deleteBaby(baby)

        #expect(repo.deleteCalls == 1)
        #expect(repo.lastDeletedId == babyId)
        #expect(!sut.currentBabies.contains { $0.id == babyId })
        // Last baby removed → "baby" stripped from productTypes.
        #expect(account.lastUpdatedProductTypes?.contains("baby") == false)
    }

    // MARK: - loadBabies

    @Test("loadBabies: merges the remote list into the local store")
    func loadBabiesMergesRemote() async throws {
        let (sut, repo, _, accountId) = makeSUT()
        repo.listResult = [
            BabyResponse(id: "remote-1", name: "Remote Baby", birthdate: "2026-03-15",
                         sex: "female", birthWeightDecigrams: 32500, birthLengthMillimeters: 510)
        ]

        try await sut.loadBabies(for: accountId)

        #expect(repo.listCalls == 1)
        let merged = try #require(sut.currentBabies.first { $0.id == "remote-1" })
        #expect(merged.name == "Remote Baby")
        #expect(merged.biologicalSex == "female")
        #expect(merged.isSynced == true)

        try await cleanup(sut, accountId: accountId)
    }

    @Test("loadBabies: remote failure falls back to local cache")
    func loadBabiesRemoteFailureFallsBack() async throws {
        let (sut, repo, _, accountId) = makeSUT()
        let baby = try await sut.saveBaby(
            name: "Cached", accountId: accountId, deviceId: nil,
            birthday: nil, biologicalSex: nil,
            birthLengthInches: nil, birthWeightLbs: nil, birthWeightOz: nil
        )
        repo.listError = HTTPError.noInternet

        try await sut.loadBabies(for: accountId)

        // Despite the remote failure, the locally cached baby still loads.
        #expect(sut.currentBabies.contains { $0.id == baby.id })

        try await cleanup(sut, accountId: accountId)
    }
}
