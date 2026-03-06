# HTTPClient Test Coverage Guide

## Purpose
This document explains how `HTTPClient` is tested, what request/response and error-handling flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Core/Network/HTTPClient.swift`
- Main test suite:
  - `meAppTests/Features/Common/Network/HTTPClientTests.swift`
- HTTP client-specific mocks:
  - `meAppTests/Features/Common/Network/Mocks/MockHTTPClientTokenManager.swift`
  - `meAppTests/Features/Common/Network/Mocks/MockTokenManagerAccountService.swift`
- Shared test support:
  - `meAppTests/Features/GoalAlert/Mocks/MockNotificationHelperService.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`
  - `meAppTests/Features/Account/Fixtures/AccountTestFixtures.swift`

## Coverage Strategy
`HTTPClient` includes request construction, response decoding, token-refresh paths, and error translation. Coverage is improved by testing each API path with:
1. Success response handling
2. Connectivity and URL error mapping
3. Invalid/malformed response handling
4. Auth retry behavior on unauthorized responses

## Flows Covered

### 1) Successful Response Handling
- `get` decodes valid JSON payloads
- `send` encodes body correctly and decodes response payload

### 2) Timeout and Offline Errors
- Offline connectivity guard returns `HTTPError.noInternet`
- `skipCheckNetwork` suppresses offline toast while still throwing
- `URLError.timedOut` maps to `HTTPError.timeout`
- Other network URL errors map to `HTTPError.noInternet`

### 3) Invalid Response Handling
- Non-HTTP URLResponse maps to `HTTPError.invalidResponse`
- Unknown HTTP status code maps to `HTTPError.statusCode`
- Malformed success payload maps to `HTTPError.decodingError`
- Structured server error payload maps to `HTTPError.apiError`

### 4) Retry Behavior
- Expired token path refreshes token before request and reuses new bearer token
- Unauthorized authenticated request triggers one token refresh and a single retry

## How `makeSUT` Works
`makeSUT` in `HTTPClientTests` creates `HTTPClient` with constructor-injected dependencies:
- mock account service
- mock notification helper
- mock logger
- mock token manager
- deterministic connectivity provider
- deterministic async request executor

This keeps tests deterministic and isolated from real network state.

## How To Add New HTTPClient Tests
1. Configure injected mock/token/connectivity behavior.
2. Provide a request-executor closure returning custom `(Data, URLResponse)` or throwing errors.
3. Call `get` or `send`.
4. Assert:
   - decoded output / thrown error
   - retry/token-refresh side effects
   - request header/body expectations.

## Run and Check Coverage
Run from repo root.

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/HTTPClientTests
```

Coverage in Xcode:
1. Test Report (`Cmd+9`)
2. Open latest run
3. Coverage tab
4. Inspect `HTTPClient.swift`

## Team Expectation
- Keep `HTTPClient.swift` coverage at least **85%**
- Every networking regression should include a deterministic unit test

## Current Coverage
- `HTTPClient.swift`: **~92%** from latest `HTTPClientTests` validation snapshot (March 5, 2026).
- Current coverage (as of 2026-03-05): **~94%**
- Estimated line coverage for `HTTPClient.swift`: **~94%**
