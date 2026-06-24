// DeviceAPIRepository.swift
// Implements remote API access for paired scale operations.
// Fulfills DeviceRepositoryProtocol.

import Foundation

@MainActor
final class DeviceAPIRepository: DeviceRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func listScales() async throws -> [DeviceDTO] {
        // GET /paired-scale
        return try await httpClient.get(
            .pairedScale,
            needsAuth: true
        )
    }
    
    func createScale(_ scale: DeviceDTO) async throws -> DeviceDTO {
        // POST /paired-scale
        return try await httpClient.send(
            .pairedScale,
            method: .post,
            body: scale,
            needsAuth: true
        )
    }
    
    func editScale(_ scaleId: String, properties: DeviceDTO) async throws -> DeviceDTO {
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
    
    func patchScaleMeta(_ scaleId: String, metaData: DeviceMetaDataDTO) async throws {
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

    // MARK: - Unified Device API (Me App 2.0)

    func listPairedDevices(deviceType: String?) async throws -> [PairedDeviceResponse] {
        // GET /paired-device/?deviceType=
        return try await httpClient.get(
            .pairedDevice(deviceType: deviceType),
            needsAuth: true
        )
    }

    func createPairedDevice(_ request: PairedDeviceRequest) async throws -> PairedDeviceResponse {
        // POST /paired-device/
        return try await httpClient.send(
            .pairedDevice(deviceType: nil),
            method: .post,
            body: request,
            needsAuth: true
        )
    }

    func updatePairedDevice(_ deviceId: String, _ request: PairedDeviceUpdateRequest) async throws -> PairedDeviceResponse {
        // PATCH /paired-device/:deviceId
        return try await httpClient.send(
            .pairedDeviceId(deviceId),
            method: .patch,
            body: request,
            needsAuth: true
        )
    }

    func deletePairedDevice(_ deviceId: String) async throws {
        // DELETE /paired-device/:deviceId (204)
        _ = try await httpClient.send(
            .pairedDeviceId(deviceId),
            method: .delete,
            body: EmptyBody(),
            needsAuth: true
        ) as EmptyResponse
    }

    // MARK: - Unified Review API (Me App 2.0)

    func submitReview(_ request: ReviewRequest) async throws {
        // POST /review/ (204)
        _ = try await httpClient.send(
            .review,
            method: .post,
            body: request,
            needsAuth: true
        ) as EmptyResponse
    }
}
