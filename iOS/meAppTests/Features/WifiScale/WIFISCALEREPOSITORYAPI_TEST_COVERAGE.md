# WifiScaleRepositoryAPI Test Coverage

## Overview
- **File Under Test**: `iOS/meApp/Data/API/WifiScaleRepositoryAPI.swift`
- **Test File**: `iOS/meAppTests/Features/WifiScale/WifiScaleRepositoryAPITests.swift`
- **Coverage**: ~100% (all public methods and code paths tested)

## Test Summary

| # | Test Name | Category | Description |
|---|-----------|----------|-------------|
| 1 | `getScaleTokenWithRequest` | Request Construction | Verifies endpoint built with request parameter |
| 2 | `getScaleTokenWithNilRequest` | Request Construction | Verifies endpoint built with nil parameter |
| 3 | `getScaleTokenRequiresAuth` | Request Construction | Confirms `needsAuth: true` is passed |
| 4 | `getScaleTokenUsesCorrectEndpoint` | Endpoint Usage | Confirms `.wifiScale` endpoint is used |
| 5 | `getScaleTokenSuccessDecodesResponse` | Response Decoding | Validates token value in decoded response |
| 6 | `getScaleTokenSuccessEmptyToken` | Response Decoding | Handles empty string token |
| 7 | `getScaleTokenSuccessLongToken` | Response Decoding | Handles long token values |
| 8 | `getScaleTokenServerError` | Error Mapping | Propagates `.serverError` |
| 9 | `getScaleTokenUnauthorized` | Error Mapping | Propagates `.unauthorized` |
| 10 | `getScaleTokenNotFound` | Error Mapping | Propagates `.notFound` |
| 11 | `getScaleTokenBadRequest` | Error Mapping | Propagates `.badRequest` |
| 12 | `getScaleTokenForbidden` | Error Mapping | Propagates `.forbidden` |
| 13 | `getScaleTokenApiError` | Error Mapping | Propagates `.apiError` with message and code |
| 14 | `getScaleTokenStatusCodeError` | Error Mapping | Propagates `.statusCode` |
| 15 | `getScaleTokenNoInternet` | Network Failure | Propagates `.noInternet` |
| 16 | `getScaleTokenTimeout` | Network Failure | Propagates `.timeout` |
| 17 | `getScaleTokenDecodingError` | Malformed Response | Propagates `.decodingError` |
| 18 | `getScaleTokenInvalidResponse` | Malformed Response | Propagates `.invalidResponse` |
| 19 | `getScaleTokenTypeMismatch` | Malformed Response | Handles type mismatch from mock |
| 20 | `getScaleTokenNoResultConfigured` | Malformed Response | Handles missing result configuration |
| 21 | `repeatedGetScaleTokenCallCount` | Repeated Calls | Verifies call count increments |
| 22 | `repeatedGetScaleTokenLastEndpoint` | Repeated Calls | Captures most recent call parameters |
| 23 | `getScaleTokenRecoveryAfterError` | Repeated Calls | Verifies recovery after error |
| 24 | `getScaleTokenNoAccountId` | Account ID | Confirms default nil accountId |

**Total: 24 tests**

## Mock Infrastructure
- **MockHTTPClient** (`meAppTests/Support/Mocks/Network/MockHTTPClient.swift`) — shared mock with call tracking and configurable results/errors

## Production Code Change
- Added `init(httpClient: HTTPClientProtocol = HTTPClient.shared)` to `WifiScaleRepositoryAPI` for testability (matches `ScaleAPIRepository` pattern)

## Run Command
```bash
cd iOS && xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  -only-testing:meAppTests/WifiScaleRepositoryAPITests \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

## Known Gaps
- None — `WifiScaleRepositoryAPI` has a single method (`getScaleToken`) which is fully covered across success, error, and edge cases.
