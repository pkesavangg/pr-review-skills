---
name: api-guide
description: API call patterns reference — HTTPClientProtocol, Endpoint enum, DTO conventions, token management, and error handling. Use when adding API endpoints, creating repository methods, making network requests, or handling auth/token refresh.
---

# meApp iOS API System Guide

The API layer implements a **type-safe, thread-safe, token-managed network system** with automatic token refresh, connectivity checks, error handling, and retry logic. All API calls go through `HTTPClientProtocol`, which abstracts `URLSession`, handles authentication, and manages token lifecycle.

---

## Architecture Overview

```
Feature Store (e.g., DashboardStore)
        ↓
RepositoryProtocol (e.g., EntryRepositoryProtocol)
        ↓
RepositoryAPI (e.g., EntryRepositoryAPI)
        ├─ Concrete API implementation
        ├─ Calls httpClient.get() or httpClient.send()
        └─ Makes DTO requests/responses
        
        ↓
HTTPClientProtocol (@MainActor)
        ├─ public get<T: Decodable>(...) async throws -> T
        ├─ public send<T: Encodable, R: Decodable>(...) async throws -> R
        ├─ Handles token + connectivity checks
        ├─ Retries on 401 (refreshes token)
        ├─ Shows toast on network errors (throttled)
        └─ Parses HTTP errors
        
        ↓
TokenManager (actor)
        ├─ checkTokenExpiration(expiresAt:)
        ├─ refreshToken(accountId:, retryCount:) async throws
        ├─ Parks concurrent requests during refresh (waits via continuation)
        └─ Handles retry logic for 502/503/network errors
        
        ↓
NetworkMonitor (@MainActor singleton)
        ├─ @Published isConnected: Bool
        ├─ Monitors NWPath for connectivity changes
        └─ Provides getCurrentConnectionStatus()
        
        ↓
Endpoint enum
        ├─ case login
        ├─ case operations(startTimestamp: String?)
        ├─ var urlRequest: URLRequest? { switch ... }
        └─ Constructs URL + headers via request(path:) helper
        
        ↓
URLSession.shared.data(for: request)
```

---

## Step 1: Define Endpoint

Location: `meApp/Domain/Models/API/EndPoints.swift`

Add a case to the `Endpoint` enum:

```swift
enum Endpoint {
    case operationsR4(startTimestamp: String?)
    case submitOperation
    case pairedScale
    // ... more cases
}

extension Endpoint {
    var urlRequest: URLRequest? {
        switch self {
        case .operationsR4(let startTimestamp):
            var components = URLComponents(string: "\(API.baseURL)/operation/r4")
            if let timestamp = startTimestamp {
                components?.queryItems = [URLQueryItem(name: "start", value: timestamp)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
            
        case .submitOperation:
            return request(path: "/operation")
            
        case .pairedScale:
            return request(path: "/paired-scale")
        }
    }
    
    // Helper for simple path-based endpoints
    private func request(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        return URLRequest(url: url)
    }
}
```

**Rules:**
- Static paths (no params) → use `request(path:)` helper
- Query string params → use `URLComponents` + `queryItems`
- Path params → interpolate directly: `/paired-scale/\(id)`
- Base URL comes from `API.baseURL` (from `AppEnvironment.apiBaseURL`)

---

## Step 2: Define DTOs (Request/Response Models)

Location: Scattered across `meApp/Domain/Models/API/`

DTOs are simple `Codable` structs for request bodies and response parsing:

```swift
// Request DTO
struct BathScaleOperationDTO: Codable, Sendable {
    let timestamp: Date
    let weight: Double
    let guid: String
    let sourceId: String?
    let unitType: String
}

// Response DTO
struct BathScaleOperationListResponse: Codable, Sendable {
    let operations: [BathScaleOperationDTO]
    let count: Int
    let nextTimestamp: String?
}

// Empty response (for 204 No Content or endpoints with no return value)
struct EmptyResponse: Codable {
    init() {}
}
```

**Conventions:**
- ✅ Use `Codable` (auto JSON encode/decode)
- ✅ Use `Sendable` for async safety
- ✅ Properties use **camelCase** (matches JSON field names)
- ✅ Optional fields for nullable API responses
- ✅ No `CodingKeys` unless JSON field names differ from Swift names
- ✅ Inline anonymous structs for one-off request bodies (see Pattern 2 below)

---

## Step 3: Create RepositoryAPI Class

Location: `meApp/Data/API/`

Concrete repository that wraps `HTTPClientProtocol`:

