import Foundation
@testable import meApp

/// A mock `TokenManaging` implementation for HTTPClient unit tests.
/// Allows configuring token-expiration checks and refresh results.
final class MockHTTPClientTokenManager: TokenManaging, @unchecked Sendable {
    // MARK: - Configurable Results
    var checkTokenExpirationResult: Bool = false
    var refreshTokenResult: Result<Tokens, Error> = .success(
        Tokens(accessToken: "refreshed-access", refreshToken: "refreshed-refresh", expiresAt: "2099-01-01T00:00:00Z")
    )

    // MARK: - Call Tracking
    private(set) var checkTokenExpirationCalls = 0
    private(set) var refreshTokenCalls = 0
    private(set) var lastRefreshAccountId: String?

    // MARK: - TokenManaging

    nonisolated func checkTokenExpiration(expiresAt: String?) -> Bool {
        // Mutation of actor-isolated state is unsafe from nonisolated context,
        // but for unit-test mocks running serially on MainActor this is acceptable.
        let mutableSelf = unsafeBitCast(self, to: MockHTTPClientTokenManager.self)
        mutableSelf.checkTokenExpirationCalls += 1
        return mutableSelf.checkTokenExpirationResult
    }

    func refreshToken(accountId: String?, retryCount: Int) async throws -> Tokens {
        refreshTokenCalls += 1
        lastRefreshAccountId = accountId
        return try refreshTokenResult.get()
    }
}
