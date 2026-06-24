# PushNotificationService Test Coverage

## Scope
Unit tests for `PushNotificationService` focus on regression safety for:
- receiving push payloads
- tap handling/navigation signal posting
- push setup and permission flow
- device info update trigger paths
- FCM token storage/read/migration logic
- duplicate/in-progress/error edge handling

## Cases Covered
1. `handleNotification` data-only payload: sync entries/scales + local banner
2. `handleNotification` alert payload: no local banner duplication
3. `handleNotification` duplicate message ID skip
4. `handleNotification` in-progress handling skip
5. `handleNotificationTap` destination post via `NotificationCenter`
6. `setupPushNotifications` token already available skip-register path
7. `setupPushNotifications` success register/token/store/update path
8. `setupPushNotifications` from scale setup permission prompt path
9. `setupPushNotifications` notifications category not required path
10. `setupPushNotifications` authorization mismatch path
11. `getStoredFCMToken` keychain-first read path
12. `getStoredFCMToken` legacy KvStorage migration path
13. `getStoredFCMToken` no-token nil path
14. token refresh notification storage + device info update path

## Dependency Strategy
Tests follow the Account suite pattern:
- protocol-backed constructor injection in `PushNotificationService`
- dedicated mocks in `meAppTests/Features/PushNotification/Mocks`
- test fixtures in `meAppTests/Features/PushNotification/Fixtures`

## Coverage Target
This suite is designed to keep `PushNotificationService` validated above the requested 85% threshold for service logic paths.

## Current Coverage
- `PushNotificationService.swift`: **92.60%** (`388/419`) from latest `PushNotificationServiceTests` run (`Test-meAppTests-2026.03.02_22-24-01-+0530.xcresult`, March 2, 2026).
