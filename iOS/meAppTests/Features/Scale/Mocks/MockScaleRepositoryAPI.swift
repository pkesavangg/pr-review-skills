import Foundation
@testable import meApp

final class MockScaleRepositoryAPI: ScaleRepositoryAPIProtocol {
    var listScalesResult: [ScaleDTO] = []
    var createScaleResult: ScaleDTO = ScaleDTO()
    var editScaleResult: ScaleDTO = ScaleDTO()
    var createScaleError: Error?
    var listScalesError: Error?
    var editScaleError: Error?
    var deleteScaleError: Error?
    var patchScaleMetaError: Error?
    var patchScalePreferenceError: Error?

    // Unified Device + Review API (Me App 2.0)
    var listPairedDevicesResult: [PairedDeviceResponse] = []
    var createPairedDeviceResult = ScaleTestFixtures.makePairedDeviceResponse(id: "paired-1")
    var updatePairedDeviceResult = ScaleTestFixtures.makePairedDeviceResponse(id: "paired-1")
    var listPairedDevicesError: Error?
    var createPairedDeviceError: Error?
    var updatePairedDeviceError: Error?
    var deletePairedDeviceError: Error?
    var submitReviewError: Error?

    private(set) var listScalesCalls = 0
    private(set) var createScaleCalls = 0
    private(set) var editScaleCalls = 0
    private(set) var deleteScaleCalls = 0
    private(set) var patchScaleMetaCalls = 0
    private(set) var patchScalePreferenceCalls = 0
    private(set) var lastDeletedScaleId: String?
    private(set) var lastEditedScaleId: String?
    private(set) var lastEditedScale: ScaleDTO?
    private(set) var lastPatchedMetaScaleId: String?
    private(set) var lastPatchedMetaData: ScaleMetaDataDTO?
    private(set) var lastCreatedScale: ScaleDTO?
    private(set) var lastPatchedPreference: R4ScalePreferenceDTO?

    private(set) var listPairedDevicesCalls = 0
    private(set) var createPairedDeviceCalls = 0
    private(set) var updatePairedDeviceCalls = 0
    private(set) var deletePairedDeviceCalls = 0
    private(set) var submitReviewCalls = 0
    private(set) var lastListedDeviceTypeFilter: String?
    private(set) var lastCreatedPairedDevice: PairedDeviceRequest?
    private(set) var lastUpdatedPairedDeviceId: String?
    private(set) var lastUpdatedPairedDevice: PairedDeviceUpdateRequest?
    private(set) var lastDeletedPairedDeviceId: String?
    private(set) var lastSubmittedReview: ReviewRequest?

    func listScales() async throws -> [ScaleDTO] {
        listScalesCalls += 1
        if let listScalesError { throw listScalesError }
        return listScalesResult
    }

    func createScale(_ scale: ScaleDTO) async throws -> ScaleDTO {
        createScaleCalls += 1
        lastCreatedScale = scale
        if let createScaleError { throw createScaleError }
        return createScaleResult
    }

    func editScale(_ scaleId: String, properties: ScaleDTO) async throws -> ScaleDTO {
        editScaleCalls += 1
        lastEditedScaleId = scaleId
        lastEditedScale = properties
        if let editScaleError { throw editScaleError }
        return editScaleResult
    }

    func deleteScale(_ scaleId: String) async throws {
        deleteScaleCalls += 1
        lastDeletedScaleId = scaleId
        if let deleteScaleError { throw deleteScaleError }
    }

    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async throws {
        patchScaleMetaCalls += 1
        lastPatchedMetaScaleId = scaleId
        lastPatchedMetaData = metaData
        if let patchScaleMetaError { throw patchScaleMetaError }
    }

    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async throws {
        patchScalePreferenceCalls += 1
        lastPatchedPreference = preference
        if let patchScalePreferenceError { throw patchScalePreferenceError }
    }

    // MARK: - Unified Device API (Me App 2.0)

    func listPairedDevices(deviceType: String?) async throws -> [PairedDeviceResponse] {
        listPairedDevicesCalls += 1
        lastListedDeviceTypeFilter = deviceType
        if let listPairedDevicesError { throw listPairedDevicesError }
        return listPairedDevicesResult
    }

    func createPairedDevice(_ request: PairedDeviceRequest) async throws -> PairedDeviceResponse {
        createPairedDeviceCalls += 1
        lastCreatedPairedDevice = request
        if let createPairedDeviceError { throw createPairedDeviceError }
        return createPairedDeviceResult
    }

    func updatePairedDevice(_ deviceId: String, _ request: PairedDeviceUpdateRequest) async throws -> PairedDeviceResponse {
        updatePairedDeviceCalls += 1
        lastUpdatedPairedDeviceId = deviceId
        lastUpdatedPairedDevice = request
        if let updatePairedDeviceError { throw updatePairedDeviceError }
        return updatePairedDeviceResult
    }

    func deletePairedDevice(_ deviceId: String) async throws {
        deletePairedDeviceCalls += 1
        lastDeletedPairedDeviceId = deviceId
        if let deletePairedDeviceError { throw deletePairedDeviceError }
    }

    // MARK: - Unified Review API (Me App 2.0)

    func submitReview(_ request: ReviewRequest) async throws {
        submitReviewCalls += 1
        lastSubmittedReview = request
        if let submitReviewError { throw submitReviewError }
    }
}
