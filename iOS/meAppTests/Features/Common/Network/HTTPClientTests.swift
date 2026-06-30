import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HTTPClientTests {
    private struct HTTPClientTestResponse: Codable, Equatable {
        let value: String
    }

    // MARK: - Successful Response Handling

    @Test("get decodes valid JSON response")
    func get_validResponse_decodesSuccessfully() async throws {
        let responseData = try JSONEncoder().encode(HTTPClientTestResponse(value: "ok"))
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 200, request: request)
                return (responseData, response)
            }

        let result: HTTPClientTestResponse = try await sut.get(.login)

        #expect(result == HTTPClientTestResponse(value: "ok"))
    }

    @Test("send encodes request body and decodes response")
    func send_validBodyAndResponse_succeeds() async throws {
        let responseData = try JSONEncoder().encode(HTTPClientTestResponse(value: "created"))
        var capturedRequest: URLRequest?
        let (sut, _, _, _, _) = makeSUT { request in
                capturedRequest = request
                let response = makeHTTPResponse(statusCode: 201, request: request)
                return (responseData, response)
            }

        let result: HTTPClientTestResponse = try await sut.send(
            .signup,
            method: .post,
            body: EmptyBody(),
            needsAuth: false
        )

        #expect(result == HTTPClientTestResponse(value: "created"))
        #expect(capturedRequest?.httpMethod == HTTPMethod.post.rawValue)
        #expect(capturedRequest?.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(capturedRequest?.httpBody != nil)
    }

    @Test("get returns plain text when response type is String")
    func get_stringResponse_returnsPlainText() async throws {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 200, request: request)
                return (Data("plain-response".utf8), response)
            }

        let result: String = try await sut.get(.login)

        #expect(result == "plain-response")
    }

    @Test("get returns EmptyResponse for 204 no content")
    func get_noContent_returnsEmptyResponse() async throws {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 204, request: request)
                return (Data(), response)
            }

        let result: EmptyResponse = try await sut.get(.login)
        _ = result
        #expect(true)
    }

    @Test("get throws decodingError for 204 no content with non-empty response type")
    func get_noContent_withDecodableType_throwsDecodingError() async {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 204, request: request)
                return (Data(), response)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected decodingError for no-content response type mismatch")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.decodingError, got \(error)")
                return
            }
            if case .decodingError = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.decodingError, got \(httpError)")
            }
        }
    }

    // MARK: - Timeout and Offline Errors

    @Test("get throws noInternet when connectivity check fails and shows toast")
    func get_offlineConnectivity_throwsNoInternetAndShowsToast() async {
        let (sut, _, notification, _, _) = makeSUT(connectivity: false)

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.noInternet")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.noInternet, got \(error)")
                return
            }
            if case .noInternet = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.noInternet, got \(httpError)")
            }
        }

        #expect(notification.showToastCalls == 1)
    }

    @Test("get throttles duplicate offline toasts within short interval")
    func get_offlineConnectivity_throttlesToast() async {
        let (sut, _, notification, _, _) = makeSUT(connectivity: false)

        let firstAttempt: HTTPClientTestResponse? = try? await sut.get(.login)
        let secondAttempt: HTTPClientTestResponse? = try? await sut.get(.login)
        _ = firstAttempt
        _ = secondAttempt

        #expect(notification.showToastCalls == 1)
    }

    @Test("get throws noInternet without toast when skipCheckNetwork is true")
    func get_offlineWithSkipCheckNetwork_throwsNoInternetWithoutToast() async {
        let (sut, _, notification, _, _) = makeSUT(connectivity: false)
        sut.skipCheckNetwork = true

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.noInternet")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.noInternet, got \(error)")
                return
            }
            if case .noInternet = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.noInternet, got \(httpError)")
            }
        }

        #expect(notification.showToastCalls == 0)
    }

    @Test("get maps URLError.timedOut to HTTPError.timeout")
    func get_timeoutURLError_mapsToTimeout() async {
        let (sut, _, _, _, _) = makeSUT { _ in
                throw URLError(.timedOut)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.timeout")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.timeout, got \(error)")
                return
            }
            if case .timeout = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.timeout, got \(httpError)")
            }
        }
    }

    @Test("get maps network URLError to HTTPError.noInternet")
    func get_networkURLError_mapsToNoInternet() async {
        let (sut, _, _, _, _) = makeSUT { _ in
                throw URLError(.notConnectedToInternet)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.noInternet")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.noInternet, got \(error)")
                return
            }
            if case .noInternet = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.noInternet, got \(httpError)")
            }
        }
    }

    // MARK: - Invalid Response Handling

    @Test("get throws invalidResponse for non-HTTP URLResponse")
    func get_nonHTTPResponse_throwsInvalidResponse() async {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = URLResponse(
                    url: request.url ?? URL(string: "https://example.com")!,
                    mimeType: nil,
                    expectedContentLength: 0,
                    textEncodingName: nil
                )
                return (Data(), response)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.invalidResponse")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.invalidResponse, got \(error)")
                return
            }
            if case .invalidResponse = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.invalidResponse, got \(httpError)")
            }
        }
    }

    @Test("get throws statusCode for unknown HTTP status")
    func get_unknownStatusCode_throwsStatusCode() async {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 418, request: request)
                return (Data(), response)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.statusCode(418)")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.statusCode(418), got \(error)")
                return
            }
            if case .statusCode(let code) = httpError {
                #expect(code == 418)
            } else {
                Issue.record("Expected HTTPError.statusCode(418), got \(httpError)")
            }
        }
    }

    @Test("get throws decodingError for malformed JSON response")
    func get_malformedJSON_throwsDecodingError() async {
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 200, request: request)
                return (Data("not-json".utf8), response)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.decodingError")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.decodingError, got \(error)")
                return
            }
            if case .decodingError = httpError {
                #expect(true)
            } else {
                Issue.record("Expected HTTPError.decodingError, got \(httpError)")
            }
        }
    }

    @Test("get parses API error payload for non-success status")
    func get_errorPayload_parsesAPIErrorMessage() async {
        let errorPayload = Data(#"{"error":"bad request payload"}"#.utf8)
        let (sut, _, _, _, _) = makeSUT { request in
                let response = makeHTTPResponse(statusCode: 400, request: request)
                return (errorPayload, response)
            }

        do {
            let _: HTTPClientTestResponse = try await sut.get(.login)
            Issue.record("Expected get to throw HTTPError.apiError")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.apiError, got \(error)")
                return
            }
            if case .apiError(let message, let code) = httpError {
                #expect(message == "bad request payload")
                #expect(code == HTTPStatusCode.badRequest.rawValue)
            } else {
                Issue.record("Expected HTTPError.apiError, got \(httpError)")
            }
        }
    }

    // MARK: - Retry Behavior

    @Test("get with auth refreshes expired token before request and succeeds")
    func get_needsAuthExpiredToken_refreshesBeforeRequest() async throws {
        // swiftlint:disable:next no_hardcoded_credentials
        let account = makeActiveAccount(accessToken: "old-token")
        let tokenManager = MockHTTPClientTokenManager()
        tokenManager.checkTokenExpirationResult = true
        tokenManager.refreshTokenResult = .success(makeTokens(access: "new-token"))
        let responseData = try JSONEncoder().encode(HTTPClientTestResponse(value: "ok"))
        var capturedAuthHeader: String?
        let (sut, _, _, _, _) = makeSUT(
            account: account,
            tokenManager: tokenManager
        ) { request in
                capturedAuthHeader = request.value(forHTTPHeaderField: "Authorization")
                let response = makeHTTPResponse(statusCode: 200, request: request)
                return (responseData, response)
            }

        let result: HTTPClientTestResponse = try await sut.get(.accountInfo, needsAuth: true)

        #expect(result.value == "ok")
        #expect(tokenManager.checkTokenExpirationCalls == 1)
        #expect(tokenManager.refreshTokenCalls == 1)
        #expect(capturedAuthHeader == "Bearer new-token")
    }

    @Test("get with auth retries once on unauthorized using refreshed token")
    func get_needsAuthUnauthorized_retriesWithRefreshedToken() async throws {
        // swiftlint:disable:next no_hardcoded_credentials
        let account = makeActiveAccount(accessToken: "old-token")
        let tokenManager = MockHTTPClientTokenManager()
        tokenManager.checkTokenExpirationResult = false
        tokenManager.refreshTokenResult = .success(makeTokens(access: "retry-token"))
        let successData = try JSONEncoder().encode(HTTPClientTestResponse(value: "ok"))
        var authHeaders: [String?] = []
        var callCount = 0

        let (sut, _, _, _, _) = makeSUT(
            account: account,
            tokenManager: tokenManager
        ) { request in
                callCount += 1
                authHeaders.append(request.value(forHTTPHeaderField: "Authorization"))

                if callCount == 1 {
                    let unauthorized = makeHTTPResponse(statusCode: 401, request: request)
                    return (Data(), unauthorized)
                }

                let success = makeHTTPResponse(statusCode: 200, request: request)
                return (successData, success)
            }

        let result: HTTPClientTestResponse = try await sut.get(.accountInfo, needsAuth: true)

        #expect(result.value == "ok")
        #expect(callCount == 2)
        #expect(tokenManager.refreshTokenCalls == 1)
        #expect(authHeaders.first == "Bearer old-token")
        #expect(authHeaders.last == "Bearer retry-token")
    }

    @Test("get with accountId uses fetchAccount token for auth header")
    func get_needsAuthWithAccountId_usesFetchedAccountToken() async throws {
        let accountService = MockTokenManagerAccountService()
        let fetched = AccountTestFixtures.makeAccountSnapshot(
            id: "acc-2",
            email: "b@example.com",
            isLoggedIn: true,
            // swiftlint:disable:next no_hardcoded_credentials
            accessToken: "fetched-token",
            expiresAt: "2099-01-01T00:00:00.000Z"
        )
        accountService.fetchAccountById["acc-2"] = fetched

        let tokenManager = MockHTTPClientTokenManager()
        tokenManager.checkTokenExpirationResult = false

        var authHeader: String?
        let responseData = try JSONEncoder().encode(HTTPClientTestResponse(value: "ok"))
        let (sut, _, _, _, _) = makeSUT(
            account: accountService,
            tokenManager: tokenManager
        ) { request in
                authHeader = request.value(forHTTPHeaderField: "Authorization")
                let response = makeHTTPResponse(statusCode: 200, request: request)
                return (responseData, response)
            }

        let result: HTTPClientTestResponse = try await sut.get(.accountInfo, needsAuth: true, accountId: "acc-2")

        #expect(result.value == "ok")
        #expect(accountService.fetchAccountCalls == 2)
        #expect(accountService.lastFetchAccountId == "acc-2")
        #expect(authHeader == "Bearer fetched-token")
    }

    // MARK: - Helpers

    private func makeSUT(
        account: MockTokenManagerAccountService? = nil,
        notification: MockNotificationHelperService? = nil,
        logger: MockLoggerService? = nil,
        tokenManager: MockHTTPClientTokenManager? = nil,
        connectivity: Bool = true,
        requestExecutor: ((URLRequest) async throws -> (Data, URLResponse))? = nil
    ) -> ( // swiftlint:disable:this large_tuple
        sut: HTTPClient,
        account: MockTokenManagerAccountService,
        notification: MockNotificationHelperService,
        logger: MockLoggerService,
        tokenManager: MockHTTPClientTokenManager
    ) {
        // swiftlint:disable:next no_hardcoded_credentials
        let account = account ?? makeActiveAccount(accessToken: "active-token")
        let notification = notification ?? MockNotificationHelperService()
        let logger = logger ?? MockLoggerService()
        let tokenManager = tokenManager ?? MockHTTPClientTokenManager()

        let executor = requestExecutor ?? { request in
            let data = try JSONEncoder().encode(HTTPClientTestResponse(value: "ok"))
            let response = makeHTTPResponse(statusCode: 200, request: request)
            return (data, response)
        }

        TestDependencyContainer.reset()
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let sut = HTTPClient(
            tokenManager: tokenManager,
            requestExecutor: executor
        ) { connectivity }
        return (sut, account, notification, logger, tokenManager)
    }

    private func makeActiveAccount(
        accessToken: String,
        accountId: String = "acc-1",
        expiresAt: String = "2099-01-01T00:00:00.000Z"
    ) -> MockTokenManagerAccountService {
        let accountService = MockTokenManagerAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: accountId,
            email: "user@example.com",
            isLoggedIn: true,
            isActiveAccount: true,
            accessToken: accessToken,
            expiresAt: expiresAt
        )
        return accountService
    }

    private func makeTokens(
        access: String = "new-token",
        refresh: String = "refresh-token",
        expiresAt: String = "2099-01-01T00:00:00.000Z"
    ) -> Tokens {
        Tokens(accessToken: access, refreshToken: refresh, expiresAt: expiresAt)
    }

    private func makeHTTPResponse(statusCode: Int, request: URLRequest) -> HTTPURLResponse {
        let url = request.url ?? URL(string: "https://example.com")!
        // swiftlint:disable:next force_unwrapping
        return HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil)!
    }
}
