import Foundation

@MainActor
final class TokenManager {
    static let shared = TokenManager()
    @Injector var accountService: AccountService
    private var isRefreshing = false
    private var refreshContinuations: [CheckedContinuation<Void, Error>] = []
    
    func checkTokenExpiration(expiresAt: String?) -> Bool {
        guard let expiresAt = expiresAt,
              let expirationDate = DateTimeTools.parse(expiresAt) else {
            return true
        }
        return Date().addingTimeInterval(AppConstants.Account.tokenExpirationBuffer) >= expirationDate
    }
    
    func refreshToken(customToken: String? = nil, accountId: String? =  nil, retryCount: Int = 0) async throws -> Tokens {
        if isRefreshing {
            try await waitForRefresh()
            return try await accountService.getActiveTokens()
        }
        
        if retryCount >= AppConstants.Account.tokenRefreshMaxRetries {
            try await accountService.logOut(accountId: nil)
            throw NetworkError.statusCode(401)
        }
        
        isRefreshing = true
        defer { isRefreshing = false }
        
        do {
            let tokens = try await accountService.refreshTokens(customToken: customToken)
            try await accountService.updateTokens(tokens)
            resumeWaitingRequests()
            return tokens
        } catch {
            resumeWaitingRequests(with: error)
            
            if let networkError = error as? NetworkError {
                switch networkError {
                case .statusCode(let code):
                    if code == HTTPStatusCode.unauthorized.rawValue {
                        try await accountService.logOut(accountId: nil)
                        throw error
                    } else if code >= 501 || code == HTTPStatusCode.networkError.rawValue {
                        return try await refreshToken(customToken: customToken, retryCount: retryCount + 1)
                    }
                case .noInternet:
                    return try await refreshToken(customToken: customToken, retryCount: retryCount + 1)
                default:
                    break
                }
            }
            try await accountService.logOut(accountId: accountId)
            throw error
        }
    }
    
    private func waitForRefresh() async throws {
        try await withCheckedThrowingContinuation { continuation in
            refreshContinuations.append(continuation)
        }
    }
    
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
