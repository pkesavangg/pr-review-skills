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
}
