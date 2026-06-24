import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct WifiScaleRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: WifiDeviceRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = WifiDeviceRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - Request Construction

    @Test("getScaleToken with request: builds wifiScale endpoint with request parameter")
    func getScaleTokenWithRequest() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "some-request-value")

        #expect(http.getCalls == 1)
        guard case .wifiScale(let request) = http.lastGetEndpoint else {
            Issue.record("Expected .wifiScale endpoint"); return
        }
        #expect(request == "some-request-value")
    }

    @Test("getScaleToken with nil request: builds wifiScale endpoint with nil parameter")
    func getScaleTokenWithNilRequest() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: nil)

        #expect(http.getCalls == 1)
        guard case .wifiScale(let request) = http.lastGetEndpoint else {
            Issue.record("Expected .wifiScale endpoint"); return
        }
        #expect(request == nil)
    }

    @Test("getScaleToken: requires authentication")
    func getScaleTokenRequiresAuth() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "test")

        #expect(http.lastGetNeedsAuth == true)
    }

    // MARK: - Endpoint Usage

    @Test("getScaleToken: uses wifiScale endpoint regardless of request value")
    func getScaleTokenUsesCorrectEndpoint() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "abc")

        guard case .wifiScale = http.lastGetEndpoint else {
            Issue.record("Expected .wifiScale endpoint"); return
        }
    }

    // MARK: - Response Decoding (Success)

    @Test("getScaleToken success: returns decoded WifiScaleTokenResponse with correct token")
    func getScaleTokenSuccessDecodesResponse() async throws {
        let (sut, http) = makeSUT()
        let expected = WifiScaleTestFixtures.makeTokenResponse(token: "my-wifi-token-xyz")
        http.getResult = expected

        let result = try await sut.getScaleToken(request: "r-value")

        #expect(result.token == "my-wifi-token-xyz")
    }

    @Test("getScaleToken success with empty token: returns response with empty string")
    func getScaleTokenSuccessEmptyToken() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse(token: "")

        let result = try await sut.getScaleToken(request: nil)

        #expect(result.token == "")
    }

    @Test("getScaleToken success with long token: returns full token value")
    func getScaleTokenSuccessLongToken() async throws {
        let (sut, http) = makeSUT()
        let longToken = String(repeating: "a", count: 512)
        http.getResult = WifiScaleTestFixtures.makeTokenResponse(token: longToken)

        let result = try await sut.getScaleToken(request: "test")

        #expect(result.token == longToken)
        #expect(result.token.count == 512)
    }

    // MARK: - Status and Error Mapping

    @Test("getScaleToken failure: propagates serverError")
    func getScaleTokenServerError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.getScaleToken(request: "test")
        }
        #expect(http.getCalls == 1)
    }

    @Test("getScaleToken failure: propagates unauthorized")
    func getScaleTokenUnauthorized() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: propagates notFound")
    func getScaleTokenNotFound() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.getScaleToken(request: nil)
        }
    }

    @Test("getScaleToken failure: propagates badRequest")
    func getScaleTokenBadRequest() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.badRequest

        await #expect(throws: HTTPError.badRequest) {
            try await sut.getScaleToken(request: "bad")
        }
    }

    @Test("getScaleToken failure: propagates forbidden")
    func getScaleTokenForbidden() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.forbidden

        await #expect(throws: HTTPError.forbidden) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: propagates apiError with message and code")
    func getScaleTokenApiError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.apiError(message: "Token expired", code: 422)

        await #expect(throws: HTTPError.apiError(message: "Token expired", code: 422)) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: propagates statusCode error")
    func getScaleTokenStatusCodeError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.statusCode(503)

        await #expect(throws: HTTPError.statusCode(503)) {
            try await sut.getScaleToken(request: "test")
        }
    }

    // MARK: - Network Failure

    @Test("getScaleToken failure: propagates noInternet")
    func getScaleTokenNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: propagates timeout")
    func getScaleTokenTimeout() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.getScaleToken(request: nil)
        }
    }

    // MARK: - Malformed / Invalid Response Handling

    @Test("getScaleToken failure: propagates decodingError for malformed response")
    func getScaleTokenDecodingError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.decodingError

        await #expect(throws: HTTPError.decodingError) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: propagates invalidResponse")
    func getScaleTokenInvalidResponse() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.invalidResponse

        await #expect(throws: HTTPError.invalidResponse) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: type mismatch throws when result is wrong type")
    func getScaleTokenTypeMismatch() async throws {
        let (sut, http) = makeSUT()
        http.getResult = "not-a-token-response"

        await #expect(throws: MockHTTPClient.MockError.typeMismatch) {
            try await sut.getScaleToken(request: "test")
        }
    }

    @Test("getScaleToken failure: no result configured throws noResultConfigured")
    func getScaleTokenNoResultConfigured() async throws {
        let (sut, http) = makeSUT()
        // Do not set getResult or getError

        await #expect(throws: MockHTTPClient.MockError.noResultConfigured) {
            try await sut.getScaleToken(request: "test")
        }
    }

    // MARK: - Repeated Calls

    @Test("repeated getScaleToken: multiple calls increment call count correctly")
    func repeatedGetScaleTokenCallCount() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "first")
        _ = try await sut.getScaleToken(request: "second")
        _ = try await sut.getScaleToken(request: nil)

        #expect(http.getCalls == 3)
    }

    @Test("repeated getScaleToken: last endpoint captures most recent call parameters")
    func repeatedGetScaleTokenLastEndpoint() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "first-request")
        _ = try await sut.getScaleToken(request: "second-request")

        guard case .wifiScale(let request) = http.lastGetEndpoint else {
            Issue.record("Expected .wifiScale endpoint"); return
        }
        #expect(request == "second-request")
    }

    @Test("getScaleToken after error: subsequent success call works correctly")
    func getScaleTokenRecoveryAfterError() async throws {
        let (sut, http) = makeSUT()

        http.getError = HTTPError.serverError
        await #expect(throws: HTTPError.serverError) {
            try await sut.getScaleToken(request: "fail")
        }

        http.getError = nil
        http.getResult = WifiScaleTestFixtures.makeTokenResponse(token: "recovered-token")
        let result = try await sut.getScaleToken(request: "succeed")

        #expect(http.getCalls == 2)
        #expect(result.token == "recovered-token")
    }

    // MARK: - Account ID

    @Test("getScaleToken: does not pass account ID (uses default nil)")
    func getScaleTokenNoAccountId() async throws {
        let (sut, http) = makeSUT()
        http.getResult = WifiScaleTestFixtures.makeTokenResponse()

        _ = try await sut.getScaleToken(request: "test")

        #expect(http.lastGetAccountId == nil)
    }
}
