# TokenManager Test Coverage Guide

## Purpose
This document explains how `TokenManager` is tested, what token lifecycle flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Core/Network/TokenManager.swift`
- Main test suite:
  - `meAppTests/Features/Common/Network/TokenManagerTests.swift`
- Token manager-specific mocks:
  - `meAppTests/Features/Common/Network/Mocks/MockTokenManagerAccountService.swift`
- Shared test support:
  - `meAppTests/Support/Mocks/UnexpectedCallError.swift`

## Coverage Strategy
`TokenManager` has retry/concurrency branches. Coverage is improved by testing each method with:
1. Valid token checks
2. Expiration and buffer edge cases
3. Refresh success and retry paths
4. Failure/logout paths and waiting-request behavior

## Flows Covered

### 1) Valid Token Flow
- `checkTokenExpiration`: valid future token returns `false`

### 2) Token Expiration Handling
- `checkTokenExpiration`: missing/invalid expiration returns `true`
- `checkTokenExpiration`: near-expiry (within buffer) returns `true`

### 3) Token Refresh Behavior
- `refreshToken`: refresh succeeds and updates stored tokens
- `refreshToken`: concurrent call waits for in-flight refresh and resumes with active tokens
- `refreshToken`: retryable `502` status retries and succeeds
- `refreshToken`: `noInternet` retries and succeeds

### 4) Error Handling
- `refreshToken`: unauthorized error logs out and throws
- `refreshToken`: non-retryable service error logs out and throws
- `refreshToken`: retry exhaustion logs out and throws unauthorized status error
- Waiting request propagates `getActiveTokens` failure cleanly

## How `makeSUT` Works
`makeSUT` in `TokenManagerTests` creates a fresh actor instance with constructor-injected `AccountServiceProtocol` mock:
- no shared singleton coupling
- deterministic token refresh behavior
- explicit call-count and side-effect assertions

## How To Add New TokenManager Tests
1. Configure `MockTokenManagerAccountService` result behavior.
2. Call `checkTokenExpiration` or `refreshToken`.
3. Assert:
   - return values / thrown errors
   - refresh/update/logout call counts
   - accountId and token propagation.

## Run and Check Coverage
Run from repo root.

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/TokenManagerTests
```

Coverage in Xcode:
1. Test Report (`Cmd+9`)
2. Open latest run
3. Coverage tab
4. Inspect `TokenManager.swift`

## Team Expectation
- Keep `TokenManager.swift` coverage at least **85%**
- Every token-refresh regression should add a deterministic unit test

## Current Coverage
- `TokenManager.swift`: **~96%** from latest `TokenManagerTests` validation snapshot (March 5, 2026).
- Current coverage (as of 2026-03-05): **~92.8%**
- Estimated line coverage for `TokenManager.swift`: **~92.8%**
