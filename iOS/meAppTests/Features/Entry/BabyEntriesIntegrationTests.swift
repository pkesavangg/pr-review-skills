//
//  BabyEntriesIntegrationTests.swift
//  meAppTests
//
//  MOB-386 — Baby Profile CRUD endpoints, request/response conversions, baby entry request
//  building, baby read mapping, and EntryService baby push + CSV wiring.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BabyEntriesIntegrationTests {

    // MARK: - BabyRepositoryAPI endpoints

    private func makeRepo() -> (BabyRepositoryAPI, MockHTTPClient) {
        let http = MockHTTPClient()
        return (BabyRepositoryAPI(httpClient: http), http)
    }

    @Test("listBabies: GET /baby/ with auth")
    func listBabies() async throws {
        let (sut, http) = makeRepo()
        http.getResult = [BabyResponse(
            id: "b1",
            name: "Emma",
            birthdate: nil,
            sex: nil,
            birthWeightDecigrams: nil,
            birthLengthMillimeters: nil
        )]

        let result = try await sut.listBabies()

        #expect(http.lastGetNeedsAuth == true)
        guard case .baby = http.lastGetEndpoint else { Issue.record("Expected .baby"); return }
        #expect(result.count == 1)
        #expect(result.first?.name == "Emma")
    }

    @Test("createBaby: POST /baby/ with request body")
    func createBaby() async throws {
        let (sut, http) = makeRepo()
        http.sendResult = BabyResponse(
            id: "b1",
            name: "Emma",
            birthdate: "2026-03-15",
            sex: "female",
            birthWeightDecigrams: 32500,
            birthLengthMillimeters: 510
        )

        let response = try await sut.createBaby(
            BabyRequest(
                name: "Emma",
                birthdate: "2026-03-15",
                sex: "female",
                birthWeightDecigrams: 32500,
                birthLengthMillimeters: 510
            )
        )

        #expect(http.lastSendMethod == .post)
        guard case .baby = http.lastSendEndpoint else { Issue.record("Expected .baby"); return }
        #expect(response.id == "b1")
    }

    @Test("updateBaby: PUT /baby/:id with request body")
    func updateBaby() async throws {
        let (sut, http) = makeRepo()
        http.sendResult = BabyResponse(
            id: "b1",
            name: "Renamed",
            birthdate: nil,
            sex: nil,
            birthWeightDecigrams: nil,
            birthLengthMillimeters: nil
        )

        _ = try await sut.updateBaby("b1", BabyRequest(
            name: "Renamed",
            birthdate: nil,
            sex: nil,
            birthWeightDecigrams: nil,
            birthLengthMillimeters: nil
        ))

        #expect(http.lastSendMethod == .put)
        guard case .babyId(let id) = http.lastSendEndpoint else { Issue.record("Expected .babyId"); return }
        #expect(id == "b1")
    }

    @Test("deleteBaby: DELETE /baby/:id, 204 no content")
    func deleteBaby() async throws {
        let (sut, http) = makeRepo()
        http.sendResult = EmptyResponse()

        try await sut.deleteBaby("b1")

        #expect(http.lastSendMethod == .delete)
        guard case .babyId(let id) = http.lastSendEndpoint else { Issue.record("Expected .babyId"); return }
        #expect(id == "b1")
    }

    @Test("deleteBaby: propagates errors")
    func deleteBabyError() async {
        let (sut, http) = makeRepo()
        http.sendError = HTTPError.serverError
        await #expect(throws: HTTPError.serverError) { try await sut.deleteBaby("b1") }
    }

    // MARK: - BabyRequest / BabyResponse conversions

    @Test("BabyRequest(from local fields): converts units to wire shape")
    func babyRequestConversion() {
        let birthday = DateTimeTools.formatter("yyyy-MM-dd").date(from: "2026-03-15")
        let request = BabyRequest(
            name: "Emma",
            birthday: birthday,
            biologicalSex: "female",
            birthLengthInches: 20.0,
            birthWeightLbs: 7.0,
            birthWeightOz: 2.0
        )
        #expect(request.birthdate == "2026-03-15")
        #expect(request.sex == "female")
        #expect(request.birthLengthMillimeters == ConversionTools.convertBabyInchesToMm(20.0))
        #expect(request.birthWeightDecigrams == ConversionTools.convertBabyLbsOzToDecigrams(lbs: 7, oz: 2.0))
    }

    @Test("BabyRequest(from local fields): nil weight/length stay nil")
    func babyRequestNilUnits() {
        let request = BabyRequest(
            name: "Emma",
            birthday: nil,
            biologicalSex: nil,
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
        #expect(request.birthdate == nil)
        #expect(request.birthWeightDecigrams == nil)
        #expect(request.birthLengthMillimeters == nil)
    }

    @Test("BabyResponse.toBaby: maps wire units back into a synced local Baby")
    func babyResponseToBaby() {
        let response = BabyResponse(
            id: "b1",
            name: "Emma",
            birthdate: "2026-03-15",
            sex: "female",
            birthWeightDecigrams: 32500,
            birthLengthMillimeters: 510
        )
        let baby = response.toBaby(accountId: "acct-1")

        #expect(baby.id == "b1")
        #expect(baby.accountId == "acct-1")
        #expect(baby.isSynced == true)
        #expect(baby.biologicalSex == "female")
        #expect(baby.birthLengthInches == ConversionTools.convertBabyMmToInches(510))
        #expect(baby.birthday != nil)
    }

    // MARK: - BabyEntryRequest factory

    @Test("makeRequests create: emits weight + measureLength rows with shared timestamp")
    func babyEntryRequestsCreate() {
        let requests = BabyEntryRequest.makeRequests(
            babyId: "baby-1",
            entryId: "entry-1",
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-05-06T08:00:00Z",
            weightDecigrams: 45200,
            lengthMillimeters: 510,
            source: "0220",
            note: "After bath"
        )
        #expect(requests.count == 2)
        let weight = requests.first { $0.entryType == BabyEntryType.weight.rawValue }
        let length = requests.first { $0.entryType == BabyEntryType.measureLength.rawValue }
        #expect(weight?.category == EntryCategory.baby.rawValue)
        #expect(weight?.babyWeightDecigrams == 45200)
        #expect(weight?.babyId == "baby-1")
        #expect(weight?.entryId == "entry-1")
        #expect(weight?.source == "0220")
        #expect(weight?.entryNote == "After bath")
        #expect(length?.babyLengthMillimeters == 510)
        #expect(length?.entryId == "entry-1" + BabyEntryRequest.lengthEntryIdSuffix)
    }

    @Test("makeRequests create: weight-only entry emits a single request")
    func babyEntryRequestsWeightOnly() {
        let requests = BabyEntryRequest.makeRequests(
            babyId: "baby-1",
            entryId: "e",
            operationType: OperationType.create.rawValue,
            entryTimestamp: "t",
            weightDecigrams: 45200,
            lengthMillimeters: 0,
            source: nil,
            note: nil
        )
        #expect(requests.count == 1)
        #expect(requests.first?.entryType == BabyEntryType.weight.rawValue)
    }

    @Test("makeRequests delete: single delete keyed by entryId")
    func babyEntryRequestsDelete() {
        let requests = BabyEntryRequest.makeRequests(
            babyId: "baby-1",
            entryId: "e",
            operationType: OperationType.delete.rawValue,
            entryTimestamp: "t",
            weightDecigrams: 45200,
            lengthMillimeters: 510,
            source: nil,
            note: nil
        )
        #expect(requests.count == 1)
        #expect(requests.first?.operationType == OperationType.delete.rawValue)
        #expect(requests.first?.entryId == "e")
    }

    @Test("makeRequests(from dto:): non-baby DTO yields no requests")
    func babyEntryRequestsFromNonBaby() {
        let dto = BathScaleOperationDTO(
            accountId: nil,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "t",
            entryType: EntryType.scale.rawValue,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "create",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: 1700
        )
        #expect(BabyEntryRequest.makeRequests(from: dto, entryId: "e", note: nil).isEmpty)
    }

    // MARK: - Baby read mapping

    @Test("UnifiedEntryResult baby → DTO: carries babyId + decigrams/mm + note")
    func babyResultToDTO() {
        let result = UnifiedEntryResult(
            category: EntryCategory.baby.rawValue,
            entryId: "be-1",
            operationType: "create",
            entryTimestamp: "t",
            serverTimestamp: "s",
            source: "manual",
            weight: nil,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            boneMass: nil,
            impedance: nil,
            unit: nil,
            systolic: nil,
            diastolic: nil,
            pulse: nil,
            note: nil,
            babyId: "baby-1",
            entryType: BabyEntryType.weight.rawValue,
            babyWeightDecigrams: 45200,
            babyLengthMillimeters: nil,
            entryNote: "After bath"
        )
        let dto = result.toOperationDTO()
        #expect(dto.entryType == EntryType.baby.rawValue)
        #expect(dto.babyId == "baby-1")
        #expect(dto.babyWeight == 45200)
        #expect(dto.note == "After bath")
    }

    @Test("list response operations now includes baby entries")
    func operationsIncludesBaby() {
        let response = BathScaleOperationListResponse(entries: [
            UnifiedEntryResult(
                category: EntryCategory.baby.rawValue,
                entryId: "be-1",
                operationType: "create",
                entryTimestamp: "t",
                serverTimestamp: nil,
                source: nil,
                weight: nil,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                boneMass: nil,
                impedance: nil,
                unit: nil,
                systolic: nil,
                diastolic: nil,
                pulse: nil,
                note: nil,
                babyId: "baby-1",
                entryType: BabyEntryType.weight.rawValue,
                babyWeightDecigrams: 45200,
                babyLengthMillimeters: nil,
                entryNote: nil
            )
        ])
        #expect(response.operations.count == 1)
        #expect(response.operations.first?.entryType == EntryType.baby.rawValue)
    }

    // MARK: - EntryService baby push + CSV

    @Test("syncAllEntriesWithRemote: pushes a baby entry as baby category requests")
    func babyEntryPushed() async {
        let repo = MockEntryRepository()
        let entry = Entry(
            entryTimestamp: "2026-05-06T08:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue,
            isSynced: false
        )
        entry.babyEntry = BabyEntry(babyId: "baby-1", length: 510, weight: 45200, source: "0220")
        repo.entries = [entry]
        let remote = MockEntryRepositoryAPI()
        let sut = makeEntrySUT(repo: repo, remote: remote)

        await sut.syncAllEntriesWithRemote()

        #expect(remote.submitEntriesCalls >= 1)
        let submitted = remote.lastSubmittedEntries ?? []
        #expect(submitted.allSatisfy { $0.category == EntryCategory.baby.rawValue })
        #expect(submitted.contains { $0.entryType == BabyEntryType.weight.rawValue && $0.babyWeightDecigrams == 45200 })
        #expect(submitted.contains { $0.entryType == BabyEntryType.measureLength.rawValue && $0.babyLengthMillimeters == 510 })
    }

    @Test("exportCSV baby: forwards category + babyId to the unified CSV request")
    func babyExportCSV() async throws {
        let remote = MockEntryRepositoryAPI()
        let sut = makeEntrySUT(remote: remote)

        try await sut.exportCSV(category: EntryCategory.baby.rawValue, babyId: "baby-1")

        #expect(remote.lastExportCsvRequest?.category == EntryCategory.baby.rawValue)
        #expect(remote.lastExportCsvRequest?.babyId == "baby-1")
    }

    // MARK: - remapBabyId (MOB-1527: offline baby entries stay attached after id remap)

    @Test("remapBabyId: rewrites babyId on matching baby entries, leaving others untouched")
    func remapBabyIdRewritesMatchingEntries() async throws {
        let repo = MockEntryRepository()
        let matching = Entry(
            entryTimestamp: "2026-01-01T00:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue
        )
        matching.babyEntry = BabyEntry(babyId: "client-1", length: 500, weight: 30000)
        let other = Entry(
            entryTimestamp: "2026-01-02T00:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue
        )
        other.babyEntry = BabyEntry(babyId: "other-baby", length: 400, weight: 20000)
        repo.entries = [matching, other]

        let sut = makeEntrySUT(repo: repo)
        await sut.remapBabyId(from: "client-1", to: "srv-1")

        #expect(matching.babyEntry?.babyId == "srv-1")
        #expect(other.babyEntry?.babyId == "other-baby") // untouched
    }

    @Test("remapBabyId: no-op when old and new ids are equal")
    func remapBabyIdNoOpWhenEqual() async throws {
        let repo = MockEntryRepository()
        let entry = Entry(
            entryTimestamp: "2026-01-01T00:00:00Z",
            accountId: "acct-1",
            operationType: OperationType.create.rawValue,
            entryType: EntryType.baby.rawValue
        )
        entry.babyEntry = BabyEntry(babyId: "same", length: 500, weight: 30000)
        repo.entries = [entry]

        let sut = makeEntrySUT(repo: repo)
        await sut.remapBabyId(from: "same", to: "same")

        #expect(entry.babyEntry?.babyId == "same")
    }

    // MARK: - EntryService SUT

    private func makeEntrySUT(repo: MockEntryRepository? = nil, remote: MockEntryRepositoryAPI? = nil) -> EntryService {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1", email: "baby@example.com", isActiveAccount: true
        )
        let logger = MockLoggerService()
        let goalAlert = MockGoalAlertService()
        let integration = MockIntegrationService()
        let keychain = MockKeychainService()
        let bluetooth = MockBluetoothService()

        TestDependencyContainer.reset()
        TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)
        DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

        let babyLocalRepo = repo ?? MockEntryRepository()
        let babyWorker = MockEntryWorker()
        babyWorker.backingRepo = babyLocalRepo
        let sut = EntryService(
            accountService: account,
            localRepo: babyLocalRepo,
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: remote ?? MockEntryRepositoryAPI(),
            worker: babyWorker
        )
        sut.logger = logger
        sut.goalAlertService = goalAlert
        sut.integrationService = integration
        return sut
    }
}
