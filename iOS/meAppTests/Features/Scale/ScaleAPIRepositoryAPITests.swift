import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ScaleAPIRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: ScaleAPIRepository, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = ScaleAPIRepository(httpClient: http)
        return (sut, http)
    }

    // MARK: - listScales

    @Test("listScales success: calls get with pairedScale endpoint with auth, returns decoded DTOs")
    func listScalesSuccess() async throws {
        let (sut, http) = makeSUT()
        let scales = [ScaleTestFixtures.makeScaleDTO(id: "s1"), ScaleTestFixtures.makeScaleDTO(id: "s2")]
        http.getResult = scales

        let result = try await sut.listScales()

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .pairedScale = http.lastGetEndpoint else {
            Issue.record("Expected .pairedScale endpoint"); return
        }
        #expect(result.count == 2)
        #expect(result[0].id == "s1")
        #expect(result[1].id == "s2")
    }

    @Test("listScales empty array: decodes empty response")
    func listScalesEmpty() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [ScaleDTO]()

        let result = try await sut.listScales()

        #expect(http.getCalls == 1)
        #expect(result.isEmpty)
    }

    @Test("listScales partial response: single scale decodes correctly")
    func listScalesPartialResponse() async throws {
        let (sut, http) = makeSUT()
        let scale = ScaleTestFixtures.makeScaleDTO(id: "only-one", displayName: "Solo Scale", sku: "R4-002")
        http.getResult = [scale]

        let result = try await sut.listScales()

        #expect(result.count == 1)
        #expect(result[0].id == "only-one")
        #expect(result[0].nickname == "AccuCheck Verve Smart Scale")
        #expect(result[0].sku == "R4-002")
    }

    @Test("listScales failure: propagates HTTPError")
    func listScalesFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.listScales()
        }
        #expect(http.getCalls == 1)
    }

    @Test("listScales failure: noInternet propagates")
    func listScalesNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.listScales()
        }
    }

    // MARK: - createScale

    @Test("createScale success: calls send with pairedScale POST with auth, returns decoded ScaleDTO")
    func createScaleSuccess() async throws {
        let (sut, http) = makeSUT()
        let request = ScaleTestFixtures.makeScaleDTO(id: nil, displayName: "New Scale")
        let response = ScaleTestFixtures.makeScaleDTO(id: "created-1", displayName: "New Scale")
        http.sendResult = response

        let result = try await sut.createScale(request)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedScale = http.lastSendEndpoint else {
            Issue.record("Expected .pairedScale endpoint"); return
        }
        #expect(result.id == "created-1")
        #expect(result.nickname == "AccuCheck Verve Smart Scale")
    }

    @Test("createScale request payload: body is ScaleDTO sent to endpoint")
    func createScalePayloadCorrectness() async throws {
        let (sut, http) = makeSUT()
        let scale = ScaleTestFixtures.makeScaleDTO(id: "temp", displayName: "Payload Scale", mac: "11:22:33:44:55:66")
        http.sendResult = scale

        _ = try await sut.createScale(scale)

        #expect(http.sendCalls == 1)
        guard let body = http.lastSendBody as? ScaleDTO else {
            Issue.record("Expected ScaleDTO body"); return
        }
        #expect(body.preference?.displayName == "Payload Scale")
        #expect(body.mac == "11:22:33:44:55:66")
    }

    @Test("createScale failure: propagates server error")
    func createScaleFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.apiError(message: "Scale already exists", code: 409)

        await #expect(throws: HTTPError.apiError(message: "Scale already exists", code: 409)) {
            try await sut.createScale(ScaleTestFixtures.makeScaleDTO())
        }
    }

    // MARK: - editScale

    @Test("editScale success: calls send with pairedScaleId endpoint PATCH with auth, returns ScaleDTO")
    func editScaleSuccess() async throws {
        let (sut, http) = makeSUT()
        let updated = ScaleTestFixtures.makeScaleDTO(id: "edit-1", displayName: "Edited Name")
        http.sendResult = updated

        let result = try await sut.editScale("edit-1", properties: updated)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedScaleId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedScaleId endpoint"); return
        }
        #expect(id == "edit-1")
        #expect(result.id == "edit-1")
        #expect(result.preference?.displayName == "Edited Name")
    }

    @Test("editScale payload: properties body encoded correctly")
    func editScalePayloadCorrectness() async throws {
        let (sut, http) = makeSUT()
        let properties = ScaleTestFixtures.makeScaleDTO(id: "p1", displayName: "Patched Nickname")
        http.sendResult = properties

        _ = try await sut.editScale("p1", properties: properties)

        guard let body = http.lastSendBody as? ScaleDTO else {
            Issue.record("Expected ScaleDTO body"); return
        }
        #expect(body.preference?.displayName == "Patched Nickname")
        #expect(body.id == "p1")
    }

    @Test("editScale failure: propagates error")
    func editScaleFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.editScale("missing-id", properties: ScaleTestFixtures.makeScaleDTO())
        }
    }

    // MARK: - deleteScale

    @Test("deleteScale success: calls send with pairedScaleId endpoint DELETE with auth")
    func deleteScaleSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deleteScale("scale-to-delete")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedScaleId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedScaleId endpoint"); return
        }
        #expect(id == "scale-to-delete")
    }

    @Test("deleteScale failure: propagates error")
    func deleteScaleFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.deleteScale("any-id")
        }
    }

    // MARK: - patchScaleMeta

    @Test("patchScaleMeta success: calls send with pairedScaleInfo endpoint PATCH with auth")
    func patchScaleMetaSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let meta = ScaleTestFixtures.makeScaleMetaDataDTO(modelNumber: "R4", serialNumber: "SN-123")

        try await sut.patchScaleMeta("scale-meta-1", metaData: meta)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedScaleInfo(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedScaleInfo endpoint"); return
        }
        #expect(id == "scale-meta-1")
    }

    @Test("patchScaleMeta payload: ScaleMetaDataDTO encoded correctly")
    func patchScaleMetaPayloadCorrectness() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let meta = ScaleTestFixtures.makeScaleMetaDataDTO(modelNumber: "R4", serialNumber: "serial-xyz", latestFirmwareVersion: "2.0.0")

        try await sut.patchScaleMeta("sid", metaData: meta)

        guard let body = http.lastSendBody as? ScaleMetaDataDTO else {
            Issue.record("Expected ScaleMetaDataDTO body"); return
        }
        #expect(body.modelNumber == "R4")
        #expect(body.serialNumber == "serial-xyz")
        #expect(body.latestFirmwareVersion == "2.0.0")
    }

    @Test("patchScaleMeta failure: propagates error")
    func patchScaleMetaFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.patchScaleMeta("id", metaData: ScaleTestFixtures.makeScaleMetaDataDTO())
        }
    }

    // MARK: - patchScalePreference

    @Test("patchScalePreference success: calls send with scaleR4Preference endpoint POST with auth")
    func patchScalePreferenceSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let pref = ScaleTestFixtures.makePreferenceDTO(scaleId: "pref-scale", displayName: "My Scale")

        try await sut.patchScalePreference(pref)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .scaleR4Preference = http.lastSendEndpoint else {
            Issue.record("Expected .scaleR4Preference endpoint"); return
        }
    }

    @Test("patchScalePreference payload: R4ScalePreferenceDTO encoded correctly")
    func patchScalePreferencePayloadCorrectness() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let pref = ScaleTestFixtures.makePreferenceDTO(scaleId: "s1", displayName: "Bathroom", displayMetrics: ["weight", "bmi"])

        try await sut.patchScalePreference(pref)

        guard let body = http.lastSendBody as? R4ScalePreferenceDTO else {
            Issue.record("Expected R4ScalePreferenceDTO body"); return
        }
        #expect(body.scaleId == "s1")
        #expect(body.displayName == "Bathroom")
        #expect(body.displayMetrics == ["weight", "bmi"])
    }

    @Test("patchScalePreference failure: propagates error")
    func patchScalePreferenceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.patchScalePreference(ScaleTestFixtures.makePreferenceDTO())
        }
    }

    // MARK: - Repeated calls / idempotent behaviour

    @Test("repeated listScales: multiple calls return same result, no side effects")
    func repeatedListScalesIdempotent() async throws {
        let (sut, http) = makeSUT()
        let scales = [ScaleTestFixtures.makeScaleDTO(id: "a")]
        http.getResult = scales

        let first = try await sut.listScales()
        let second = try await sut.listScales()

        #expect(http.getCalls == 2)
        #expect(first.count == 1)
        #expect(second.count == 1)
        #expect(first[0].id == second[0].id)
    }

    @Test("repeated deleteScale: second delete propagates error, no inconsistent state")
    func repeatedDeleteScale() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deleteScale("same-id")
        #expect(http.sendCalls == 1)

        http.sendError = HTTPError.notFound
        await #expect(throws: HTTPError.notFound) {
            try await sut.deleteScale("same-id")
        }
        #expect(http.sendCalls == 2)
    }

    @Test("repeated patchScalePreference: second patch succeeds (idempotent)")
    func repeatedPatchScalePreferenceIdempotent() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let pref = ScaleTestFixtures.makePreferenceDTO(scaleId: "s1", displayName: "Same")

        try await sut.patchScalePreference(pref)
        try await sut.patchScalePreference(pref)

        #expect(http.sendCalls == 2)
        guard case .scaleR4Preference = http.lastSendEndpoint else {
            Issue.record("Expected .scaleR4Preference"); return
        }
    }

    // MARK: - Unified Device API (Me App 2.0)

    @Test("createPairedDevice success: calls send with pairedDevice POST with auth, returns decoded response")
    func createPairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        let expected = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-1")
        http.sendResult = expected

        let req = ScaleTestFixtures.makePairedDeviceRequest()
        let result = try await sut.createPairedDevice(req)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendNeedsAuth == true)
        #expect(http.lastSendMethod == .post)
        guard case .pairedDevice(let dt) = http.lastSendEndpoint, dt == nil else {
            Issue.record("Expected .pairedDevice(deviceType: nil)"); return
        }
        #expect(result.id == "pd-1")
    }

    @Test("createPairedDevice failure: propagates server error")
    func createPairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: (any Error).self) {
            try await sut.createPairedDevice(ScaleTestFixtures.makePairedDeviceRequest())
        }
    }

    @Test("updatePairedDevice success: calls send with pairedDeviceId PATCH with auth, returns decoded response")
    func updatePairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        let expected = ScaleTestFixtures.makePairedDeviceResponse(id: "pd-1", nickname: "Updated")
        http.sendResult = expected

        let result = try await sut.updatePairedDevice("pd-1", PairedDeviceUpdateRequest(nickname: "Updated"))

        #expect(http.sendCalls == 1)
        #expect(http.lastSendNeedsAuth == true)
        #expect(http.lastSendMethod == .patch)
        guard case .pairedDeviceId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedDeviceId"); return
        }
        #expect(id == "pd-1")
        #expect(result.id == "pd-1")
    }

    @Test("updatePairedDevice failure: propagates error")
    func updatePairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: (any Error).self) {
            try await sut.updatePairedDevice("pd-1", PairedDeviceUpdateRequest(nickname: "X"))
        }
    }

    @Test("deletePairedDevice success: calls send with pairedDeviceId DELETE with auth")
    func deletePairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deletePairedDevice("pd-99")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendNeedsAuth == true)
        #expect(http.lastSendMethod == .delete)
        guard case .pairedDeviceId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedDeviceId"); return
        }
        #expect(id == "pd-99")
    }

    @Test("deletePairedDevice failure: propagates error")
    func deletePairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: (any Error).self) {
            try await sut.deletePairedDevice("pd-99")
        }
    }

    // MARK: - Unified Review API (Me App 2.0)

    @Test("submitReview success: calls send with review POST with auth (204 no content)")
    func submitReviewSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        let req = ReviewRequest(reviewType: .app, status: .ios, rating: 5)
        try await sut.submitReview(req)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendNeedsAuth == true)
        #expect(http.lastSendMethod == .post)
        guard case .review = http.lastSendEndpoint else {
            Issue.record("Expected .review endpoint"); return
        }
    }

    @Test("submitReview failure: propagates server error")
    func submitReviewFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: (any Error).self) {
            try await sut.submitReview(ReviewRequest(reviewType: .scale, status: .exitA))
        }
    }
}
