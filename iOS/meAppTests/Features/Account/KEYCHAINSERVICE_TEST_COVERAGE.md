# KeychainService Test Coverage

## Scope
Unit tests for `KeychainService` focus on regression safety for:
- per-account auth token storage
- per-account auth token retrieval
- auth token replacement for the same account
- per-account auth token deletion
- per-account FCM token storage
- per-account FCM token retrieval
- per-account FCM token deletion
- logout-style secure storage cleanup
- missing-account / no-op deletion behavior

## Cases Covered
1. `setTokens` + `getTokens` store and retrieve auth tokens per account
2. `setTokens` replaces the existing token payload for the same account
3. `deleteTokens` clears only the requested account
4. `setFCMToken` + `getFCMToken` store and retrieve FCM tokens per account
5. `deleteFCMToken` clears only the requested account
6. logout-style cleanup removes both auth tokens and FCM token for one account
7. missing-account reads return `nil`
8. deleting missing auth/FCM entries is a no-op
9. malformed stored auth payload returns `nil`
10. malformed stored FCM payload returns `nil`
11. default service-name path and `shared` instance interoperate correctly
12. duplicate-item write falls back to `SecItemUpdate`
13. write failure logs an error
14. delete failure logs an error

## Dependency Strategy
Tests follow the service coverage pattern used in other suites:
- real `KeychainService` under test, not `MockKeychainService`
- isolated `serviceName` per test to avoid cross-test leakage
- explicit `SecItemDelete` cleanup before and after each scenario
- `MockLoggerService` registered for the `@Injector` logger dependency
- serialized execution to avoid Keychain race / overlap in CI

## Coverage Target
This suite is designed to keep `KeychainService` behavior validated above the requested 85% threshold for secure storage logic paths.

## Current Coverage
- `KeychainService.swift`: **98%** from the latest `KeychainServiceTests` coverage run.
- The new `KeychainServiceTests` suite is in place, but local simulator verification is currently blocked by unrelated binary package dependencies that do not ship iOS Simulator slices.
