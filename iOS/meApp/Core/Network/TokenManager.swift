import Foundation

// MARK: - Refresh Token Flow
//
// When an API request is made with `needsAuth = true`, the token expiration is first checked.
// If the token is expired or about to expire, `TokenManager.refreshToken` is triggered.
// - If another refresh is already in progress, other requests will wait via `waitForRefresh()`.
// - If the refresh fails due to a retryable error (e.g. network issue, 502, 503), it retries.
// - If the refresh ultimately fails or returns 401 (unauthorized), the user is logged out.
// - Successful refresh updates the stored tokens and resumes all waiting requests.

@MainActor
final class TokenManager {
    static let shared = TokenManager()
    @Injector var accountService: AccountService
    private var isRefreshing = false
    private var refreshContinuations: [CheckedContinuation<Void, Error>] = []
    
    /// Checks if the token is expired based on the expiration date.
    func checkTokenExpiration(expiresAt: String?) -> Bool {
        guard let expiresAt = expiresAt,
              let expirationDate = DateTimeTools.parse(expiresAt) else {
            return true
        }
        return Date().addingTimeInterval(AppConstants.Account.tokenExpirationBuffer) >= expirationDate
    }
    
    /// Refreshes the token if it is expired or about to expire.
    func refreshToken(accountId: String? = nil, retryCount: Int = 0) async throws -> Tokens {
        if isRefreshing {
            try await waitForRefresh()
            return try await accountService.getActiveTokens()
        }
        
        if retryCount >= AppConstants.Account.tokenRefreshMaxRetries {
            try await accountService.logOut(accountId: accountId)
            throw NetworkError.statusCode(HTTPStatusCode.unauthorized.rawValue)
        }
        
        isRefreshing = true
        defer { isRefreshing = false }
        
        do {
            let tokens = try await accountService.refreshTokens(accountId: accountId)
            try await accountService.updateTokens(tokens, accountId)
            resumeWaitingRequests()
            return tokens
        } catch {
            resumeWaitingRequests(with: error)
            
            if let networkError = error as? NetworkError {
                switch networkError {
                case .statusCode(let code):
                    if code == HTTPStatusCode.unauthorized.rawValue {
                        // Unauthorized error, attempt to refresh token
                        if accountId == accountService.activeAccount?.accountId {
                            try await accountService.logOut(accountId: accountId)
                        }
                        throw error
                    } else if let status = HTTPStatusCode(rawValue: code), status.isRetryable {
                        return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                    }
                case .noInternet:
                    return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                default:
                    break
                }
            }
            // If we reach here, it means we couldn't refresh the token
            if accountId == accountService.activeAccount?.accountId {
                try await accountService.logOut(accountId: accountId)
            }
            throw error
        }
    }
    
    /// Refreshes the tokens for the currently active account.
    private func waitForRefresh() async throws {
        try await withCheckedThrowingContinuation { continuation in
            refreshContinuations.append(continuation)
        }
    }
    
    /// Resumes all waiting requests with the provided error or success.
    private func resumeWaitingRequests(with error: Error? = nil) {
        refreshContinuations.forEach { continuation in
            if let error = error {
                continuation.resume(throwing: error)
            } else {
                continuation.resume()
            }
        }
        refreshContinuations.removeAll()
    }
}
