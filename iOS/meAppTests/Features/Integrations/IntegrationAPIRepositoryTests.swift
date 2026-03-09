// swiftlint:disable function_parameter_count
import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct IntegrationAPIRepositoryTests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: IntegrationAPIRepository, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = IntegrationAPIRepository(httpClient: http)
        return (sut, http)
    }

    // MARK: - removeIntegration

    @Test("removeIntegration success: healthKit uses integrationHealthDevice(accountId) DELETE with auth")
    func removeIntegrationHealthKitSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.removeIntegration(accountId: "acct-1", provider: .healthKit)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationHealthDevice(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealthDevice endpoint"); return
        }
        #expect(id == "acct-1")
    }

    @Test("removeIntegration success: healthConnect uses integrationHealthDevice(accountId) DELETE with auth")
    func removeIntegrationHealthConnectSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.removeIntegration(accountId: "acct-2", provider: .healthConnect)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationHealthDevice(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealthDevice endpoint"); return
        }
        #expect(id == "acct-2")
    }

    @Test("removeIntegration success: fitbit uses integrationProvider(rawValue) DELETE with auth")
    func removeIntegrationFitbitSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.removeIntegration(accountId: "acct-3", provider: .fitbit)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationProvider(let provider) = http.lastSendEndpoint else {
            Issue.record("Expected .integrationProvider endpoint"); return
        }
        #expect(provider == IntegrationType.fitbit.rawValue)
    }

    @Test("removeIntegration failure: propagates error from http client")
    func removeIntegrationFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.removeIntegration(accountId: "acct-1", provider: .fitbit)
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - createHealthIntegration

    @Test("createHealthIntegration success: calls send with integrationHealth POST with auth, returns response")
    func createHealthIntegrationSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = HealthIntegrationResponse(
            deviceId: "device-abc",
            type: .healthKit,
            preferences: [:],
            integratedAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z"
        )

        let result = try await sut.createHealthIntegration(
            deviceId: "device-abc",
            type: .healthKit,
            preferences: [:]
        )

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationHealth = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealth endpoint"); return
        }
        #expect(result.deviceId == "device-abc")
        #expect(result.type == .healthKit)
    }

    @Test("createHealthIntegration failure: propagates error from http client")
    func createHealthIntegrationFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.createHealthIntegration(deviceId: "device-abc", type: .healthKit, preferences: [:])
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - logHealthIntegration

    @Test("logHealthIntegration success: calls send with integrationHealthLog POST with auth, returns response")
    func logHealthIntegrationSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = HealthIntegrationLogResponse(
            type: .healthKit,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: 7500,
            bodyFat: 20,
            muscleMass: 60,
            water: 55,
            bmi: 22,
            data: [:]
        )

        let result = try await sut.logHealthIntegration(
            type: .healthKit,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: 7500,
            bodyFat: 20,
            muscleMass: 60,
            water: 55,
            bmi: 22,
            data: [:]
        )

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationHealthLog = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealthLog endpoint"); return
        }
        #expect(result.type == .healthKit)
        #expect(result.weight == 7500)
        #expect(result.bodyFat == 20)
    }

    @Test("logHealthIntegration success: nil optional fields are accepted")
    func logHealthIntegrationNilOptionalFields() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = HealthIntegrationLogResponse(
            type: .healthConnect,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: nil,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            data: [:]
        )

        let result = try await sut.logHealthIntegration(
            type: .healthConnect,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: nil,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            data: [:]
        )

        #expect(http.sendCalls == 1)
        guard case .integrationHealthLog = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealthLog endpoint"); return
        }
        #expect(result.weight == nil)
        #expect(result.bmi == nil)
    }

    @Test("logHealthIntegration repeat call: succeeds twice (idempotency)")
    func logHealthIntegrationRepeatCall() async throws {
        let (sut, http) = makeSUT()
        let response = HealthIntegrationLogResponse(
            type: .healthKit,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: 7500,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            data: [:]
        )
        http.sendResult = response

        try await sut.logHealthIntegration(
            type: .healthKit,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: 7500,
            bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
            data: [:]
        )
        try await sut.logHealthIntegration(
            type: .healthKit,
            sentAt: "2026-03-01T08:00:00Z",
            timestamp: "2026-03-01T07:55:00Z",
            weight: 7500,
            bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
            data: [:]
        )

        #expect(http.sendCalls == 2)
    }

    @Test("logHealthIntegration failure: propagates error from http client")
    func logHealthIntegrationFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.logHealthIntegration(
                type: .healthKit,
                sentAt: "2026-03-01T08:00:00Z",
                timestamp: "2026-03-01T07:55:00Z",
                weight: nil, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil,
                data: [:]
            )
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - deleteHealthIntegration

    @Test("deleteHealthIntegration success: calls send with integrationHealthDevice(deviceId) DELETE with auth")
    func deleteHealthIntegrationSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deleteHealthIntegration(deviceId: "device-xyz")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .integrationHealthDevice(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .integrationHealthDevice endpoint"); return
        }
        #expect(id == "device-xyz")
    }

    @Test("deleteHealthIntegration failure: propagates error from http client")
    func deleteHealthIntegrationFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.deleteHealthIntegration(deviceId: "device-xyz")
        }
        #expect(http.sendCalls == 1)
    }
}
// swiftlint:enable function_parameter_count
