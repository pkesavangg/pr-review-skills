// ScaleAPIRepository.swift
// Implements remote API access for paired scale operations.
// Fulfills ScaleRepositoryProtocol.

import Foundation

@MainActor
final class ScaleAPIRepository: ScaleRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func listScales() async throws -> [ScaleDTO] {
        // GET /paired-scale
        return try await httpClient.get(
            .pairedScale,
            needsAuth: true
        )
    }
    
    func createScale(_ scale: ScaleDTO) async throws -> ScaleDTO {
        // POST /paired-scale
        return try await httpClient.send(
            .pairedScale,
            method: .post,
            body: scale,
            needsAuth: true
        )
    }
    
    func editScale(_ scaleId: String, properties: ScaleDTO) async throws -> ScaleDTO {
        // PATCH /paired-scale/:scaleId
        let endpoint = Endpoint.pairedScaleId(scaleId)
        return try await httpClient.send(
            endpoint,
            method: .patch,
            body: properties,
            needsAuth: true
        )
    }
    
    func deleteScale(_ scaleId: String) async throws {
        // DELETE /paired-scale/:scaleId
        let endpoint = Endpoint.pairedScaleId(scaleId)
        _ = try await httpClient.send(
            endpoint,
            method: .delete,
            body: EmptyBody(),
            needsAuth: true
        ) as EmptyResponse
    }
    
    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async throws {
        // PATCH /paired-scale/:scaleId/info
        let endpoint = Endpoint.pairedScaleInfo(scaleId)
        _ = try await httpClient.send(
            endpoint,
            method: .patch,
            body: metaData,
            needsAuth: true
        ) as EmptyResponse
    }
    
    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async throws {
        // PATCH /scale-r4/preference
        let endpoint = Endpoint.scaleR4Preference
        _ = try await httpClient.send(
            endpoint,
            method: .post,
            body: preference,
            needsAuth: true
        ) as EmptyResponse
    }
}
