import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct EntryRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: EntryRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = EntryRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - submitEntries

    @Test("submitEntries success: POSTs to submitEntries endpoint with auth and returns response")
    func submitEntriesSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = UnifiedEntryResponse(
            entries: [],
            timestamp: "2026-03-01T08:00:05Z"
        )

        let request = UnifiedEntryRequest(
            category: EntryCategory.weight.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-03-01T08:00:00Z",
            weight: 1723,
            unit: "lb",
            source: "btWifiR4"
        )
        let response = try await sut.submitEntries([request])

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .submitEntries = http.lastSendEndpoint else {
            Issue.record("Expected .submitEntries endpoint"); return
        }
        #expect(response.timestamp == "2026-03-01T08:00:05Z")
    }

    @Test("submitEntries mixed batch: sends weight + BP entries in one atomic array body")
    func submitEntriesMixedBatch() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = UnifiedEntryResponse(entries: [], timestamp: "2026-03-01T08:00:05Z")

        let weight = UnifiedEntryRequest(
            category: EntryCategory.weight.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-03-01T08:00:00Z",
            weight: 1723
        )
        let bp = UnifiedEntryRequest(
            category: EntryCategory.bp.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-03-01T07:30:00Z",
            systolic: 120, diastolic: 80, pulse: 72, source: "manual"
        )
        _ = try await sut.submitEntries([weight, bp])

        let body = http.lastSendBody as? [UnifiedEntryRequest]
        #expect(body?.count == 2)
        #expect(body?.first?.category == EntryCategory.weight.rawValue)
        #expect(body?.last?.category == EntryCategory.bp.rawValue)
    }

    @Test("submitEntries failure: propagates error from http client")
    func submitEntriesFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        let request = UnifiedEntryRequest(
            category: EntryCategory.weight.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-03-01T08:00:00Z",
            weight: 1723
        )
        await #expect(throws: HTTPError.serverError) {
            _ = try await sut.submitEntries([request])
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - fetchEntries

    @Test("fetchEntries sync mode: calls get with entries endpoint (start set), returns flat response")
    func fetchEntriesSyncMode() async throws {
        let (sut, http) = makeSUT()
        let entry = UnifiedEntryResult(
            category: EntryCategory.weight.rawValue, entryId: "12345",
            operationType: "create", entryTimestamp: "2026-03-01T08:00:00Z",
            serverTimestamp: "2026-03-01T08:00:05Z", source: "btWifiR4",
            weight: 1723, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
            boneMass: nil, impedance: nil, unit: "lb",
            systolic: nil, diastolic: nil, pulse: nil, note: nil
        )
        http.getResult = BathScaleOperationListResponse(
            entries: [entry], timestamp: "2026-03-01T08:00:10Z"
        )

        let result = try await sut.fetchEntries(start: "2026-01-01T00:00:00Z", cursor: nil, limit: nil, category: nil)

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .entries(let start, let cursor, let limit, let category, _) = http.lastGetEndpoint else {
            Issue.record("Expected .entries endpoint"); return
        }
        #expect(start == "2026-01-01T00:00:00Z")
        #expect(cursor == nil)
        #expect(limit == nil)
        #expect(category == nil)
        #expect(result.entries.count == 1)
        #expect(result.operations.count == 1)
        #expect(result.timestamp == "2026-03-01T08:00:10Z")
    }

    @Test("fetchEntries cursor mode: passes cursor + limit + category and surfaces nextCursor/hasMore")
    func fetchEntriesCursorMode() async throws {
        let (sut, http) = makeSUT()
        http.getResult = BathScaleOperationListResponse(
            entries: [], nextCursor: "2026-03-01T07:00:00Z", hasMore: true
        )

        let result = try await sut.fetchEntries(
            start: nil, cursor: "2026-03-01T08:00:00Z", limit: 20, category: EntryCategory.bp.rawValue
        )

        guard case .entries(let start, let cursor, let limit, let category, _) = http.lastGetEndpoint else {
            Issue.record("Expected .entries endpoint"); return
        }
        #expect(start == nil)
        #expect(cursor == "2026-03-01T08:00:00Z")
        #expect(limit == 20)
        #expect(category == EntryCategory.bp.rawValue)
        #expect(result.nextCursor == "2026-03-01T07:00:00Z")
        #expect(result.hasMore == true)
    }

    @Test("fetchEntries empty: returns empty entries array")
    func fetchEntriesEmpty() async throws {
        let (sut, http) = makeSUT()
        http.getResult = BathScaleOperationListResponse(entries: [], timestamp: "2026-03-01T08:00:00Z")

        let result = try await sut.fetchEntries(start: nil, cursor: nil, limit: nil, category: nil)

        #expect(result.entries.isEmpty)
        #expect(result.operations.isEmpty)
    }

    @Test("fetchEntries failure: propagates error from http client")
    func fetchEntriesFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.fetchEntries(start: nil, cursor: nil, limit: nil, category: nil)
        }
        #expect(http.getCalls == 1)
    }

    // MARK: - exportEntriesCSV

    @Test("exportEntriesCSV success: calls get with entriesCSV endpoint with auth")
    func exportEntriesCSVSuccess() async throws {
        let (sut, http) = makeSUT()
        http.getResult = ExportResponse(sent: true)

        let result = try await sut.exportEntriesCSV(
            EntriesCSVRequest(category: EntryCategory.weight.rawValue, download: false, utcOffset: -300)
        )

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .entriesCSV(let category, _, let download, let utcOffset, _) = http.lastGetEndpoint else {
            Issue.record("Expected .entriesCSV endpoint"); return
        }
        #expect(category == EntryCategory.weight.rawValue)
        #expect(download == false)
        #expect(utcOffset == -300)
        #expect(result.sent == true)
    }

    @Test("exportEntriesCSV download mode: forwards download flag and babyId")
    func exportEntriesCSVDownloadMode() async throws {
        let (sut, http) = makeSUT()
        http.getResult = ExportResponse(sent: true)

        _ = try await sut.exportEntriesCSV(
            EntriesCSVRequest(category: EntryCategory.baby.rawValue, babyId: "baby-1", download: true)
        )

        guard case .entriesCSV(let category, let babyId, let download, _, _) = http.lastGetEndpoint else {
            Issue.record("Expected .entriesCSV endpoint"); return
        }
        #expect(category == EntryCategory.baby.rawValue)
        #expect(babyId == "baby-1")
        #expect(download == true)
    }

    @Test("exportEntriesCSV failure: propagates error from http client")
    func exportEntriesCSVFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.exportEntriesCSV(EntriesCSVRequest())
        }
        #expect(http.getCalls == 1)
    }

    // MARK: - Error Propagation

    @Test("noInternet error: propagated from send")
    func noInternetPropagated() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        let request = UnifiedEntryRequest(
            category: EntryCategory.weight.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-03-01T08:00:00Z",
            weight: 1723
        )
        await #expect(throws: HTTPError.noInternet) {
            _ = try await sut.submitEntries([request])
        }
    }

    @Test("timeout error: propagated from get")
    func timeoutErrorPropagated() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.fetchEntries(start: nil, cursor: nil, limit: nil, category: nil)
        }
    }

    @Test("unauthorized error: propagated from get")
    func unauthorizedErrorPropagated() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.exportEntriesCSV(EntriesCSVRequest())
        }
    }
}