```swift
@MainActor
final class EntryRepositoryAPI: EntryRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    // GET request (returns decoded type)
    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse {
        let response: BathScaleOperationListResponse = try await httpClient.get(
            .operationsR4(startTimestamp: startTimestamp),
            needsAuth: true
        )
        return response
    }

    // POST request (sends body, returns response)
    func syncOperation(operation: BathScaleOperationDTO) async throws {
        _ = try await httpClient.send(
            .operationsR4(startTimestamp: nil),
            method: .post,
            body: operation,
            needsAuth: true
        ) as EmptyResponse
    }

    // POST with anonymous inline request struct
    func logIn(email: String, password: String) async throws -> AccountResponse {
        struct LoginRequest: Codable {
            let email: String
            let password: String
        }
        
        let req = LoginRequest(email: email, password: password)
        return try await httpClient.send(
            .login,
            method: .post,
            body: req,
            needsAuth: false  // Login endpoint doesn't need auth
        )
    }

    // GET specific account
    func fetchAccount(accountId: String? = nil) async throws -> AccountDTO {
        return try await httpClient.get(
            .accountInfo,
            needsAuth: true,
            accountId: accountId  // Fetch specific account if provided
        )
    }
}
```

**Key patterns:**
- Inject `HTTPClientProtocol` via constructor (allows test mocks)
- Default to `HTTPClient.shared` in production
- Generic return type `<T: Decodable>` for type-safe decoding
- Use `_ = ... as EmptyResponse` when discarding response
- Always pass `needsAuth: true` for protected endpoints

---

## Step 4: Create Protocol Definition

Location: `meApp/Domain/Repositories/` (or nearby)

Define the interface stores will inject:

```swift
@MainActor
protocol EntryRepositoryAPIProtocol: AnyObject {
    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse
    func syncOperation(operation: BathScaleOperationDTO) async throws
}
```

---

## HTTPClientProtocol: The Core

Location: `meApp/Core/Network/HTTPClientProtocol.swift`

Two main methods:

```swift
@MainActor
protocol HTTPClientProtocol: AnyObject {
    var skipCheckNetwork: Bool { get set }

    // GET request: returns decoded type
    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> T

    // POST/PUT/PATCH/DELETE with body: sends body, returns response
    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> R
}

enum HTTPMethod: String {
    case get, post, put, patch, delete
}
```

### What HTTPClient Does

1. **Connectivity Check** → throws `.noInternet` if offline (shows throttled toast)
2. **Auth Token Injection** → adds `Authorization: Bearer {token}` if `needsAuth: true`
3. **Token Expiration Check** → calls `TokenManager.checkTokenExpiration(expiresAt:)`
4. **Request Execution** → calls `URLSession.shared.data(for: request)`
5. **Response Parsing** → decodes JSON, handles 204 No Content, handles errors
6. **401 Retry** → refreshes token and retries once (prevents infinite loops)

---

## Token Management Flow

Location: `meApp/Core/Network/TokenManager.swift` (actor for thread safety)

```swift
actor TokenManager: TokenManaging {
    // Check if token expired (nonisolated = no actor state accessed)
    nonisolated func checkTokenExpiration(expiresAt: String?) -> Bool {
        guard let expirationDate = DateTimeTools.parse(expiresAt) else { return true }
        return Date().addingTimeInterval(AppConstants.Account.tokenExpirationBuffer) >= expirationDate
    }

    // Refresh token with retry logic + concurrent request parking
    func refreshToken(accountId: String? = nil, retryCount: Int = 0) async throws -> Tokens {
        // If already refreshing, wait for in-progress refresh (via continuation)
        if isRefreshing {
            try await waitForRefresh()
            return try await accountService.getActiveTokens()
        }

        // Max retries check
        if retryCount >= AppConstants.Account.tokenRefreshMaxRetries {
            try await accountService.logOut(accountId: accountId, isAutoLogout: true)
            throw HTTPError.statusCode(HTTPStatusCode.unauthorized.rawValue)
        }

        isRefreshing = true
        do {
            let tokens = try await accountService.refreshTokens(accountId: accountId)
            try await accountService.updateTokens(tokens, accountId)
            isRefreshing = false
            resumeWaitingRequests()  // Wake all parked requests
            return tokens
        } catch {
            isRefreshing = false
            resumeWaitingRequests(with: error)  // Wake all parked requests with error
            
            // Retry logic: 502/503 or network errors
            if let networkError = error as? HTTPError {
                switch networkError {
                case .statusCode(let code):
                    if let status = HTTPStatusCode(rawValue: code), status.isRetryable {
                        return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                    }
                case .noInternet:
                    return try await refreshToken(accountId: accountId, retryCount: retryCount + 1)
                case .unauthorized:
                    try await accountService.logOut(accountId: accountId, isAutoLogout: true)
                    throw error
                default:
                    break
                }
            }
            
            // If we reach here, auto-logout
            try await accountService.logOut(accountId: accountId, isAutoLogout: true)
            throw error
        }
    }

    // Parks a request until refresh completes
    private func waitForRefresh() async throws {
        try await withCheckedThrowingContinuation { continuation in
            refreshContinuations.append(continuation)
        }
    }
}
```

