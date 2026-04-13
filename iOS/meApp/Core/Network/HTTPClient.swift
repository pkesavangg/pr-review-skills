//
//  HTTPClient.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

final class HTTPClient {
    static let shared = HTTPClient()
    @Injector var accountService: AccountService
    @Injector var notificationHelperService: NotificationHelperService
    @Atomic public var skipCheckNetwork: Bool = false
    private let tokenManager = TokenManager.shared
    @Atomic private var lastToastShownTime: Date?

    private init() {}
    
    // MARK: - GET Request
    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> T {
        try await checkConnectivity()
        
        let request = try await makeRequest(
            for: endpoint,
            method: .get,
            headers: headers,
            needsAuth: needsAuth,
            accountId: accountId
        )
        return try await send(request: request, needsAuth: needsAuth, accountId: accountId)
    }
    
    // MARK: - POST/PUT/PATCH/DELETE with Body
    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> R {
        try await checkConnectivity()
        
        var request = try await makeRequest(
            for: endpoint,
            method: method,
            headers: headers,
            needsAuth: needsAuth,
            accountId: accountId
        )
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return try await send(request: request, needsAuth: needsAuth, accountId: accountId)
    }
    
    // MARK: - Core Send Logic
    private func send<T: Decodable>(
        request: URLRequest,
        needsAuth: Bool,
        accountId: String?,
        restartWithNewTokens: Bool = false
    ) async throws -> T {
        // Skip token check for logout and refresh token endpoints
        let skipTokenCheck = request.url?.path.contains("/refresh-token") == true ||
        request.url?.path.contains("/logout") == true || request.url?.path.contains("/login") == true
        
        // Only check token expiration if needed and not skipped
        if needsAuth && !skipTokenCheck {
            // getAccountSnapshot is @MainActor — hops there, reads model primitives, returns
            // a plain Sendable struct. No @Model object crosses the actor boundary.
            let snapshot = try await getAccountSnapshot(accountId)
            if tokenManager.checkTokenExpiration(expiresAt: snapshot.expiresAt) {
                let tokens = try await tokenManager.refreshToken(accountId: snapshot.accountId)
                var newRequest = request
                newRequest.setValue("Bearer \(tokens.accessToken)", forHTTPHeaderField: "Authorization")
                return try await performRequest(newRequest)
            }
        }

        do {
           return try await performRequest(request)
        } catch {
            if let networkError = error as? HTTPError {
                switch networkError {
                case HTTPError.unauthorized:
                    // Only retry with refreshed token if we haven't already retried
                    // This prevents infinite loops when 401 is legitimate (e.g., wrong password)
                    if needsAuth && !skipTokenCheck && !restartWithNewTokens {
                        let snapshot = try await getAccountSnapshot(accountId)
                        let tokens = try await tokenManager.refreshToken(accountId: snapshot.accountId)
                        var newRequest = request
                        newRequest.setValue("Bearer \(tokens.accessToken)", forHTTPHeaderField: "Authorization")
                        return try await send(request: newRequest, needsAuth: needsAuth, accountId: accountId, restartWithNewTokens: true)
                    }
                default:
                    break
                }
            }
            throw error
        }
    }
    
