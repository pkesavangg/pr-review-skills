import Foundation
@testable import meApp

final class MockIntegrationAPIRepository: IntegrationRepositoryAPIProtocol {
    var createHealthIntegrationResult: Result<HealthIntegrationResponse, Error> = .success(
        HealthIntegrationResponse(
            deviceId: "test-device",
            type: .healthKit,
            preferences: [:],
            integratedAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z"
        )
    )
    var deleteHealthIntegrationResult: Result<Void, Error> = .success(())

    private(set) var removeIntegrationCalls = 0
    private(set) var createHealthIntegrationCalls = 0
    private(set) var logHealthIntegrationCalls = 0
    private(set) var deleteHealthIntegrationCalls = 0

    func removeIntegration(accountId: String, provider: IntegrationType) async throws {
        removeIntegrationCalls += 1
        throw UnexpectedCallError.methodCalled("removeIntegration")
    }

    func createHealthIntegration(deviceId: String, type: IntegrationType, preferences: [String: AnyCodable]) async throws -> HealthIntegrationResponse {
        createHealthIntegrationCalls += 1
        return try createHealthIntegrationResult.get()
    }

    func logHealthIntegration(type: IntegrationType, sentAt: String, timestamp: String, weight: Int?, bodyFat: Int?, muscleMass: Int?, water: Int?, bmi: Int?, data: [String: AnyCodable]) async throws -> HealthIntegrationLogResponse {
        logHealthIntegrationCalls += 1
        throw UnexpectedCallError.methodCalled("logHealthIntegration")
    }

    func deleteHealthIntegration(deviceId: String) async throws {
        deleteHealthIntegrationCalls += 1
        _ = try deleteHealthIntegrationResult.get()
    }

    func requestNewIntegration(suggestion: String) async throws {
        throw UnexpectedCallError.methodCalled("requestNewIntegration")
    }
}
