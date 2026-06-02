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

    // MARK: - fetchOperations

    @Test("fetchOperations success: calls get with operationsR4(nil) endpoint with auth, returns response")
    func fetchOperationsSuccess() async throws {
        let (sut, http) = makeSUT()
        let operation = BathScaleOperationDTO(
            accountId: "acct-1", bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
            entryTimestamp: "2026-03-01T08:00:00Z", entryType: nil, impedance: nil, metabolicAge: nil,
            muscleMass: nil, operationType: "create", proteinPercent: nil, pulse: nil,
            serverTimestamp: nil, skeletalMusclePercent: nil, source: nil,
            subcutaneousFatPercent: nil, systolic: nil, diastolic: nil, meanArterial: nil,
            unit: "lb", visceralFatLevel: nil, water: nil, weight: 75.0
        )
        http.getResult = BathScaleOperationListResponse(
            operations: [operation],
            timestamp: "2026-03-01T08:00:00Z"
        )

        let result = try await sut.fetchOperations(startTimestamp: nil)

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .operationsR4(let ts) = http.lastGetEndpoint else {
            Issue.record("Expected .operationsR4 endpoint"); return
        }
        #expect(ts == nil)
        #expect(result.operations.count == 1)
        #expect(result.timestamp == "2026-03-01T08:00:00Z")
    }

    @Test("fetchOperations with timestamp: passes startTimestamp to endpoint")
    func fetchOperationsWithTimestamp() async throws {
        let (sut, http) = makeSUT()
        http.getResult = BathScaleOperationListResponse(operations: [], timestamp: "2026-03-01T08:00:00Z")

        _ = try await sut.fetchOperations(startTimestamp: "2026-01-01T00:00:00Z")

        guard case .operationsR4(let ts) = http.lastGetEndpoint else {
            Issue.record("Expected .operationsR4 endpoint"); return
        }
        #expect(ts == "2026-01-01T00:00:00Z")
    }

    @Test("fetchOperations empty: returns empty operations array")
    func fetchOperationsEmpty() async throws {
        let (sut, http) = makeSUT()
        http.getResult = BathScaleOperationListResponse(operations: [], timestamp: "2026-03-01T08:00:00Z")

        let result = try await sut.fetchOperations(startTimestamp: nil)

        #expect(result.operations.isEmpty)
    }

    @Test("fetchOperations failure: propagates error from http client")
    func fetchOperationsFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.fetchOperations(startTimestamp: nil)
        }
        #expect(http.getCalls == 1)
    }

    // MARK: - exportCsv

    @Test("exportCsv R4 success: calls get with operationsR4CSV endpoint with auth")
    func exportCsvR4Success() async throws {
        let (sut, http) = makeSUT()
        http.getResult = ExportResponse(sent: true)

        let result = try await sut.exportCsv(useR4Endpoint: true)

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .operationsR4CSV = http.lastGetEndpoint else {
            Issue.record("Expected .operationsR4CSV endpoint"); return
        }
        #expect(result.sent == true)
    }

    @Test("exportCsv legacy success: calls get with operationsCSV endpoint when useR4Endpoint is false")
    func exportCsvLegacySuccess() async throws {
        let (sut, http) = makeSUT()
        http.getResult = ExportResponse(sent: true)

        _ = try await sut.exportCsv(useR4Endpoint: false)

        guard case .operationsCSV = http.lastGetEndpoint else {
            Issue.record("Expected .operationsCSV endpoint"); return
        }
    }

    @Test("exportCsv failure: propagates error from http client")
    func exportCsvFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.exportCsv(useR4Endpoint: true)
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
            try await sut.fetchOperations(startTimestamp: nil)
        }
    }

    @Test("unauthorized error: propagated from get")
    func unauthorizedErrorPropagated() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.exportCsv(useR4Endpoint: true)
        }
    }
}
