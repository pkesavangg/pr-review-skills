import Foundation
@testable import meApp

final class MockHTTPClientTokenManager: TokenManaging {
    var checkTokenExpirationResult = false
    var refreshTokenResult: Result<Tokens, Error> = .failure(UnexpectedCallError.methodCalled("refreshToken"))

    private(set) var checkTokenExpirationCalls = 0
    private(set) var refreshTokenCalls = 0
    private(set) var lastCheckExpiresAt: String?
    private(set) var lastRefreshAccountId: String?
    private(set) var lastRefreshRetryCount: Int?

    func checkTokenExpiration(expiresAt: String?) -> Bool {
        checkTokenExpirationCalls += 1
        lastCheckExpiresAt = expiresAt
        return checkTokenExpirationResult
    }

    func refreshToken(accountId: String?, retryCount: Int) async throws -> Tokens {
        refreshTokenCalls += 1
        lastRefreshAccountId = accountId
        lastRefreshRetryCount = retryCount
        return try refreshTokenResult.get()
    }
}