**Key insight:** While one request refreshes tokens, others wait via continuation. When refresh completes, all waiting requests are resumed with the new tokens.

---

## Connectivity Management

Location: `meApp/Core/Network/NetworkMonitor.swift`

```swift
@MainActor
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()
    
    @Published private(set) var isConnected = false
    @Published private(set) var connectionType: NWInterface.InterfaceType?
    
    private let monitor = NWPathMonitor()  // Apple's Network framework

    // Called by HTTPClient.checkConnectivity()
    func getCurrentConnectionStatus() -> Bool {
        return monitor.currentPath.status == .satisfied
    }
    
    // Ping the base URL to verify real internet (not just WiFi with no internet)
    func verifyNetworkAvailability(baseURL: String) async -> Bool {
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.timeoutInterval = 5
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse) != nil
        } catch {
            return false
        }
    }
}
```

---

## Error Handling

Location: `meApp/Core/Network/` (HTTPError enum)

```swift
enum HTTPError: Error, Equatable {
    case badRequest
    case unauthorized
    case forbidden
    case notFound
    case statusCode(Int)
    case apiError(message: String, code: Int)
    case noInternet
    case timeout
    case invalidResponse
    case decodingError
}

enum HTTPStatusCode: Int {
    case ok = 200
    case created = 201
    case noContent = 204
    case badRequest = 400
    case unauthorized = 401
    case forbidden = 403
    case notFound = 404
    case serverError = 500
    case badGateway = 502
    case serviceUnavailable = 503
    
    var isRetryable: Bool {
        return self == .badGateway || self == .serviceUnavailable
    }
    
    var isSuccess: Bool {
        return (200...299).contains(self.rawValue)
    }
}
```

---

## Common Patterns

### Pattern 1: Simple GET Request

```swift
func fetchEntries(startDate: Date) async throws -> [Entry] {
    return try await httpClient.get(
        .operations(startTimestamp: dateFormatter.string(from: startDate)),
        needsAuth: true
    )
}
```

### Pattern 2: POST with Inline Request Body

```swift
func createEntry(weight: Double, date: Date) async throws {
    struct CreateEntryRequest: Codable {
        let weight: Double
        let timestamp: String
    }
    
    let body = CreateEntryRequest(
        weight: weight,
        timestamp: dateFormatter.string(from: date)
    )
    
    _ = try await httpClient.send(
        .submitOperation,
        method: .post,
        body: body,
        needsAuth: true
    ) as EmptyResponse
}
```

### Pattern 3: Request with Query Parameters

```swift
// In Endpoint enum:
case operationsCSV(utcOffset: Int?, download: Bool?)

var urlRequest: URLRequest? {
    var components = URLComponents(string: "\(API.baseURL)/operation/csv/")
    var queryItems: [URLQueryItem] = []
    if let offset = utcOffset {
        queryItems.append(URLQueryItem(name: "utcOffset", value: "\(offset)"))
    }
    if let shouldDownload = download, shouldDownload {
        queryItems.append(URLQueryItem(name: "download", value: "true"))
    }
    components?.queryItems = queryItems
    guard let url = components?.url else { return nil }
    return URLRequest(url: url)
}

// Usage:
func exportOperationsCSV(utcOffset: Int) async throws -> String {
    return try await httpClient.get(
        .operationsCSV(utcOffset: utcOffset, download: true),
        needsAuth: true
    )
}
```

### Pattern 4: Multi-Account Request

```swift
// Fetch account for a specific account ID
func fetchAccount(accountId: String) async throws -> AccountDTO {
    return try await httpClient.get(
        .accountInfo,
        needsAuth: true,
        accountId: accountId  // Fetch tokens for this account
    )
}
```

### Pattern 5: Error Handling in Store

