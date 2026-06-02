//
//  UnifiedEntriesReadTests.swift
//  meAppTests
//
//  MOB-385 — Unified entries read path: response model, DTO bridging,
//  CSV request, cursor pagination engine, and category mapping.
//

import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct UnifiedEntriesReadTests {

    // MARK: - UnifiedEntryResult ⇄ BathScaleOperationDTO mapping

    @Test("toOperationDTO: weight result maps measurement fields and category→entryType")
    func toOperationDTOWeight() {
        let result = UnifiedEntryResult(
            category: EntryCategory.weight.rawValue, entryId: "1", operationType: "create",
            entryTimestamp: "2026-05-06T10:00:00Z", serverTimestamp: "2026-05-06T10:00:05Z",
            source: "btWifiR4", weight: 1723, bodyFat: 225, muscleMass: 401, water: 552,
            bmi: 243, boneMass: 38, impedance: 495, unit: "lb",
            systolic: nil, diastolic: nil, pulse: 68, note: nil
        )

        let dto = result.toOperationDTO()

        #expect(dto.entryType == EntryType.scale.rawValue)
        #expect(dto.weight == 1723)
        #expect(dto.bodyFat == 225)
        #expect(dto.impedance == 495)
        #expect(dto.unit == "lb")
        #expect(dto.serverTimestamp == "2026-05-06T10:00:05Z")
    }

    @Test("toOperationDTO: bp result maps systolic/diastolic/pulse/note and category→entryType")
    func toOperationDTOBp() {
        let result = UnifiedEntryResult(
            category: EntryCategory.bp.rawValue, entryId: "bp-1", operationType: "create",
            entryTimestamp: "2026-05-06T09:30:00Z", serverTimestamp: "2026-05-06T09:30:02Z",
            source: "bluetooth", weight: nil, bodyFat: nil, muscleMass: nil, water: nil,
            bmi: nil, boneMass: nil, impedance: nil, unit: nil,
            systolic: 120, diastolic: 80, pulse: 72, note: "Morning reading"
        )

        let dto = result.toOperationDTO()

        #expect(dto.entryType == EntryType.bpm.rawValue)
        #expect(dto.systolic == 120)
        #expect(dto.diastolic == 80)
        #expect(dto.pulse == 72)
        #expect(dto.note == "Morning reading")
    }

    @Test("entryType(forCategory:) maps weight/bp/baby and unknown→nil")
    func entryTypeForCategory() {
        #expect(UnifiedEntryResult.entryType(forCategory: "weight") == EntryType.scale.rawValue)
        #expect(UnifiedEntryResult.entryType(forCategory: "bp") == EntryType.bpm.rawValue)
        #expect(UnifiedEntryResult.entryType(forCategory: "baby") == EntryType.baby.rawValue)
        #expect(UnifiedEntryResult.entryType(forCategory: "unknown") == nil)
    }

    @Test("category(forEntryType:) maps bpm/baby and legacy nil/scale→weight")
    func categoryForEntryType() {
        #expect(UnifiedEntryResult.category(forEntryType: EntryType.bpm.rawValue) == EntryCategory.bp.rawValue)
        #expect(UnifiedEntryResult.category(forEntryType: EntryType.baby.rawValue) == EntryCategory.baby.rawValue)
        #expect(UnifiedEntryResult.category(forEntryType: nil) == EntryCategory.weight.rawValue)
        #expect(UnifiedEntryResult.category(forEntryType: EntryType.scale.rawValue) == EntryCategory.weight.rawValue)
    }

    // MARK: - BathScaleOperationListResponse decoding (flat shape)

    @Test("decodes cursor-mode JSON: entries + nextCursor + hasMore")
    func decodeCursorMode() throws {
        let json = """
        {
          "entries": [
            { "category": "weight", "entryId": "12345", "operationType": "create",
              "entryTimestamp": "2026-05-06T10:00:00.000Z", "serverTimestamp": "2026-05-06T10:00:05.000Z",
              "source": "btWifiR4", "weight": 1723, "bodyFat": 225, "unit": "lb" }
          ],
          "nextCursor": "2026-05-05T22:00:00.000Z",
          "hasMore": true
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(BathScaleOperationListResponse.self, from: json)

        #expect(response.entries.count == 1)
        #expect(response.entries.first?.weight == 1723)
        #expect(response.nextCursor == "2026-05-05T22:00:00.000Z")
        #expect(response.hasMore == true)
        #expect(response.timestamp == nil)
    }

    @Test("decodes sync-mode JSON: entries + timestamp, no cursor metadata")
    func decodeSyncMode() throws {
        let json = """
        { "entries": [], "timestamp": "2026-05-06T10:05:00.000Z" }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(BathScaleOperationListResponse.self, from: json)

        #expect(response.entries.isEmpty)
        #expect(response.timestamp == "2026-05-06T10:05:00.000Z")
        #expect(response.nextCursor == nil)
        #expect(response.hasMore == nil)
    }

    @Test("operations accessor: maps weight + bp but filters out baby (out of scope)")
    func operationsFiltersBaby() {
        let response = BathScaleOperationListResponse(entries: [
            UnifiedEntryResult(
                category: EntryCategory.weight.rawValue, entryId: "1", operationType: "create",
                entryTimestamp: "t1", serverTimestamp: nil, source: nil, weight: 1700,
                bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, boneMass: nil,
                impedance: nil, unit: nil, systolic: nil, diastolic: nil, pulse: nil, note: nil
            ),
            UnifiedEntryResult(
                category: EntryCategory.bp.rawValue, entryId: "2", operationType: "create",
                entryTimestamp: "t2", serverTimestamp: nil, source: nil, weight: nil,
                bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, boneMass: nil,
                impedance: nil, unit: nil, systolic: 120, diastolic: 80, pulse: 70, note: nil
            ),
            UnifiedEntryResult(
                category: EntryCategory.baby.rawValue, entryId: "3", operationType: "create",
                entryTimestamp: "t3", serverTimestamp: nil, source: nil, weight: nil,
                bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, boneMass: nil,
                impedance: nil, unit: nil, systolic: nil, diastolic: nil, pulse: nil, note: nil
            )
        ])

        let ops = response.operations
        #expect(ops.count == 2)
        #expect(ops.contains { $0.entryType == EntryType.scale.rawValue })
        #expect(ops.contains { $0.entryType == EntryType.bpm.rawValue })
        #expect(!ops.contains { $0.entryType == EntryType.baby.rawValue })
    }

    // MARK: - EntriesCSVRequest

    @Test("EntriesCSVRequest defaults: email mode, no category, zero offset")
    func csvRequestDefaults() {
        let request = EntriesCSVRequest()
        #expect(request.category == nil)
        #expect(request.babyId == nil)
        #expect(request.download == false)
        #expect(request.utcOffset == 0)
        #expect(request.entryType == nil)
    }

    // MARK: - EntriesPagination

    @Test("EntriesPagination.clamp bounds limit into 1...100")
    func paginationClamp() {
        #expect(EntriesPagination.clamp(limit: 0) == 1)
        #expect(EntriesPagination.clamp(limit: -5) == 1)
        #expect(EntriesPagination.clamp(limit: 20) == 20)
        #expect(EntriesPagination.clamp(limit: 250) == EntriesPagination.maxLimit)
    }

    // MARK: - EntryService.fetchEntriesPage

    @Test("fetchEntriesPage: forwards cursor/limit/category and surfaces nextCursor + hasMore")
    func fetchEntriesPageForwardsAndSurfaces() async throws {
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesResult = BathScaleOperationListResponse(
            entries: [
                UnifiedEntryResult(
                    category: EntryCategory.weight.rawValue, entryId: "1", operationType: "create",
                    entryTimestamp: "2026-05-06T10:00:00Z", serverTimestamp: nil, source: nil,
                    weight: 1700, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
                    boneMass: nil, impedance: nil, unit: nil, systolic: nil, diastolic: nil, pulse: nil, note: nil
                )
            ],
            nextCursor: "2026-05-05T22:00:00Z",
            hasMore: true
        )
        let sut = makeSUT(remote: remote)

        let page = try await sut.fetchEntriesPage(cursor: "2026-05-06T10:00:00Z", limit: 20, category: EntryCategory.weight.rawValue)

        #expect(remote.fetchEntriesCalls == 1)
        #expect(remote.lastFetchCursor == "2026-05-06T10:00:00Z")
        #expect(remote.lastFetchStart == nil)
        #expect(remote.lastFetchLimit == 20)
        #expect(remote.lastFetchCategory == EntryCategory.weight.rawValue)
        #expect(page.entries.count == 1)
        #expect(page.nextCursor == "2026-05-05T22:00:00Z")
        #expect(page.hasMore == true)
    }

    @Test("fetchEntriesPage: clamps an out-of-range limit before calling the API")
    func fetchEntriesPageClampsLimit() async throws {
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesResult = BathScaleOperationListResponse(entries: [], hasMore: false)
        let sut = makeSUT(remote: remote)

        _ = try await sut.fetchEntriesPage(cursor: nil, limit: 999, category: nil)

        #expect(remote.lastFetchLimit == EntriesPagination.maxLimit)
    }

    @Test("fetchEntriesPage: infers hasMore from a full page when server omits the flag")
    func fetchEntriesPageInfersHasMore() async throws {
        let remote = MockEntryRepositoryAPI()
        // A full page (count == limit) with no hasMore flag implies more rows remain.
        let full = (0..<5).map { index in
            UnifiedEntryResult(
                category: EntryCategory.weight.rawValue, entryId: "\(index)", operationType: "create",
                entryTimestamp: "2026-05-06T10:0\(index):00Z", serverTimestamp: nil, source: nil,
                weight: 1700, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
                boneMass: nil, impedance: nil, unit: nil, systolic: nil, diastolic: nil, pulse: nil, note: nil
            )
        }
        remote.fetchEntriesResult = BathScaleOperationListResponse(entries: full, nextCursor: nil, hasMore: nil)
        let sut = makeSUT(remote: remote)

        let page = try await sut.fetchEntriesPage(cursor: nil, limit: 5, category: nil)
        #expect(page.hasMore == true)
    }

    @Test("fetchEntriesPage: a short page with no flag means no more rows")
    func fetchEntriesPageShortPageNoMore() async throws {
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesResult = BathScaleOperationListResponse(entries: [], nextCursor: nil, hasMore: nil)
        let sut = makeSUT(remote: remote)

        let page = try await sut.fetchEntriesPage(cursor: nil, limit: 20, category: nil)
        #expect(page.hasMore == false)
    }

    @Test("fetchEntriesPage: propagates API errors")
    func fetchEntriesPageError() async {
        let remote = MockEntryRepositoryAPI()
        remote.fetchEntriesError = EntryTestError.remoteFailure
        let sut = makeSUT(remote: remote)

        await #expect(throws: EntryTestError.remoteFailure) {
            _ = try await sut.fetchEntriesPage(cursor: nil, limit: 20, category: nil)
        }
    }

    // MARK: - ProductSelection.entriesCategory

    @Test("ProductSelection.entriesCategory maps selections to API categories")
    func productSelectionEntriesCategory() {
        #expect(ProductSelection.myWeight.entriesCategory == EntryCategory.weight.rawValue)
        #expect(ProductSelection.myBloodPressure.entriesCategory == EntryCategory.bp.rawValue)
        let baby = ProductSelection.baby(profile: BabyProfile(id: "b1", name: "Emma"))
        #expect(baby.entriesCategory == EntryCategory.baby.rawValue)
    }

    // MARK: - SUT

    private func makeSUT(remote: MockEntryRepositoryAPI) -> EntryService {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-1", email: "entry@example.com", isActiveAccount: true
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

        let sut = EntryService(
            accountService: account,
            localRepo: MockEntryRepository(),
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: remote
        )
        sut.logger = logger
        sut.goalAlertService = goalAlert
        sut.integrationService = integration
        return sut
    }
}
