//
//  HTTPClient.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

// Isolation note (MOB-1433): this client stays @MainActor. Its heavy cost on a
// large sync — decoding the full-history JSON — is moved off the main actor via
// `decodeOffMain`. Fully de-isolating the network stack was deliberately NOT
// done here: `@Injector` resolves from the non-thread-safe `DependencyContainer`
// singleton, which is race-free only because every consumer is main-actor
// bound; making HTTPClient (and the 10 API repositories) nonisolated would move
// that shared-dictionary access off main and require de-isolating the DI
// container too. The URLSession I/O already runs off the main thread; only its
// cheap continuation resumes on main.
@MainActor
final class HTTPClient: HTTPClientProtocol {
    static let shared = HTTPClient()
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationHelperService: NotificationHelperServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    // TEMPORARY (MOB-382/383/384/385/386/405/407): dedicated console-only logger for Phase 2 API testing.
    // Bypasses LoggerService's `.info` console gate; AppLogger writes to os.Logger only (never persisted/uploaded).
    private let apiLogger = AppLogger(tag: "API")
    @Atomic public var skipCheckNetwork: Bool = false
    private let tokenManager: TokenManaging
    private let requestExecutor: (URLRequest) async throws -> (Data, URLResponse)
    private let connectivityProvider: () -> Bool
    @Atomic private var lastToastShownTime: Date?

    init(
        tokenManager: TokenManaging? = nil,
        requestExecutor: ((URLRequest) async throws -> (Data, URLResponse))? = nil,
        connectivityProvider: (() -> Bool)? = nil
    ) {
        self.tokenManager = tokenManager ?? TokenManager.shared
        self.requestExecutor = requestExecutor ?? { request in
            #if DEBUG || LOCAL_DEV_TLS
            // TEMPORARY (Phase 2 local testing): route through a session that trusts the
            // local dev server's self-signed cert. Compiled in for DEBUG builds and any
            // config that defines LOCAL_DEV_TLS (currently Production, for testing against
            // the self-signed test server). MUST be removed before any real release.
            return try await LocalDevTrust.session.data(for: request)
            #else
            return try await URLSession.shared.data(for: request)
            #endif
        }
        self.connectivityProvider = connectivityProvider ?? {
            NetworkMonitor.shared.getCurrentConnectionStatus()
        }
    }

