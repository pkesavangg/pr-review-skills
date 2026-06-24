import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct TokenManagerTests {
    private enum TokenManagerTestError: Error, Equatable {
        case serviceFailed
    }

    // MARK: - checkTokenExpiration

    @Test("checkTokenExpiration returns false for valid future token")
    func checkTokenExpiration_validFutureToken_returnsFalse() async {
        let (sut, _) = makeSUT()
        let future = isoString(secondsFromNow: Int(AppConstants.Account.tokenExpirationBuffer + 600))

        let isExpired = await sut.checkTokenExpiration(expiresAt: future)

        #expect(isExpired == false)
    }

    @Test("checkTokenExpiration returns true for missing or invalid expiration")
    func checkTokenExpiration_missingOrInvalid_returnsTrue() async {
        let (sut, _) = makeSUT()

        let missing = await sut.checkTokenExpiration(expiresAt: nil)
        let invalid = await sut.checkTokenExpiration(expiresAt: "invalid-date")

        #expect(missing == true)
        #expect(invalid == true)
    }

    @Test("checkTokenExpiration returns true when token is within expiration buffer")
    func checkTokenExpiration_withinBuffer_returnsTrue() async {
        let (sut, _) = makeSUT()
        let nearFuture = isoString(secondsFromNow: Int(AppConstants.Account.tokenExpirationBuffer - 60))

        let isExpired = await sut.checkTokenExpiration(expiresAt: nearFuture)

        #expect(isExpired == true)
    }

    // MARK: - refreshToken

    @Test("refreshToken success refreshes and stores token")
    func refreshToken_success_updatesStoredTokens() async throws {
        let mock = MockTokenManagerAccountService()
        let refreshed = makeTokens(access: "new-access", refresh: "new-refresh")
        mock.refreshTokensResult = .success(refreshed)
        let sut = TokenManager(accountService: mock)

        let tokens = try await sut.refreshToken(accountId: "acc-1")

        #expect(tokens == refreshed)
        #expect(mock.refreshTokensCalls == 1)
        #expect(mock.updateTokensCalls == 1)
        #expect(mock.lastRefreshAccountId == "acc-1")
        #expect(mock.lastUpdateTokens == refreshed)
        #expect(mock.lastUpdateAccountId == "acc-1")
        #expect(mock.logOutCalls == 0)
    }

    @Test("refreshToken waits for in-progress refresh and returns active tokens")
    func refreshToken_whenAlreadyRefreshing_waitsAndReturnsActiveTokens() async throws {
        let mock = MockTokenManagerAccountService()
        let refreshed = makeTokens(access: "fresh-access", refresh: "fresh-refresh")
        let active = makeTokens(access: "active-access", refresh: "active-refresh")
        mock.refreshDelayNs = 200_000_000
        mock.refreshTokensResult = .success(refreshed)
        mock.getActiveTokensResult = .success(active)
        let sut = TokenManager(accountService: mock)

        let firstTask = Task { try await sut.refreshToken(accountId: "acc-1") }
        let firstStarted = await waitUntil { mock.refreshTokensCalls == 1 }
        #expect(firstStarted == true)

        let secondTask = Task { try await sut.refreshToken(accountId: "acc-1") }
        let firstResult = try await firstTask.value
        let secondResult = try await secondTask.value

        let returnedTokens = [firstResult, secondResult]
        #expect(returnedTokens.contains(refreshed))
        #expect(returnedTokens.contains(active))
        #expect(firstResult != secondResult)
        #expect(mock.refreshTokensCalls == 1)
        #expect(mock.getActiveTokensCalls == 1)
        #expect(mock.updateTokensCalls == 1)
        #expect(mock.logOutCalls == 0)
    }

    @Test("refreshToken retries on retryable status code and succeeds")
    func refreshToken_retryableStatus_retriesThenSucceeds() async throws {
        let mock = MockTokenManagerAccountService()
        let refreshed = makeTokens(access: "retry-access", refresh: "retry-refresh")
        mock.refreshTokensResultsQueue = [
            .failure(HTTPError.statusCode(HTTPStatusCode.badGateway.rawValue)),
            .success(refreshed)
        ]
        let sut = TokenManager(accountService: mock)

        let tokens = try await sut.refreshToken(accountId: "acc-1")

        #expect(tokens == refreshed)
        #expect(mock.refreshTokensCalls == 2)
        #expect(mock.updateTokensCalls == 1)
        #expect(mock.logOutCalls == 0)
    }

    @Test("refreshToken retries on no internet and succeeds")
    func refreshToken_noInternet_retriesThenSucceeds() async throws {
        let mock = MockTokenManagerAccountService()
        let refreshed = makeTokens(access: "net-access", refresh: "net-refresh")
        mock.refreshTokensResultsQueue = [
            .failure(HTTPError.noInternet),
            .success(refreshed)
        ]
        let sut = TokenManager(accountService: mock)

        let tokens = try await sut.refreshToken(accountId: "acc-1")

        #expect(tokens == refreshed)
        #expect(mock.refreshTokensCalls == 2)
        #expect(mock.updateTokensCalls == 1)
        #expect(mock.logOutCalls == 0)
    }

    @Test("refreshToken logs out and throws on unauthorized")
    func refreshToken_unauthorized_logsOutAndThrows() async {
        let mock = MockTokenManagerAccountService()
        mock.refreshTokensResult = .failure(HTTPError.unauthorized)
        let sut = TokenManager(accountService: mock)

        do {
            _ = try await sut.refreshToken(accountId: "acc-1")
            Issue.record("Expected unauthorized refresh to throw")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError, got \(error)")
                return
            }
            if case .unauthorized = httpError {
                #expect(true)
            } else {
                Issue.record("Expected .unauthorized, got \(httpError)")
            }
        }

        #expect(mock.logOutCalls == 1)
        #expect(mock.lastLogoutAccountId == "acc-1")
        #expect(mock.lastLogoutIsAutoLogout == true)
        #expect(mock.updateTokensCalls == 0)
    }

    @Test("refreshToken logs out and throws on non-retryable refresh failure")
    func refreshToken_nonRetryableError_logsOutAndThrows() async {
        let mock = MockTokenManagerAccountService()
        mock.refreshTokensResult = .failure(TokenManagerTestError.serviceFailed)
        let sut = TokenManager(accountService: mock)

        do {
            _ = try await sut.refreshToken(accountId: "acc-1")
            Issue.record("Expected non-retryable refresh to throw")
        } catch {
            #expect(error as? TokenManagerTestError == .serviceFailed)
        }

        #expect(mock.logOutCalls == 1)
        #expect(mock.updateTokensCalls == 0)
    }

    @Test("refreshToken logs out and throws unauthorized when retries are exhausted")
    func refreshToken_retryExhausted_logsOutAndThrowsUnauthorized() async {
        let mock = MockTokenManagerAccountService()
        let sut = TokenManager(accountService: mock)

        do {
            _ = try await sut.refreshToken(accountId: "acc-1", retryCount: AppConstants.Account.tokenRefreshMaxRetries)
            Issue.record("Expected retry exhaustion to throw unauthorized")
        } catch {
            guard let httpError = error as? HTTPError else {
                Issue.record("Expected HTTPError.statusCode(401), got \(error)")
                return
            }
            if case .statusCode(let code) = httpError {
                #expect(code == HTTPStatusCode.unauthorized.rawValue)
            } else {
                Issue.record("Expected .statusCode(401), got \(httpError)")
            }
        }

        #expect(mock.logOutCalls == 1)
        #expect(mock.refreshTokensCalls == 0)
    }

    @Test("refreshToken waiting request propagates error when active token read fails")
    func refreshToken_waitingRequest_activeTokenFailure_propagates() async {
        let mock = MockTokenManagerAccountService()
        let refreshed = makeTokens(access: "fresh-access", refresh: "fresh-refresh")
        mock.refreshDelayNs = 200_000_000
        mock.refreshTokensResult = .success(refreshed)
        mock.getActiveTokensResult = .failure(TokenManagerTestError.serviceFailed)
        let sut = TokenManager(accountService: mock)

        async let first = sut.refreshToken(accountId: "acc-1")
        async let second = sut.refreshToken(accountId: "acc-1")

        // Task scheduling doesn't guarantee which call enters the actor first,
        // so collect both results and assert one succeeds and one fails.
        let firstResult: Result<Tokens, Error>
        do { firstResult = .success(try await first) } catch { firstResult = .failure(error) }
        let secondResult: Result<Tokens, Error>
        do { secondResult = .success(try await second) } catch { secondResult = .failure(error) }

        let successes = [firstResult, secondResult].compactMap { try? $0.get() }
        let failures: [Error] = [firstResult, secondResult].compactMap {
            guard case .failure(let e) = $0 else { return nil }
            return e
        }

        #expect(successes == [refreshed])
        #expect(failures.count == 1)
        #expect(failures.first as? TokenManagerTestError == .serviceFailed)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: TokenManager, account: MockTokenManagerAccountService) {
        let account = MockTokenManagerAccountService()
        let sut = TokenManager(accountService: account)
        return (sut, account)
    }

    private func makeTokens(
        access: String = "access-token",
        refresh: String = "refresh-token",
        expiresAt: String? = nil
    ) -> Tokens {
        Tokens(
            accessToken: access,
            refreshToken: refresh,
            expiresAt: expiresAt ?? isoString(secondsFromNow: 3600)
        )
    }

    private func isoString(secondsFromNow: Int) -> String {
        DateTimeTools.isoFormatter(useUTC: true).string(from: Date().addingTimeInterval(TimeInterval(secondsFromNow)))
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 10_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }
}
