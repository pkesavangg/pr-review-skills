//
//  IntegrationsAPIRepository.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

// swiftlint:disable function_parameter_count
// The logHealthIntegration function intentionally has many parameters to match the backend API contract
// and provide clear, explicit parameter names for all health integration data fields.
// This matches the protocol definition which also disables this rule for the same reason.

import Foundation

@MainActor
final class IntegrationAPIRepository: IntegrationRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func removeIntegration(accountId: String, provider: IntegrationType) async throws {
        switch provider {
        case .healthKit, .healthConnect:
            let endpoint = Endpoint.integrationHealthDevice(accountId)
            _ = try await httpClient.send(
                endpoint,
                method: .delete,
                body: EmptyBody(),
                needsAuth: true
            ) as EmptyResponse
        default:
            let endpoint = Endpoint.integrationProvider(provider.rawValue)
            _ = try await httpClient.send(
                endpoint,
                method: .delete,
                body: EmptyBody(),
                needsAuth: true
            ) as EmptyResponse
        }
    }

    func createHealthIntegration(deviceId: String, type: IntegrationType, preferences: [String: AnyCodable]) async throws -> HealthIntegrationResponse {
        struct CreateRequest: Codable {
            let deviceId: String
            let type: String
            let preferences: [String: AnyCodable]
        }
        let requestBody = CreateRequest(deviceId: deviceId, type: type.rawValue, preferences: preferences)
        return try await httpClient.send(
            .integrationHealth,
            method: .post,
            body: requestBody,
            needsAuth: true
        )
    }

    func logHealthIntegration(
        type: IntegrationType,
        sentAt: String,
        timestamp: String,
        weight: Int?,
        bodyFat: Int?,
        muscleMass: Int?,
        water: Int?,
        bmi: Int?,
        data: [String: AnyCodable]
    ) async throws -> HealthIntegrationLogResponse {
        let requestBody = HealthLogRequest(
            type: type.rawValue,
            sentAt: sentAt,
            timestamp: timestamp,
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            data: data
        )
        return try await httpClient.send(
            .integrationHealthLog,
            method: .post,
            body: requestBody,
            needsAuth: true
        )
    }

    // MARK: - Delete Health Integration
    /// Deletes the Health Connect/HealthKit integration for the given **device identifier**.
    ///
    /// Endpoint: `DELETE /integrations/health/{deviceId}`
    /// - Parameter deviceId: The unique identifier for the Health integration device.
    func deleteHealthIntegration(deviceId: String) async throws {
        _ = try await httpClient.send(
            .integrationHealthDevice(deviceId),
            method: .delete,
            body: EmptyBody(),
            needsAuth: true
        ) as EmptyResponse
    }

    // MARK: - Request New Integration

    func requestNewIntegration(suggestion: String) async throws {
        struct SuggestionRequest: Codable {
            let suggestion: String
        }
        _ = try await httpClient.send(
            .integrationSuggestion,
            method: .post,
            body: SuggestionRequest(suggestion: suggestion),
            needsAuth: true
        ) as EmptyResponse
    }
}
// swiftlint:enable function_parameter_count
