import Foundation

@MainActor
final class TokenManager {
    static let shared = TokenManager()
    private var isRefreshing = false
    private var refreshContinuations: [CheckedContinuation<Void, Error>] = []
    private let maxRetries = 3
    
    func checkTokenExpiration(expiresAt: String?) -> Bool {
        guard let expiresAt = expiresAt,
              let expirationDate = DateTimeTools.parse(expiresAt) else {
            return true
        }
        return Date().addingTimeInterval(5 * 60) >= expirationDate
    }
    
    func refreshToken(customToken: String? = nil, retryCount: Int = 0) async throws -> Tokens {
        if isRefreshing {
            try await waitForRefresh()
            return try await AccountService.shared.getActiveTokens()
        }
        
        if retryCount >= maxRetries {
            try await AccountService.shared.logOut(accountId: nil)
            throw NetworkError.statusCode(401)
        }
        
        isRefreshing = true
        defer { isRefreshing = false }
        
        do {
            let tokens = try await AccountService.shared.refreshTokens(customToken: customToken)
            try await AccountService.shared.updateTokens(tokens)
            resumeWaitingRequests()
            return tokens
        } catch {
            resumeWaitingRequests(with: error)
            
            if let networkError = error as? NetworkError {
                switch networkError {
                case .statusCode(let code):
                    if code == 401 {
                        try await AccountService.shared.logOut(accountId: nil)
                        throw error
                    } else if code >= 501 || code == 0 {
                        return try await refreshToken(customToken: customToken, retryCount: retryCount + 1)
                    }
                case .noInternet:
                    return try await refreshToken(customToken: customToken, retryCount: retryCount + 1)
                default:
                    break
                }
            }
            
            try await AccountService.shared.logOut(accountId: nil)
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
