import Foundation

// MARK: - Refresh Token Flow
//
// When an API request is made with `needsAuth = true`, the token expiration is first checked.
// If the token is expired or about to expire, `TokenManager.refreshToken` is triggered.
// - If another refresh is already in progress, other requests will wait via `waitForRefresh()`.
// - If the refresh fails due to a retryable error (e.g. network issue, 502, 503), it retries.
// - If the refresh ultimately fails or returns 401 (unauthorized), the user is logged out.
// - Successful refresh updates the stored tokens and resumes all waiting requests.

actor TokenManager {
    static let shared = TokenManager()

    // Manual dependency resolution replacing @Injector (incompatible with actors due to mutating getter)
    private var _accountService: AccountServiceProtocol?
    private var accountService: AccountServiceProtocol {
        if _accountService == nil {
            _accountService = DependencyContainer.shared.resolve(AccountServiceProtocol.self)
        }
        guard let accountService = _accountService else {
            fatalError("AccountService dependency is not registered")
        }
        return accountService
    }

    private var isRefreshing = false
    private var refreshContinuations: [CheckedContinuation<Void, Error>] = []

    init(accountService: AccountServiceProtocol? = nil) {
        self._accountService = accountService
    }

    /// Checks if the token is expired based on the expiration date.
    /// This is nonisolated because it accesses no actor state — only its parameters and static constants.
    nonisolated func checkTokenExpiration(expiresAt: String?) -> Bool {
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
            try await accountService.logOut(accountId: accountId, isAutoLogout: true)
            throw HTTPError.statusCode(HTTPStatusCode.unauthorized.rawValue)
        }

        isRefreshing = true

        do {
            let tokens = try await accountService.refreshTokens(accountId: accountId)
            try await accountService.updateTokens(tokens, accountId)
            isRefreshing = false
            resumeWaitingRequests()
            return tokens
        } catch {
            isRefreshing = false
            resumeWaitingRequests(with: error)
            if let networkError = error as? HTTPError {
                switch networkError {
                case HTTPError.unauthorized:
                    try await accountService.logOut(accountId: accountId, isAutoLogout: true)
                    throw error
                case .statusCode(let code):
                    if let status = HTTPStatusCode(rawValue: code), status.isRetryable {
                        return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                    }
                case .noInternet:
                    return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                default:
                    break
                }
            }
            // If we reach here, it means we couldn't refresh the token
            try await accountService.logOut(accountId: accountId, isAutoLogout: true)
            throw error
        }
    }

    /// Parks the caller until the in-progress token refresh completes.
    private func waitForRefresh() async throws {
        try await withCheckedThrowingContinuation { continuation in
            refreshContinuations.append(continuation)
        }
    }

    /// Resumes all waiting requests with the provided error or success.
    private func resumeWaitingRequests(with error: Error? = nil) {
        let continuations = refreshContinuations
        refreshContinuations.removeAll()
        for continuation in continuations {
            if let error = error {
                continuation.resume(throwing: error)
            } else {
                continuation.resume()
            }
        }
    }
}
