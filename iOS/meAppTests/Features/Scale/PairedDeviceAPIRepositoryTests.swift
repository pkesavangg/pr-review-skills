import Foundation
import Testing
@testable import meApp

/// Tests for the Me App 2.0 unified `/paired-device/` CRUD endpoints and the unified `/review/`
/// endpoint on `ScaleAPIRepository` (MOB-383).
@Suite(.serialized)
@MainActor
struct PairedDeviceAPIRepositoryTests {

    private func makeSUT() -> (sut: ScaleAPIRepository, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = ScaleAPIRepository(httpClient: http)
        return (sut, http)
    }

    // MARK: - listPairedDevices

    @Test("listPairedDevices: GET /paired-device/ with auth, no filter, decodes responses")
    func listPairedDevicesNoFilter() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [
            ScaleTestFixtures.makePairedDeviceResponse(id: "d1", deviceType: "weight_scale"),
            ScaleTestFixtures.makePairedDeviceResponse(id: "d2", deviceType: "bpm")
        ]

        let result = try await sut.listPairedDevices(deviceType: nil)

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .pairedDevice(let filter) = http.lastGetEndpoint else {
            Issue.record("Expected .pairedDevice endpoint"); return
        }
        #expect(filter == nil)
        #expect(result.count == 2)
        #expect(result[0].id == "d1")
        #expect(result[1].deviceType == "bpm")
    }

    @Test("listPairedDevices: forwards deviceType filter to endpoint")
    func listPairedDevicesWithFilter() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [ScaleTestFixtures.makePairedDeviceResponse(id: "d1", deviceType: "bpm")]

        _ = try await sut.listPairedDevices(deviceType: "bpm")

        guard case .pairedDevice(let filter) = http.lastGetEndpoint else {
            Issue.record("Expected .pairedDevice endpoint"); return
        }
        #expect(filter == "bpm")
    }

    @Test("listPairedDevices: empty array decodes")
    func listPairedDevicesEmpty() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [PairedDeviceResponse]()

        let result = try await sut.listPairedDevices(deviceType: nil)

        #expect(result.isEmpty)
    }

    @Test("listPairedDevices: propagates error")
    func listPairedDevicesFailure() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.listPairedDevices(deviceType: nil)
        }
    }

    // MARK: - createPairedDevice

    @Test("createPairedDevice: POST /paired-device/ with auth, returns created device")
    func createPairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ScaleTestFixtures.makePairedDeviceResponse(id: "created-1")

        let result = try await sut.createPairedDevice(ScaleTestFixtures.makePairedDeviceRequest())

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedDevice(let filter) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedDevice endpoint"); return
        }
        #expect(filter == nil)
        #expect(result.id == "created-1")
    }

    @Test("createPairedDevice: encodes request body fields")
    func createPairedDevicePayload() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ScaleTestFixtures.makePairedDeviceResponse()
        let request = ScaleTestFixtures.makePairedDeviceRequest(
            deviceType: "baby_scale", type: "bluetooth", nickname: "Nursery", sku: "0220"
        )

        _ = try await sut.createPairedDevice(request)

        guard let body = http.lastSendBody as? PairedDeviceRequest else {
            Issue.record("Expected PairedDeviceRequest body"); return
        }
        #expect(body.deviceType == "baby_scale")
        #expect(body.type == "bluetooth")
        #expect(body.nickname == "Nursery")
        #expect(body.sku == "0220")
    }

    @Test("createPairedDevice: propagates error")
    func createPairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.apiError(message: "duplicate", code: 409)

        await #expect(throws: HTTPError.apiError(message: "duplicate", code: 409)) {
            try await sut.createPairedDevice(ScaleTestFixtures.makePairedDeviceRequest())
        }
    }

    // MARK: - updatePairedDevice

    @Test("updatePairedDevice: PATCH /paired-device/:id with auth, returns updated device")
    func updatePairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ScaleTestFixtures.makePairedDeviceResponse(id: "u1", nickname: "Bedroom Monitor")

        let result = try await sut.updatePairedDevice("u1", PairedDeviceUpdateRequest(nickname: "Bedroom Monitor"))

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedDeviceId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedDeviceId endpoint"); return
        }
        #expect(id == "u1")
        #expect(result.nickname == "Bedroom Monitor")
        guard let body = http.lastSendBody as? PairedDeviceUpdateRequest else {
            Issue.record("Expected PairedDeviceUpdateRequest body"); return
        }
        #expect(body.nickname == "Bedroom Monitor")
    }

    @Test("updatePairedDevice: propagates error")
    func updatePairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.updatePairedDevice("missing", PairedDeviceUpdateRequest(nickname: "x"))
        }
    }

    // MARK: - deletePairedDevice

    @Test("deletePairedDevice: DELETE /paired-device/:id with auth (204)")
    func deletePairedDeviceSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.deletePairedDevice("del-1")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .pairedDeviceId(let id) = http.lastSendEndpoint else {
            Issue.record("Expected .pairedDeviceId endpoint"); return
        }
        #expect(id == "del-1")
    }

    @Test("deletePairedDevice: propagates error")
    func deletePairedDeviceFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.deletePairedDevice("any")
        }
    }

    // MARK: - submitReview

    @Test("submitReview: POST /review/ with auth (204), encodes request body")
    func submitReviewSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let request = ReviewRequest(reviewType: .scale, status: .reviewed, rating: 5, sku: "0375", feedback: "Great")

        try await sut.submitReview(request)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .review = http.lastSendEndpoint else {
            Issue.record("Expected .review endpoint"); return
        }
        guard let body = http.lastSendBody as? ReviewRequest else {
            Issue.record("Expected ReviewRequest body"); return
        }
        #expect(body.reviewType == "scale")
        #expect(body.status == "reviewed")
        #expect(body.rating == 5)
        #expect(body.sku == "0375")
        #expect(body.feedback == "Great")
    }

    @Test("submitReview: propagates error")
    func submitReviewFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.submitReview(ReviewRequest(reviewType: .app, status: .exitA))
        }
    }
}
