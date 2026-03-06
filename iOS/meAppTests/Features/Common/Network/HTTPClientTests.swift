import Foundation
import Testing
@testable import meApp

// MARK: - Test Helpers

private struct TestResponse: Codable, Equatable {
    let id: Int
    let name: String
}

private struct TestRequestBody: Codable, Equatable {
    let value: String
}

/// Creates a fake HTTP response for test request executors.
private func makeHTTPResponse(
    url: URL = URL(string: "https://example.com")!,
    statusCode: Int = 200
) -> HTTPURLResponse {
    HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil)!
}

// MARK: - Test Suite

@Suite(.serialized)
@MainActor
struct HTTPClientTests {

    // MARK: - Factory

    @MainActor
    private func makeSUT(
        tokenManager: MockHTTPClientTokenManager = MockHTTPClientTokenManager(),
        requestExecutor: (@Sendable (URLRequest) async throws -> (Data, URLResponse))? = nil,
        connectivityProvider: (() -> Bool)? = { true },
        activeAccount: Account? = nil
    ) -> (sut: HTTPClient, tokenManager: MockHTTPClientTokenManager, accountService: MockAccountService, notificationService: MockNotificationHelperService, logger: MockLoggerService) {
        TestDependencyContainer.reset()

        let accountService = MockAccountService()
        if let activeAccount {
            accountService.seedAccounts([activeAccount], active: activeAccount)
        }
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)

