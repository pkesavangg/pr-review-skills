//
//  HTTPClient.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

@MainActor
final class HTTPClient {
    static let shared = HTTPClient()
    // Singleton instance for shared access
    @Injector var accountService: AccountService
    private let tokenManager = TokenManager.shared
    private let maxRetries = 3
    private init() {}
    
    private var accessToken: String {
        // Retrieve the access token from the AccountService for the logged-in user
        accountService.activeAccount?.accessToken ?? ""
    }
    
    // MARK: - GET Request
    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        customToken: String? = nil
    ) async throws -> T {
        try await checkConnectivity()
        
        let request = try makeRequest(
            for: endpoint,
            method: .get,
            headers: headers,
            needsAuth: needsAuth,
            customToken: customToken
        )
        return try await send(request: request, needsAuth: needsAuth, customToken: customToken)
    }
    
    // MARK: - POST/PUT/PATCH/DELETE with Body
    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        customToken: String? = nil
    ) async throws -> R {
        try await checkConnectivity()
        
        var request = try makeRequest(
            for: endpoint,
            method: method,
            headers: headers,
            needsAuth: needsAuth,
            customToken: customToken
        )
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return try await send(request: request, needsAuth: needsAuth, customToken: customToken)
    }
    
    // MARK: - Core Send Logic
    private func send<T: Decodable>(
        request: URLRequest,
        needsAuth: Bool,
        customToken: String?,
        retryCount: Int = 0
    ) async throws -> T {
        // Skip token check for logout and refresh token endpoints
        let skipTokenCheck = request.url?.path.contains("/refresh-token") == true ||
                           request.url?.path.contains("/logout") == true
        
        // Only check token expiration if needed and not skipped
        if needsAuth && !skipTokenCheck {
            if let account = customToken != nil ? try await getAccountForToken(customToken!) : accountService.activeAccount,
               tokenManager.checkTokenExpiration(expiresAt: account.expiresAt)
            {
                let tokens = try await tokenManager.refreshToken(customToken: customToken)
                var newRequest = request
                newRequest.setValue("Bearer \(tokens.accessToken)", forHTTPHeaderField: "Authorization")
                return try await performRequest(newRequest)
            }
        }
        
        do {
            return try await performRequest(request)
        } catch {
            throw error
        }
    }
    
    private func performRequest<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        // Map raw status code to enum
        guard let status = HTTPStatusCode(rawValue: httpResponse.statusCode) else {
            throw NetworkError.statusCode(httpResponse.statusCode)
        }
        
        // Check for success status
        guard status.isSuccess else {
            throw NetworkError.statusCode(status.rawValue)
        }
        
        // Handle 204 No Content
        if status == .noContent || data.isEmpty {
            if let emptyResponse = EmptyResponse() as? T {
                return emptyResponse
            } else {
                throw NetworkError.decodingError
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
            throw NetworkError.decodingError
        }
    }
    
    private func getAccountForToken(_ token: String) async throws -> Account? {
        // Find account matching the custom token
        return try await accountService.fetchAllAccounts().first { $0.accessToken == token }
    }
    
    // MARK: - Request Constructor
    private func makeRequest(
        for endpoint: Endpoint,
        method: HTTPMethod,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        customToken: String? = nil
    ) throws -> URLRequest {
        guard var request = endpoint.urlRequest else {
            throw NetworkError.invalidRequest
        }
        
        request.httpMethod = method.rawValue
        
        var allHeaders = headers ?? [:]
        if needsAuth {
            let token = customToken ?? accountService.activeAccount?.accessToken ?? ""
            if !token.isEmpty {
                allHeaders["Authorization"] = "Bearer \(token)"
            }
        }
        
        allHeaders.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        
        return request
    }
    
    // MARK: - Connectivity Check
    private func checkConnectivity() async throws {
        if !NetworkMonitor.shared.isConnected {
            throw NetworkError.noInternet
        }
    }
}

// MARK: - USAGE GUIDE
//
// To make an API call using HTTPClient:
//
// 1. For GET (no body, possibly with auth):
//    let result: YourDecodableType = try await HTTPClient.shared.get(.yourEndpoint, needsAuth: true)
//
// 2. For POST/PUT/PATCH/DELETE with request body:
//    let requestBody = YourEncodableRequest(...)
//    let result: YourDecodableResponse = try await HTTPClient.shared.send(
//        .yourEndpoint,
//        method: .post, // or .put, .patch, .delete
//        body: requestBody,
//        needsAuth: true // default is false
//    )
//
// Notes:
// - Set `needsAuth` to true if the endpoint requires an Authorization header.
// - Ensure your model types conform to Codable.
// - Handle errors using try-catch for proper feedback.
//
// Example in a ViewModel:
// do {
//     let response: SomeResponse = try await HTTPClient.shared.get(.someEndpoint, needsAuth: true)
// } catch {
//     print("API Error: \(error.localizedDescription)")
// }

