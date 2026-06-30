---
name: add-endpoint
description: Add a new backend endpoint end-to-end across the iOS stack. Use when the user says "add an API call", "wire a new endpoint", "connect this screen to backend", or when a ticket introduces a new server interaction. The endpoint is: $ARGUMENTS
---

Add a backend endpoint or API-backed operation across the relevant layers (Endpoint → DTO → Protocol → Concrete → Service → Store).

The endpoint or API change is: $ARGUMENTS

## Instructions

### 1 — Find the Existing Vertical Slice

Inspect the relevant files before editing:
- `meApp/Domain/Models/API/EndPoints.swift` — all Endpoint cases
- The relevant `meApp/Domain/Repositories/*RepositoryAPIProtocol.swift` — protocol defining contract
- The concrete `meApp/Data/API/*RepositoryAPI.swift` — implementation
- The calling service in `meApp/Data/Services/` — if service thread is needed
- Any store/view model that triggers the flow

Use repo-relative search to find similar patterns:
```bash
rg -n "case {similar_endpoint}" meApp/Domain/Models/API/EndPoints.swift
rg -n "{protocol_name}Protocol" meApp/Domain/Repositories/ -g '*.swift'
```

### 2 — Plan the Layer Changes

Decide which layers need updates:
- **Endpoint** case and URL request in `EndPoints.swift`
- **DTOs** (Request/Response) in `Domain/Models/API/{Feature}/` or inline in repository
- **Protocol** method signature in `Domain/Repositories/*RepositoryAPIProtocol.swift`
- **Concrete** implementation in `Data/API/*RepositoryAPI.swift`
- **Service** method if business logic is needed
- **Store / view model** action if user-facing

### 3 — Implement Consistently

**Step 3A: Add the Endpoint case**

In `meApp/Domain/Models/API/EndPoints.swift`, add a case:
```swift
enum Endpoint {
    case yourNewEndpoint(queryParam: String?)  // with optional params

    var urlRequest: URLRequest? {
        switch self {
        case let .yourNewEndpoint(queryParam):
            var components = URLComponents(string: API.baseURL + "/users/profile")
            if let queryParam = queryParam {
                components?.queryItems = [URLQueryItem(name: "param", value: queryParam)]
            }
            return components?.url.map { URLRequest(url: $0) }
        // ... other cases
        }
    }
}
```

**Step 3B: Define Request/Response DTOs**

For simple endpoints, inline the request struct in the repository method. For reusable DTOs, create in `Domain/Models/API/{Feature}/`:
```swift
// Inline (preferred for one-off endpoints):
struct UpdateProfileRequest: Codable {
    let firstName: String
    let lastName: String
    let email: String
}

// Reusable DTOs go in Domain/Models/API/{Feature}/:
struct ProfileResponse: Codable {
    let id: String
    let firstName: String
    let lastName: String
    let email: String
}
```

**Step 3C: Add Protocol Method**

In the relevant `Domain/Repositories/*RepositoryAPIProtocol.swift`:
```swift
@MainActor
protocol AccountRepositoryAPIProtocol {
    /// Fetches user profile details. (GET /users/profile)
    /// - Parameter accountId: Optional account ID for multi-account contexts.
    /// - Returns: ProfileResponse with user details.
    func fetchProfile(accountId: String?) async throws -> ProfileResponse
}
```

**Step 3D: Implement in Concrete Repository**

In `Data/API/*RepositoryAPI.swift`:
```swift
@MainActor
final class AccountRepositoryAPI: AccountRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func fetchProfile(accountId: String? = nil) async throws -> ProfileResponse {
        return try await httpClient.get(.yourNewEndpoint(queryParam: nil), needsAuth: true, accountId: accountId)
    }

    func updateProfile(_ request: UpdateProfileRequest) async throws -> ProfileResponse {
        return try await httpClient.send(.updateProfile, method: .patch, body: request, needsAuth: true)
    }
}
```

**HTTPClientProtocol patterns:**
- `httpClient.get(.endpoint, needsAuth: true)` — GET requests
- `httpClient.send(.endpoint, method: .post, body: request, needsAuth: true)` — POST/PUT/PATCH with body
- `accountId: accountId` — pass for multi-account contexts
- `needsAuth: true` — automatically adds Bearer token from Keychain

**Step 3E: Thread Through Service (if needed)**

If business logic is required, add a service method that calls the repository:
```swift
@MainActor
final class AccountService: AccountServiceProtocol {
    @Injector private var accountRepository: AccountRepositoryAPIProtocol

    func fetchUserProfile(accountId: String? = nil) async throws -> Profile {
        let dto = try await accountRepository.fetchProfile(accountId: accountId)
        return dto.toDomainModel()  // Convert DTO to domain model
    }
}
```

**Step 3F: Update Store / ViewModel (if user-facing)**

Call the service or repository from the store:
```swift
@MainActor
final class ProfileStore: ObservableObject {
    @Published var profile: Profile?
    @Injector var accountService: AccountServiceProtocol

    func loadProfile() async {
        do {
            self.profile = try await accountService.fetchUserProfile()
        } catch {
            // handle error
        }
    }
}
```

### 4 — Validate Endpoint Schema with `api-change-planner` (Optional but Recommended)

**Before implementing**, invoke the `api-change-planner` agent to validate your endpoint design against the actual backend schema. This subagent will:
- Verify endpoint path, method, and URL structure
- Check request/response DTO compatibility with backend API spec
- Validate query parameters and request body encoding
- Identify potential schema mismatches before implementation
- Suggest corrections if the endpoint design doesn't match backend

**Invocation:**
```
Spawn api-change-planner agent with:
- Endpoint: {endpoint_name}
- HTTP method: {GET|POST|PUT|PATCH|DELETE}
- URL path: {path_from_backend_spec}
- Request DTO: {structure or schema}
- Response DTO: {structure or schema}
- Expected behavior: {brief description}
```

The agent will return:
- ✅ Validation passed — proceed with implementation
- ⚠️ Schema mismatch — suggested corrections to endpoint/DTOs
- ❌ Endpoint not found — verify with backend team

**Skip this if:**
- You're implementing a known/tested endpoint pattern
- Backend spec is not yet available
- This is a straightforward GET/list endpoint

### 4B — Manual Integration Checks

Before finishing, verify:
- No duplicate endpoint or protocol method already exists (search EndPoints.swift + repository protocols)
- URL path/query construction uses safe encoding (URLComponents for queries, let framework handle percent-encoding)
- DTO mapping is isolated to API/repository layer (no domain models in API methods)
- Request body is Codable (Encodable for requests, Decodable for responses)
- Any new dependency introduced is compatible with DI setup (check `ServiceRegistry` registration order if adding new service)
- Multi-account context is threaded correctly (check `accountId` parameter presence)

If the API change also introduces a new service, follow up with `/wire-service`.

### 5 — Verify Build & Coverage

Run a build check to ensure no compilation errors:
```bash
cd iOS && xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'generic/platform=iOS' \
  -configuration Debug \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

### 6 — Recommend Follow-Up

Report:
- **Files changed by layer:** Endpoint case, DTOs, protocol, implementation, service (if added), store/ViewModel
- **Test coverage needed:** Data layer 75% minimum for API repository adapters
- **Next steps:** Follow with `/gen-test-file` for repository tests (use `MockHTTPClient` Pattern C for API layer), `/gen-mock` if new service added, `/verify-tests` to confirm coverage