        let notificationService = MockNotificationHelperService()
        DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)

        let logger = MockLoggerService()
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let sut = HTTPClient(
            tokenManager: tokenManager,
            requestExecutor: requestExecutor,
            connectivityProvider: connectivityProvider
        )

        return (sut, tokenManager, accountService, notificationService, logger)
    }

    // MARK: - Successful Response Handling

    @Test("GET request decodes JSON response successfully")
    func getDecodesJSONResponse() async throws {
        let expected = TestResponse(id: 1, name: "Test")
        let jsonData = try JSONEncoder().encode(expected)

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let result: TestResponse = try await sut.get(.accountInfo)
        #expect(result == expected)
    }

    @Test("POST request encodes body and decodes response")
    func postEncodesBodyAndDecodesResponse() async throws {
        let body = TestRequestBody(value: "hello")
        let expected = TestResponse(id: 2, name: "Created")
        let responseData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (responseData, makeHTTPResponse(statusCode: 201))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let result: TestResponse = try await sut.send(.submitOperation, method: .post, body: body)
        #expect(result == expected)
        #expect(capturedRequest?.httpMethod == "POST")
        #expect(capturedRequest?.value(forHTTPHeaderField: "Content-Type") == "application/json")

        // Verify body was encoded
        if let bodyData = capturedRequest?.httpBody {
            let decoded = try JSONDecoder().decode(TestRequestBody.self, from: bodyData)
            #expect(decoded == body)
        }
    }

    @Test("204 No Content returns EmptyResponse")
    func noContentReturnsEmptyResponse() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 204))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: EmptyResponse = try await sut.get(.accountInfo)
        // If we reach here without throwing, the test passes
    }

    @Test("Empty data with 200 returns EmptyResponse")
    func emptyDataReturnsEmptyResponse() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: EmptyResponse = try await sut.get(.accountInfo)
    }

    @Test("204 No Content throws decodingError when non-EmptyResponse expected")
    func noContentThrowsDecodingErrorForNonEmpty() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 204))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        await #expect(throws: HTTPError.self) {
            let _: TestResponse = try await sut.get(.accountInfo)
        }
    }

    @Test("String response decodes as plain text")
    func stringResponseDecodesAsPlainText() async throws {
        let text = "Hello, World!"
        let data = text.data(using: .utf8)!

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (data, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let result: String = try await sut.get(.accountInfo)
        #expect(result == text)
    }

    // MARK: - Error Response Handling

    @Test("400 Bad Request maps to badRequest error")
    func badRequestMapsToError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 400))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.badRequest")
        } catch let error as HTTPError {
            if case .badRequest = error {
                // Expected
            } else {
                Issue.record("Expected badRequest, got \(error)")
            }
        }
    }

    @Test("401 Unauthorized maps to unauthorized error when not authenticated")
    func unauthorizedMapsToError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 401))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.unauthorized")
        } catch let error as HTTPError {
            if case .unauthorized = error {
                // Expected
            } else {
                Issue.record("Expected unauthorized, got \(error)")
            }
        }
    }

    @Test("403 Forbidden maps to forbidden error")
    func forbiddenMapsToError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 403))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.forbidden")
        } catch let error as HTTPError {
            if case .forbidden = error {
                // Expected
            } else {
                Issue.record("Expected forbidden, got \(error)")
            }
        }
    }

    @Test("404 Not Found maps to notFound error")
    func notFoundMapsToError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 404))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.notFound")
        } catch let error as HTTPError {
            if case .notFound = error {
                // Expected
            } else {
                Issue.record("Expected notFound, got \(error)")
            }
        }
    }

    @Test("500 Internal Server Error maps to serverError")
    func serverErrorMapsToError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 500))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.serverError")
        } catch let error as HTTPError {
            if case .serverError = error {
                // Expected
            } else {
                Issue.record("Expected serverError, got \(error)")
            }
        }
    }

    @Test("API error with JSON message returns apiError with server message")
    func apiErrorWithJsonMessage() async throws {
        let errorJson = """
        {"error": "Custom server error message"}
        """.data(using: .utf8)!

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (errorJson, makeHTTPResponse(statusCode: 400))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.apiError")
        } catch let error as HTTPError {
            if case .apiError(let message, let code) = error {
                #expect(message == "Custom server error message")
                #expect(code == 400)
            } else {
                Issue.record("Expected apiError, got \(error)")
            }
        }
    }

    @Test("API error with message field returns apiError")
    func apiErrorWithMessageField() async throws {
        let errorJson = """
        {"message": "Validation failed"}
        """.data(using: .utf8)!

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (errorJson, makeHTTPResponse(statusCode: 400))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.apiError")
        } catch let error as HTTPError {
            if case .apiError(let message, _) = error {
                #expect(message == "Validation failed")
            } else {
                Issue.record("Expected apiError, got \(error)")
            }
        }
    }

    @Test("Unknown status code maps to statusCode error")
    func unknownStatusCodeMapsToStatusCodeError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (Data(), makeHTTPResponse(statusCode: 418))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.statusCode")
        } catch let error as HTTPError {
            if case .statusCode(let code) = error {
                #expect(code == 418)
            } else {
                Issue.record("Expected statusCode, got \(error)")
            }
        }
    }

    @Test("Non-HTTP response throws invalidResponse")
    func nonHTTPResponseThrowsInvalidResponse() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            let response = URLResponse(
                url: URL(string: "https://example.com")!,
                mimeType: nil,
                expectedContentLength: 0,
                textEncodingName: nil
            )
            return (Data(), response)
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        await #expect(throws: HTTPError.self) {
            let _: TestResponse = try await sut.get(.accountInfo)
        }
    }

    @Test("Invalid JSON throws decodingError")
    func invalidJsonThrowsDecodingError() async throws {
        let invalidJson = "not valid json".data(using: .utf8)!

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (invalidJson, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.decodingError")
        } catch let error as HTTPError {
            if case .decodingError = error {
                // Expected
            } else {
                Issue.record("Expected decodingError, got \(error)")
            }
        }
    }

    // MARK: - Timeout / Offline Errors

    @Test("URLError.timedOut throws HTTPError.timeout")
    func urlErrorTimedOutThrowsTimeout() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            throw URLError(.timedOut)
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.timeout")
        } catch let error as HTTPError {
            if case .timeout = error {
                // Expected
            } else {
                Issue.record("Expected timeout, got \(error)")
            }
        }
    }

    @Test("URLError other than timeout throws HTTPError.noInternet")
    func urlErrorOtherThrowsNoInternet() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            throw URLError(.notConnectedToInternet)
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.noInternet")
        } catch let error as HTTPError {
            if case .noInternet = error {
                // Expected
            } else {
                Issue.record("Expected noInternet, got \(error)")
            }
        }
    }

    @Test("Connectivity check throws noInternet when offline")
    func connectivityCheckThrowsNoInternetWhenOffline() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            Issue.record("Request executor should not be called when offline")
            return (Data(), makeHTTPResponse())
        }

        let (sut, _, _, notificationService, _) = makeSUT(
            requestExecutor: executor,
            connectivityProvider: { false }
        )

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.noInternet")
        } catch let error as HTTPError {
            if case .noInternet = error {
                #expect(notificationService.showToastCalls == 1)
            } else {
                Issue.record("Expected noInternet, got \(error)")
            }
        }
    }

    @Test("Connectivity check skips toast when skipCheckNetwork is true")
    func connectivityCheckSkipsToastWhenSkipCheckNetworkTrue() async throws {
        let (sut, _, _, notificationService, _) = makeSUT(
            connectivityProvider: { false }
        )
        sut.skipCheckNetwork = true

        do {
            let _: TestResponse = try await sut.get(.accountInfo)
            Issue.record("Expected HTTPError.noInternet")
        } catch let error as HTTPError {
            if case .noInternet = error {
                #expect(notificationService.showToastCalls == 0)
            } else {
                Issue.record("Expected noInternet, got \(error)")
            }
        }
    }

    @Test("Toast throttling prevents duplicate toasts within 2 seconds")
    func toastThrottlingPreventsDuplicates() async throws {
        let (sut, _, _, notificationService, _) = makeSUT(
            connectivityProvider: { false }
        )

        // First call
        do {
            let _: TestResponse = try await sut.get(.accountInfo)
        } catch {}

        // Second call immediately after
        do {
            let _: TestResponse = try await sut.get(.accountInfo)
        } catch {}

        // Only one toast should have been shown due to throttling
        #expect(notificationService.showToastCalls == 1)
    }

    // MARK: - Authentication & Token Handling

    @Test("GET with needsAuth adds Authorization header from active account")
    func getWithAuthAddsAuthorizationHeader() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "100", isActive: true)
        account.accessToken = "my-access-token"
        let expected = TestResponse(id: 1, name: "Auth Test")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
        #expect(result == expected)
        #expect(capturedRequest?.value(forHTTPHeaderField: "Authorization") == "Bearer my-access-token")
    }

    @Test("GET with needsAuth and no active account throws noActiveAccount")
    func getWithAuthNoAccountThrowsError() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            Issue.record("Should not reach request executor")
            return (Data(), makeHTTPResponse())
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        await #expect(throws: AccountError.self) {
            let _: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
        }
    }

    @Test("GET with needsAuth and expired token refreshes token before request")
    func getWithExpiredTokenRefreshesBeforeRequest() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "200", isActive: true)
        account.accessToken = "old-token"
        account.expiresAt = "2020-01-01T00:00:00Z"

        let expected = TestResponse(id: 3, name: "Refreshed")
        let jsonData = try JSONEncoder().encode(expected)

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = true
        mockTokenManager.refreshTokenResult = .success(
            Tokens(accessToken: "new-token", refreshToken: "new-refresh", expiresAt: "2099-01-01T00:00:00Z")
        )

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
        #expect(result == expected)
        #expect(mockTokenManager.refreshTokenCalls == 1)
        #expect(capturedRequest?.value(forHTTPHeaderField: "Authorization") == "Bearer new-token")
    }

    @Test("401 response with needsAuth retries once with refreshed token")
    func unauthorizedRetryWithRefreshedToken() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "300", isActive: true)
        account.accessToken = "valid-token"

        let expected = TestResponse(id: 4, name: "RetrySuccess")
        let jsonData = try JSONEncoder().encode(expected)

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = false
        mockTokenManager.refreshTokenResult = .success(
            Tokens(accessToken: "retry-token", refreshToken: "retry-refresh", expiresAt: "2099-01-01T00:00:00Z")
        )

        var callCount = 0
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            callCount += 1
            if callCount == 1 {
                return (Data(), makeHTTPResponse(statusCode: 401))
            }
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
        #expect(result == expected)
        #expect(mockTokenManager.refreshTokenCalls == 1)
        #expect(callCount == 2)
    }

    @Test("401 response without needsAuth does not retry")
    func unauthorizedWithoutAuthDoesNotRetry() async throws {
        var callCount = 0
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            callCount += 1
            return (Data(), makeHTTPResponse(statusCode: 401))
        }

        let (sut, tokenManager, _, _, _) = makeSUT(requestExecutor: executor)

        do {
            let _: TestResponse = try await sut.get(.accountInfo, needsAuth: false)
            Issue.record("Expected error")
        } catch {
            #expect(callCount == 1)
            #expect(tokenManager.refreshTokenCalls == 0)
        }
    }

    @Test("401 on retry does not retry infinitely")
    func unauthorizedOnRetryDoesNotLoop() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "400", isActive: true)
        account.accessToken = "valid-token"

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = false
        mockTokenManager.refreshTokenResult = .success(
            Tokens(accessToken: "retry-token", refreshToken: "retry-refresh", expiresAt: "2099-01-01T00:00:00Z")
        )

        var callCount = 0
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            callCount += 1
            return (Data(), makeHTTPResponse(statusCode: 401))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        do {
            let _: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
            Issue.record("Expected HTTPError.unauthorized")
        } catch let error as HTTPError {
            if case .unauthorized = error {
                // Only 1 refresh (first 401), then 2nd 401 is thrown
                #expect(mockTokenManager.refreshTokenCalls == 1)
                #expect(callCount == 2)
            } else {
                Issue.record("Expected unauthorized, got \(error)")
            }
        }
    }

    @Test("Login endpoint skips token check even with needsAuth")
    func loginEndpointSkipsTokenCheck() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "500", isActive: true)
        account.accessToken = "some-token"

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = true // Would trigger refresh normally

        let body = TestRequestBody(value: "login")
        let expected = TestResponse(id: 5, name: "LoggedIn")
        let jsonData = try JSONEncoder().encode(expected)

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.send(.login, method: .post, body: body, needsAuth: true)
        #expect(result == expected)
        #expect(mockTokenManager.refreshTokenCalls == 0)
    }

    @Test("Refresh token endpoint skips token check even with needsAuth")
    func refreshTokenEndpointSkipsTokenCheck() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "600", isActive: true)
        account.accessToken = "some-token"

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = true

        let body = TestRequestBody(value: "refresh")
        let expected = TestResponse(id: 6, name: "Refreshed")
        let jsonData = try JSONEncoder().encode(expected)

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.send(.refreshToken, method: .post, body: body, needsAuth: true)
        #expect(result == expected)
        #expect(mockTokenManager.refreshTokenCalls == 0)
    }

    @Test("Logout endpoint skips token check even with needsAuth")
    func logoutEndpointSkipsTokenCheck() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "700", isActive: true)
        account.accessToken = "some-token"

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = true

        let body = TestRequestBody(value: "logout")
        let expected = TestResponse(id: 7, name: "LoggedOut")
        let jsonData = try JSONEncoder().encode(expected)

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        let result: TestResponse = try await sut.send(.logout, method: .post, body: body, needsAuth: true)
        #expect(result == expected)
        #expect(mockTokenManager.refreshTokenCalls == 0)
    }

    @Test("Token refresh failure propagates error")
    func tokenRefreshFailurePropagatesError() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "800", isActive: true)
        account.accessToken = "old-token"

        let mockTokenManager = MockHTTPClientTokenManager()
        mockTokenManager.checkTokenExpirationResult = true
        mockTokenManager.refreshTokenResult = .failure(HTTPError.unauthorized)

        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            Issue.record("Should not reach request executor")
            return (Data(), makeHTTPResponse())
        }

        let (sut, _, _, _, _) = makeSUT(
            tokenManager: mockTokenManager,
            requestExecutor: executor,
            activeAccount: account
        )

        do {
            let _: TestResponse = try await sut.get(.accountInfo, needsAuth: true)
            Issue.record("Expected error from token refresh")
        } catch let error as HTTPError {
            if case .unauthorized = error {
                #expect(mockTokenManager.refreshTokenCalls == 1)
            } else {
                Issue.record("Expected unauthorized, got \(error)")
            }
        }
    }

    @Test("Fetch account by specific accountId works")
    func fetchAccountBySpecificId() async throws {
        let account = AccountTestFixtures.makeAccountModel(id: "specific-123", isActive: true)
        account.accessToken = "specific-token"
        let expected = TestResponse(id: 9, name: "Specific")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, accountService, _, _) = makeSUT(requestExecutor: executor)
        accountService.seedAccounts([account], active: account)

        let result: TestResponse = try await sut.get(.accountInfo, needsAuth: true, accountId: "specific-123")
        #expect(result == expected)
        #expect(capturedRequest?.value(forHTTPHeaderField: "Authorization") == "Bearer specific-token")
    }

    @Test("Fetch account by nonexistent accountId throws accountNotFound")
    func fetchAccountByNonexistentIdThrows() async throws {
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            Issue.record("Should not reach request executor")
            return (Data(), makeHTTPResponse())
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        await #expect(throws: AccountError.self) {
            let _: TestResponse = try await sut.get(.accountInfo, needsAuth: true, accountId: "nonexistent")
        }
    }

    // MARK: - Request Construction

    @Test("GET request sets correct HTTP method")
    func getRequestSetsCorrectMethod() async throws {
        let expected = TestResponse(id: 10, name: "Method")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: TestResponse = try await sut.get(.accountInfo)
        #expect(capturedRequest?.httpMethod == "GET")
    }

    @Test("Custom headers are forwarded in request")
    func customHeadersForwarded() async throws {
        let expected = TestResponse(id: 11, name: "Headers")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: TestResponse = try await sut.get(
            .accountInfo,
            headers: ["X-Custom": "value123"]
        )
        #expect(capturedRequest?.value(forHTTPHeaderField: "X-Custom") == "value123")
    }

    @Test("PUT request sends correct method and body")
    func putRequestSendsCorrectMethodAndBody() async throws {
        let body = TestRequestBody(value: "updated")
        let expected = TestResponse(id: 12, name: "Updated")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let result: TestResponse = try await sut.send(.updateAccount, method: .put, body: body)
        #expect(result == expected)
        #expect(capturedRequest?.httpMethod == "PUT")
    }

    @Test("DELETE request sends correct method")
    func deleteRequestSendsCorrectMethod() async throws {
        let body = EmptyBody()
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            #expect(request.httpMethod == "DELETE")
            return (Data(), makeHTTPResponse(statusCode: 204))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: EmptyResponse = try await sut.send(.deleteAccount, method: .delete, body: body)
    }

    @Test("PATCH request sends correct method")
    func patchRequestSendsCorrectMethod() async throws {
        let body = TestRequestBody(value: "patched")
        let expected = TestResponse(id: 13, name: "Patched")
        let jsonData = try JSONEncoder().encode(expected)

        var capturedRequest: URLRequest?
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            capturedRequest = request
            return (jsonData, makeHTTPResponse(statusCode: 200))
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        let _: TestResponse = try await sut.send(.updateProfile, method: .patch, body: body)
        #expect(capturedRequest?.httpMethod == "PATCH")
    }

    // MARK: - Non-HTTPError Passthrough

    @Test("Non-URLError exceptions are rethrown as-is")
    func nonURLErrorExceptionsRethrown() async throws {
        struct CustomError: Error {}
        let executor: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
            throw CustomError()
        }

        let (sut, _, _, _, _) = makeSUT(requestExecutor: executor)

        await #expect(throws: CustomError.self) {
            let _: TestResponse = try await sut.get(.accountInfo)
        }
    }

    // MARK: - Send with Body Connectivity

    @Test("Send with body checks connectivity before executing")
    func sendWithBodyChecksConnectivity() async throws {
        let (sut, _, _, notificationService, _) = makeSUT(
            connectivityProvider: { false }
        )

        do {
            let body = TestRequestBody(value: "test")
            let _: TestResponse = try await sut.send(.submitOperation, method: .post, body: body)
            Issue.record("Expected HTTPError.noInternet")
        } catch let error as HTTPError {
            if case .noInternet = error {
                #expect(notificationService.showToastCalls == 1)
            } else {
                Issue.record("Expected noInternet, got \(error)")
            }
        }
    }
}