    // MARK: - GET Request
    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> T {
        try checkConnectivity()

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
        try checkConnectivity()

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
            let account = try await getAccount(accountId)
            // Extract primitives from @Model before crossing async boundaries (R1)
            let expiresAt = account.expiresAt
            let acctId = account.accountId
            if tokenManager.checkTokenExpiration(expiresAt: expiresAt) {
                let tokens = try await tokenManager.refreshToken(accountId: acctId, retryCount: 0)
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
                        let account = try await getAccount(accountId)
                        // Extract primitives from @Model before crossing async boundaries (R1)
                        let acctId = account.accountId
                        let tokens = try await tokenManager.refreshToken(accountId: acctId, retryCount: 0)
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
    private func performRequest<T: Decodable>(_ request: URLRequest) async throws -> T { // swiftlint:disable:this cyclomatic_complexity
        #if DEBUG
        debugPrintRequest(request)
        #endif
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await requestExecutor(request)
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
            throw parseErrorResponse(data: data, status: status, statusCode: httpResponse.statusCode)
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

        // Attempt to decode response.
        // MOB-1433: `performRequest` is main-actor-isolated (the class is
        // @MainActor), so decoding a full-history payload here would parse
        // thousands of entries on the UI thread. Hand the CPU-bound decode to a
        // nonisolated helper so it runs off the main actor.
        do {
            logRawResponse(data: data)
            return try await Self.decodeOffMain(T.self, from: data)
        } catch {
            logger.log(level: .error, tag: "HTTPClient", message: "Decoding error", data: error)
            throw HTTPError.decodingError
        }
    }

    /// Decodes a response body off the main actor. `nonisolated` + `async`
    /// means this does not adopt the caller's main-actor isolation (SE-0338);
    /// it runs on the cooperative pool, keeping large-payload JSON parsing off
    /// the UI thread (MOB-1433). The full network stack stays @MainActor for
    /// now — see the isolation note in the class header.
    nonisolated private static func decodeOffMain<T: Decodable>(_ type: T.Type, from data: Data) async throws -> T {
        try JSONDecoder().decode(T.self, from: data)
    }

    private func parseErrorResponse(data: Data, status: HTTPStatusCode, statusCode: Int) -> HTTPError {
        if let apiError = try? JSONDecoder().decode(ErrorResponse.self, from: data),
           let serverMessage = apiError.error ?? apiError.message,
           !serverMessage.isEmpty {
            return HTTPError.apiError(message: serverMessage, code: status.rawValue)
        }

        if let mappedStatus = HTTPStatusCode(rawValue: statusCode) {
            return HTTPError.from(status: mappedStatus)
        }

        return HTTPError.statusCode(status.rawValue)
    }

    // MARK: - Phase 2 API testing instrumentation (MOB-382/383/384/385/386/405/407)
    // TEMPORARY: console prints to verify Phase 2 unified API requests/responses in Xcode.
    // Remove before merging out of the testing branch.
    // Searchable token prefixed to every API request/response log line so they can be
    // filtered out of the combined log stream (search `API_TRACE` to see all traffic,
    // `API_REQUEST` for outgoing, `API_RESPONSE` for incoming).
    private static let apiTraceToken = "API_TRACE"

    private func debugPrintRequest(_ request: URLRequest) {
        let method = request.httpMethod ?? "?"
        let url = request.url?.absoluteString ?? "?"
        var bodyString = ""
        if let body = request.httpBody, !body.isEmpty,
           let string = String(data: body, encoding: .utf8) {
            bodyString = "\n   request body: \(string)"
        }
        apiLogger.log(
            level: .info,
            tag: "API",
            message: "\(Self.apiTraceToken) API_REQUEST ➡️ \(method) \(url)\(bodyString)"
        )
    }

    private func debugPrintResponse(_ request: URLRequest, statusCode: Int, data: Data) {
        let method = request.httpMethod ?? "?"
        let url = request.url?.absoluteString ?? "?"
        let icon = (200..<300).contains(statusCode) ? "✅" : "❌"
        let bodyString = String(data: data, encoding: .utf8).flatMap { $0.isEmpty ? nil : $0 } ?? "<empty>"
        apiLogger.log(
            level: .info,
            tag: "API",
            message: "\(Self.apiTraceToken) API_RESPONSE \(icon) \(statusCode) \(method) \(url)\n   response: \(bodyString)"
        )
    }

    private func logRawResponse(data: Data) {
        #if DEBUG
        if let rawString = String(data: data, encoding: .utf8) {
            logger.log(level: .debug, tag: "HTTPClient", message: "Raw response", data: rawString)
        } else {
            logger.log(level: .debug, tag: "HTTPClient", message: "Unable to decode data to string")
        }
        #endif
    }

    // MARK: - Account Handling
    /// Retrieves the active account or a specific account by ID.
    private func getAccount(_ accountId: String?) async throws -> AccountSnapshot {
        if let accountId = accountId {
            guard let account = try await accountService.fetchAccount(byId: accountId) else {
                throw AccountError.accountNotFound(id: accountId)
            }
            return account
        } else {
            guard let account = accountService.activeAccount else {
                throw AccountError.noActiveAccount
            }
            return account
        }
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
            // Always fetch fresh authenticated data; cached responses can become stale when device time is changed.
            request.cachePolicy = .reloadIgnoringLocalCacheData
            allHeaders["Cache-Control"] = "no-cache"
            allHeaders["Pragma"] = "no-cache"

            if let account = try? await getAccount(accountId) {
                // Extract primitive from @Model before crossing async boundaries
                let token = account.accessToken
                if let token = token, !token.isEmpty {
                    allHeaders["Authorization"] = "Bearer \(token)"
                }
            }
        }

        allHeaders.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        return request
    }

    // MARK: - Connectivity Check
    private func checkConnectivity() throws {
        let isConnected = connectivityProvider()
        if !isConnected {
            if !skipCheckNetwork {
                showToastIfNeeded(ToastStrings.unableToConnect)
            }
            throw HTTPError.noInternet
        }
    }

    private func showToastIfNeeded(_ message: String) {
        let now = Date()
        if let lastToastShownTime, now.timeIntervalSince(lastToastShownTime) < 2.0 {
            return
        }
        lastToastShownTime = now
        notificationHelperService.showToast(ToastModel(message: message))
    }

}

#if DEBUG || LOCAL_DEV_TLS
// MARK: - Local Dev Self-Signed Cert Trust (TEMPORARY — Phase 2 local testing)
//
// Lets the app talk to a dev/test server whose TLS cert is self-signed.
// URLSession.shared cannot carry a delegate, so we use a dedicated session whose
// delegate accepts the server trust — but ONLY for the LAN ranges / explicit dev hosts
// listed below. Compiled in for DEBUG builds and any config defining LOCAL_DEV_TLS
// (currently Production, for testing against the self-signed test server).
// ⚠️ MUST be removed before any real release — it disables cert validation for those hosts.
private final class LocalDevTrustDelegate: NSObject, URLSessionDelegate {
    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        let host = challenge.protectionSpace.host
        // Explicit dev hosts (public IPs that won't match the LAN ranges below).
        let trustedDevHosts: Set<String> = ["49.207.187.28"]
        let isLocalHost = trustedDevHosts.contains(host)
            || host.hasPrefix("192.168.")
            || host.hasPrefix("10.")
            || host.hasPrefix("127.")
            || host.hasPrefix("172.")
            || host.hasSuffix(".local")

        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              isLocalHost,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        completionHandler(.useCredential, URLCredential(trust: serverTrust))
    }
}

private enum LocalDevTrust {
    nonisolated(unsafe) static let session = URLSession(
        configuration: .default,
        delegate: LocalDevTrustDelegate(),
        delegateQueue: nil
    )
}
#endif

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
