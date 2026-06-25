import Foundation
@testable import meApp

final class MockIntegrationsAPIRepository: IntegrationRepositoryAPIProtocol {
    var removeIntegrationError: Error?
    var createHealthIntegrationError: Error?
    var logHealthIntegrationError: Error?
    var deleteHealthIntegrationError: Error?
    var requestNewIntegrationError: Error?

    private(set) var removeIntegrationCalls = 0
    private(set) var lastRemoveAccountId: String?
    private(set) var lastRemoveProvider: IntegrationType?
    private(set) var logHealthIntegrationCalls = 0
    private(set) var lastLogType: IntegrationType?
    private(set) var lastLogTimestamp: String?
    private(set) var requestNewIntegrationCalls = 0
    private(set) var lastRequestSuggestion: String?

    func requestNewIntegration(suggestion: String) async throws {
        requestNewIntegrationCalls += 1
        lastRequestSuggestion = suggestion
        if let requestNewIntegrationError {
            throw requestNewIntegrationError
        }
    }

    func removeIntegration(accountId: String, provider: IntegrationType) async throws {
        removeIntegrationCalls += 1
        lastRemoveAccountId = accountId
        lastRemoveProvider = provider
        if let removeIntegrationError {
            throw removeIntegrationError
        }
    }

    func createHealthIntegration(deviceId: String, type: IntegrationType, preferences: [String: AnyCodable]) async throws -> HealthIntegrationResponse {
        if let createHealthIntegrationError {
            throw createHealthIntegrationError
        }
        return HealthIntegrationResponse(
            deviceId: deviceId,
            type: type,
            preferences: preferences,
            integratedAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z"
        )
    }

    func logHealthIntegration(type: IntegrationType, sentAt: String, timestamp: String, weight: Int?, bodyFat: Int?, muscleMass: Int?, water: Int?, bmi: Int?, data: [String: AnyCodable]) async throws -> HealthIntegrationLogResponse {
        logHealthIntegrationCalls += 1
        lastLogType = type
        lastLogTimestamp = timestamp
        if let logHealthIntegrationError {
            throw logHealthIntegrationError
        }
        return HealthIntegrationLogResponse(
            type: type,
            sentAt: sentAt,
            timestamp: timestamp,
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            data: data
        )
    }

    func deleteHealthIntegration(deviceId: String) async throws {
        if let deleteHealthIntegrationError {
            throw deleteHealthIntegrationError
        }
    }
}
