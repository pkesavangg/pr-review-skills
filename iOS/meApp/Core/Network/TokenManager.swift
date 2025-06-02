import Foundation

@MainActor
final class TokenManager {
    static let shared = TokenManager()
    private var isRefreshing = false
    private var refreshContinuations: [CheckedContinuation<Void, Error>] = []
    
    func checkTokenExpiration(expiresAt: String?) -> Bool {
        guard let expiresAt = expiresAt,
              let expirationDate = DateTimeTools.parse(expiresAt) else {
            return true
        }
        return Date().addingTimeInterval(5 * 60) >= expirationDate
    }
    
    func refreshToken(customToken: String? = nil) async throws -> Tokens {
        if isRefreshing {
            try await waitForRefresh()
            return try await AccountService.shared.getActiveTokens()
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