    // MARK: - Request Execution
    /// Performs the actual network request and handles response decoding.
    private func performRequest<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch let urlError as URLError {
            // Convert URLErrors to HTTPErrors so callers can detect network errors reliably.
            switch urlError.code {
            case .timedOut:
                throw HTTPError.timeout
            default:
                throw HTTPError.noInternet
            }
        } catch {
            throw error
        }
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw HTTPError.invalidResponse
        }
        
        // Map raw status code to enum
        guard let status = HTTPStatusCode(rawValue: httpResponse.statusCode) else {
            throw HTTPError.statusCode(httpResponse.statusCode)
        }
        
        // Check for success status
        guard status.isSuccess else {
            // Attempt to decode server-provided error message for more context
            if let apiError = try? JSONDecoder().decode(ErrorResponse.self, from: data),
               let serverMessage = apiError.error ?? apiError.message, !serverMessage.isEmpty {
                throw HTTPError.apiError(message: serverMessage, code: status.rawValue)
            }
            if let status = HTTPStatusCode(rawValue: httpResponse.statusCode) {
                throw HTTPError.from(status: status)
            }
            throw HTTPError.statusCode(status.rawValue)
        }
        
        // Handle 204 No Content
        if status == .noContent || data.isEmpty {
            if let emptyResponse = EmptyResponse() as? T {
                return emptyResponse
            } else {
                throw HTTPError.decodingError
            }
        }
        
        // Handle plain text response for String.self
        if T.self == String.self, let string = String(data: data, encoding: .utf8) as? T {
            return string
        }
        
        // Attempt to decode response
        do {
            // 🔹 Print raw JSON or text response for debugging
            if let rawString = String(data: data, encoding: .utf8) {
#if DEBUG
                print("🔍 HTTPClient Raw Response: \(rawString)")
#endif
            } else {
#if DEBUG
                print("⚠️ HTTPClient Unable to decode data to string")
#endif
            }
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
#if DEBUG
            print("🔍 HTTPClient Decoding Error: \(error)")
#endif
            throw HTTPError.decodingError
        }
    }
    
    // MARK: - Account Handling

    /// Plain value type carrying the auth primitives needed by HTTPClient.
    /// Returned instead of the raw @Model object so callers never touch a
    /// SwiftData model outside its owning @MainActor ModelContext.
    private struct AccountSnapshot {
        let accountId: String
        let accessToken: String?
        let expiresAt: String?
    }

    /// Fetches account auth primitives on the main actor.
    /// Marked @MainActor so that all @Model property reads happen on the
    /// thread that owns PersistenceController.shared.context.
    @MainActor
    private func getAccountSnapshot(_ accountId: String?) async throws -> AccountSnapshot {
        let account: Account
        if let accountId = accountId {
            guard let acct = try await accountService.fetchAccount(byId: accountId) else {
                throw AccountError.accountNotFound(id: accountId)
            }
            account = acct
        } else {
            guard let acct = accountService.activeAccount else {
                throw AccountError.noActiveAccount
            }
            account = acct
        }
        // Extract primitives here while still on @MainActor — never let the
        // @Model object escape to a background thread.
        return AccountSnapshot(
            accountId: account.accountId,
            accessToken: account.accessToken,
            expiresAt: account.expiresAt
        )
    }
    
    // MARK: - Request Constructor
    private func makeRequest(
        for endpoint: Endpoint,
        method: HTTPMethod,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> URLRequest {
        guard var request = endpoint.urlRequest else {
            throw HTTPError.badRequest
        }
        
        request.httpMethod = method.rawValue
        
        var allHeaders = headers ?? [:]
        if needsAuth {
            if let snapshot = try? await getAccountSnapshot(accountId) {
                if let token = snapshot.accessToken, !token.isEmpty {
                    allHeaders["Authorization"] = "Bearer \(token)"
                }
            }
        }
        
        allHeaders.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        
        return request
    }
    
    // MARK: - Connectivity Check
    private func checkConnectivity() async throws {
        let isConnected = await NetworkMonitor.shared.getCurrentConnectionStatus()
        if !isConnected {
            if !skipCheckNetwork {
                await showToastIfNeeded(ToastStrings.unableToConnect)
            }
            throw HTTPError.noInternet
        }
    }
    
    private func showToastIfNeeded(_ message: String) async {
        let now = Date()
        guard lastToastShownTime == nil || now.timeIntervalSince(lastToastShownTime!) >= 2.0 else { return }
        lastToastShownTime = now
        await notificationHelperService.showToast(ToastModel(message: message))
    }
    
}

// MARK: - USAGE GUIDE
//
// 🔹 GET request:
// let result: YourDecodableType = try await HTTPClient.shared.get(
//     .yourEndpoint,
//     needsAuth: true
// )
//
// 🔹 POST/PUT/PATCH/DELETE with body:
// let body = YourEncodableRequest(...)
// let result: YourDecodableType = try await HTTPClient.shared.send(
//     .yourEndpoint,
//     method: .post,
//     body: body,
//     needsAuth: true
// )
//
// 🔐 Auth Handling:
// - Automatically adds Bearer token if `needsAuth` is true.
// - Refreshes expired tokens and retries the request once.
// - Prevents infinite retry loops by tracking `restartWithNewTokens` flag.
// - Skips token check for login/logout/refresh-token endpoints.
//
// 🔁 Retryable Errors:
// - Automatically retries for `.networkError`, `502`, and `503`.
// - Customize in `HTTPStatusCode.isRetryable`.
//
// ⚠️ Error Handling:
// Use `do-catch` to handle `NetworkError`, e.g.:
// ```swift
// do {
//     let response = try await HTTPClient.shared.get(.endpoint, needsAuth: true)
// } catch {
//     print("Error: \(error)")
// }
// ```
//
// ✅ Notes:
// - Request/response models must conform to `Codable`.
// - 204 responses support `EmptyResponse` type.