```swift
func syncEntries() {
    Task {
        notificationService.showLoader(LoaderModel(text: "Syncing..."))
        defer { notificationService.dismissLoader() }
        
        do {
            try await entryRepository.syncEntries()
            notificationService.showToast(ToastModel(
                title: "Success",
                message: "Entries synced"
            ))
        } catch let error as HTTPError {
            switch error {
            case .noInternet:
                notificationService.showToast(ToastModel(
                    message: "No internet connection"
                ))
            case .unauthorized:
                // Already auto-logged out by TokenManager
                notificationService.showAlert(AlertModel(
                    title: "Session Expired",
                    message: "Please log in again",
                    buttons: [AlertButtonModel(title: "OK", type: .primary) { _ in }]
                ))
            case .apiError(let message, _):
                notificationService.showToast(ToastModel(
                    message: "Sync failed: \(message)"
                ))
            default:
                notificationService.showToast(ToastModel(
                    message: "Sync failed. Try again."
                ))
            }
        } catch {
            notificationService.showToast(ToastModel(
                message: "Unexpected error"
            ))
        }
    }
}
```

---

## Golden Rules

1. **Always inject HTTPClientProtocol** — Never use `HTTPClient.shared` directly in stores
2. **Generic type parameters matter** — `get<T>()` auto-decodes to T; specify return type
3. **needsAuth = true for protected endpoints** — Login/logout may need `false`
4. **Use EmptyResponse for 204/no-return endpoints** — Cast as `_ = ... as EmptyResponse`
5. **DTO = DTO + Sendable** — Always conform to both for async safety
6. **Constructor injection for tests** — Pass `httpClient` param to allow mocking
7. **One-off request bodies = inline structs** — Don't create reusable types for single use
8. **Reusable DTOs in Domain/Models/API** — Shared response types live here
9. **Error handling in stores, not repositories** — Repos throw, stores catch and show UI
10. **Token refresh is automatic** — Don't manually refresh; HTTPClient handles it via 401 retry

---

## File Reference

| File | Purpose |
|------|---------|
| `meApp/Core/Network/HTTPClientProtocol.swift` | Interface for all API calls |
| `meApp/Core/Network/HTTPClient.swift` | Concrete implementation: token injection, error handling, retry logic |
| `meApp/Core/Network/TokenManager.swift` | Actor managing token refresh + concurrent request parking |
| `meApp/Core/Network/NetworkMonitor.swift` | Monitors connectivity via NWPath |
| `meApp/Domain/Models/API/EndPoints.swift` | Endpoint enum: all API paths |
| `meApp/Data/API/*RepositoryAPI.swift` | Concrete repository implementations (e.g., EntryRepositoryAPI) |
| `meApp/Domain/Repositories/*RepositoryAPIProtocol.swift` | Protocol interfaces for APIs (injected into stores) |
| `meApp/Domain/Models/API/*DTO.swift` | Request/response DTOs (Codable structs) |

---

## Related Skills

- **`/add-endpoint`** — Step-by-step to add a complete API endpoint
- **`/wire-service`** — Register repository in DI for store injection
- **`/fix-bug`** — Common bug: 401 retry loop; debug via `restartWithNewTokens` flag
- **`/review-security`** — Never log tokens or auth headers in full

---

## API Request Lifecycle (Diagram)

```
1. Store calls: entryRepository.fetchOperations(startDate)
   ↓
2. RepositoryAPI.fetchOperations() calls:
   httpClient.get(.operationsR4(startTimestamp: ...))
   ↓
3. HTTPClient.get<T>():
   3a. checkConnectivity() → throws .noInternet if offline
   3b. makeRequest() → adds Auth header if needsAuth = true
   3c. TokenManager.checkTokenExpiration() → check if token expired
   3d. If expired → refreshToken() → waits if already refreshing
   ↓
4. performRequest(urlRequest):
   4a. URLSession.shared.data(for: request)
   4b. Parse HTTPStatusCode
   4c. If not 2xx → parseErrorResponse() → throw HTTPError
   4d. If 204 or empty → return EmptyResponse
   4e. Else → JSONDecode<T> and return
   ↓
5. Error handling:
   5a. If 401 Unauthorized → TokenManager.refreshToken() → retry once
   5b. If 502/503 → TokenManager retries up to maxRetries
   5c. If max retries reached → auto-logout
   5d. If .noInternet → show throttled toast
   ↓
6. Store receives result or error
   ↓
7. Store catches error → shows alert/toast
```

---

## Summary

The API system is **type-safe, async-first, with built-in token management and error resilience**:

1. **Centralized** — all requests go through `HTTPClientProtocol`
2. **Type-safe** — generics ensure compile-time correctness
3. **Token-managed** — automatic refresh + concurrent request parking
4. **Connected** — connectivity checks + network errors
5. **Resilient** — retries on 502/503, logs out on 401
6. **Testable** — constructor injection allows mock `HTTPClient`

Always use protocols for injection, define DTOs as `Codable + Sendable`, and let HTTPClient handle auth + errors.
