import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct PushNotificationRepositoryAPIAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: PushNotificationRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = PushNotificationRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - Fixtures

    private func makeDeviceInfoRequest(
        appVersion: String = "1.0.0",
        deviceManufacturer: String = "Apple",
        deviceOSName: String = "iOS",
        deviceOSVersion: String = "18.0",
        deviceUUID: String = "uuid-123",
        deviceModel: String = "iPhone",
        fcmToken: String = "fcm-token-abc"
    ) -> DeviceInfoRequest {
        DeviceInfoRequest(
            appVersion: appVersion,
            deviceManufacturer: deviceManufacturer,
            deviceOSName: deviceOSName,
            deviceOSVersion: deviceOSVersion,
            deviceUUID: deviceUUID,
            deviceModel: deviceModel,
            fcmToken: fcmToken
        )
    }

    // MARK: - Request construction & endpoint selection

    @Test("updateDeviceInfo success: calls send with .updateDeviceInfo endpoint, PATCH, needsAuth true")
    func updateDeviceInfoSuccessRequestConstruction() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.updateDeviceInfo(makeDeviceInfoRequest())

        #expect(http.sendCalls == 1)
        guard case .updateDeviceInfo = http.lastSendEndpoint else {
            Issue.record("Expected .updateDeviceInfo endpoint"); return
        }
        #expect(http.lastSendMethod == .patch)
        #expect(http.lastSendNeedsAuth == true)
    }

    @Test("updateDeviceInfo endpoint: uses PATCH /account/device/ (contract)")
    func updateDeviceInfoEndpointContract() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.updateDeviceInfo(makeDeviceInfoRequest())

        guard case .updateDeviceInfo = http.lastSendEndpoint else {
            Issue.record("Expected .updateDeviceInfo endpoint (account/device)"); return
        }
    }

    // MARK: - Payload encoding

    @Test("updateDeviceInfo payload: body is same DeviceInfoRequest sent to client")
    func updateDeviceInfoPayloadEncoding() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let info = makeDeviceInfoRequest(
            appVersion: "2.5.0",
            deviceManufacturer: "Apple",
            deviceOSName: "iOS",
            deviceOSVersion: "18.5",
            deviceUUID: "device-uuid-xyz",
            deviceModel: "iPhone16,1",
            fcmToken: "fcm-token-xyz"
        )

        try await sut.updateDeviceInfo(info)

        #expect(http.sendCalls == 1)
        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest body"); return
        }
        #expect(body.appVersion == "2.5.0")
        #expect(body.deviceOSVersion == "18.5")
        #expect(body.deviceUUID == "device-uuid-xyz")
        #expect(body.deviceModel == "iPhone16,1")
        #expect(body.fcmToken == "fcm-token-xyz")
    }

    @Test("updateDeviceInfo payload: all fields encoded and passed through")
    func updateDeviceInfoPayloadAllFields() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let info = makeDeviceInfoRequest(
            appVersion: "1.0",
            deviceManufacturer: "Apple",
            deviceOSName: "iOS",
            deviceOSVersion: "17.0",
            deviceUUID: "uuid",
            deviceModel: "iPad",
            fcmToken: "token"
        )

        try await sut.updateDeviceInfo(info)

        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest body"); return
        }
        #expect(body.appVersion == "1.0")
        #expect(body.deviceManufacturer == "Apple")
        #expect(body.deviceOSName == "iOS")
        #expect(body.deviceOSVersion == "17.0")
        #expect(body.deviceUUID == "uuid")
        #expect(body.deviceModel == "iPad")
        #expect(body.fcmToken == "token")
    }

    // MARK: - Response decoding & success

    @Test("updateDeviceInfo success: does not throw when client returns EmptyResponse")
    func updateDeviceInfoSuccessNoThrow() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.updateDeviceInfo(makeDeviceInfoRequest())

        #expect(http.sendCalls == 1)
    }

    // MARK: - Error propagation & status mapping

    @Test("updateDeviceInfo failure: propagates serverError from http client")
    func updateDeviceInfoFailureServerError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateDeviceInfo failure: propagates unauthorized from http client")
    func updateDeviceInfoFailureUnauthorized() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateDeviceInfo failure: propagates noInternet from http client")
    func updateDeviceInfoFailureNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateDeviceInfo failure: propagates timeout from http client")
    func updateDeviceInfoFailureTimeout() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateDeviceInfo failure: propagates notFound from http client")
    func updateDeviceInfoFailureNotFound() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
    }

    @Test("updateDeviceInfo failure: propagates apiError from http client")
    func updateDeviceInfoFailureApiError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.apiError(message: "Invalid device", code: 400)

        await #expect(throws: HTTPError.apiError(message: "Invalid device", code: 400)) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateDeviceInfo failure: propagates decodingError from http client")
    func updateDeviceInfoFailureDecodingError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.decodingError

        await #expect(throws: HTTPError.decodingError) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - Retry-safe behavior

    @Test("updateDeviceInfo repeated calls: second call succeeds (retry-safe contract)")
    func updateDeviceInfoRepeatedCalls() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        try await sut.updateDeviceInfo(makeDeviceInfoRequest(fcmToken: "fcm-token-updated"))

        #expect(http.sendCalls == 2)
        guard case .updateDeviceInfo = http.lastSendEndpoint else {
            Issue.record("Expected .updateDeviceInfo on second call"); return
        }
        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest on second call"); return
        }
        #expect(body.fcmToken == "fcm-token-updated")
    }

    @Test("updateDeviceInfo after failure: next call still uses correct endpoint and body")
    func updateDeviceInfoAfterFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.updateDeviceInfo(makeDeviceInfoRequest())
        }
        #expect(http.sendCalls == 1)

        http.sendError = nil
        http.sendResult = EmptyResponse()

        try await sut.updateDeviceInfo(makeDeviceInfoRequest(appVersion: "2.0", fcmToken: "retry-token"))

        #expect(http.sendCalls == 2)
        guard case .updateDeviceInfo = http.lastSendEndpoint else {
            Issue.record("Expected .updateDeviceInfo on retry"); return
        }
        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest on retry"); return
        }
        #expect(body.appVersion == "2.0")
        #expect(body.fcmToken == "retry-token")
    }

    // MARK: - Malformed / edge payload handling

    @Test("updateDeviceInfo edge payload: empty string fields are passed through")
    func updateDeviceInfoEdgeEmptyStrings() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let info = makeDeviceInfoRequest(
            appVersion: "",
            deviceManufacturer: "",
            deviceOSName: "",
            deviceOSVersion: "",
            deviceUUID: "",
            deviceModel: "",
            fcmToken: ""
        )

        try await sut.updateDeviceInfo(info)

        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest"); return
        }
        #expect(body.appVersion == "")
        #expect(body.fcmToken == "")
    }

    @Test("updateDeviceInfo edge payload: long fcmToken is passed through")
    func updateDeviceInfoEdgeLongFcmToken() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let longToken = String(repeating: "x", count: 500)
        let info = makeDeviceInfoRequest(fcmToken: longToken)

        try await sut.updateDeviceInfo(info)

        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest"); return
        }
        #expect(body.fcmToken == longToken)
    }

    @Test("updateDeviceInfo edge payload: special characters in fields are passed through")
    func updateDeviceInfoEdgeSpecialCharacters() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let info = makeDeviceInfoRequest(
            deviceUUID: "uuid-with-dash-123",
            fcmToken: "token:with:colons"
        )

        try await sut.updateDeviceInfo(info)

        guard let body = http.lastSendBody as? DeviceInfoRequest else {
            Issue.record("Expected DeviceInfoRequest"); return
        }
        #expect(body.deviceUUID == "uuid-with-dash-123")
        #expect(body.fcmToken == "token:with:colons")
    }
}
