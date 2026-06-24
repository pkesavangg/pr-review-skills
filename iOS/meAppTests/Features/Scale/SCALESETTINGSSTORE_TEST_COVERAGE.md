# ScaleSettingsStore Test Coverage

## Scope
Unit tests for `ScaleSettingsStore` focus on regression safety for:
- initial cached scale/preference state
- Bluetooth permission gating for connected state
- product manual browser state
- Wi-Fi MAC address loading
- users-list loading for connected scales
- Bluetooth device info refresh and derived UI state
- preference sync handoff after device info fetch
- alert-driven body-metrics enable flow
- alert-driven scale deletion flow
- session impedance setting updates

## Cases Covered
1. Initial state uses cached preference values and Bluetooth permission gating
2. `refreshScaleData` falls back to active-account name when preference is missing
3. `openProductGuide` sets browser state
4. `ensureWifiMacAddress` fetches once and caches result
5. `ensureWifiMacAddress` failure keeps the cached value `nil`
6. `ensureUsersList` returns empty when the scale is disconnected
7. `ensureUsersList` stores fetched users for connected R4 scales
8. `ensureUsersList` failure clears users and returns empty
9. `getDeviceInfo` returns early when the scale is disconnected
10. `getDeviceInfo` success updates published state and syncs unsynced preferences
11. `getDeviceInfo` failure does not fetch Wi-Fi SSID or sync preferences
12. `getDeviceInfo` skips preference sync when preferences are already synced
13. `getDeviceInfo` handles Bluetooth preference-sync failure without mutating sync state
14. Scale publisher events trigger a device-info refresh for the current scale
15. `handleEnableBodyMetrics` shows alert and primary action enables session
16. `handleEnableBodyMetrics` does nothing when the scale is disconnected
17. `handleEnableBodyMetrics` failure does not show a success toast
18. `handleScaleDelete` confirms deletion and runs the service sync flow
19. `handleScaleDelete` failure shows an error toast, dismisses the loader, and skips success callback
20. `setSessionImpedance` success updates the session flag
21. `setSessionImpedance` returns early when the scale is disconnected
22. `setSessionImpedance` failure leaves the session flag unchanged

## Dependency Strategy
Tests follow the existing store-suite pattern:
- DI container registration scoped per test
- dedicated mocks for notification, scale, Bluetooth, account, logger, and permissions services
- real `ScaleSettingsStore` under test
- serialized execution to avoid shared DI collisions

## Coverage Target
This suite is intended to keep `ScaleSettingsStore.swift` above 90% line coverage while protecting the highest-risk regressions in CI:
- device info fetch and preference sync
- delete flow success and failure handling
- Bluetooth-driven settings updates
- disconnected/no-op branches
- Wi-Fi MAC and users-list loading failures

## Code Coverage
`ScaleSettingsStore.swift`: **93.7%** from latest `ScaleSettingsStoreTests` run (22/22 tests passing on March 3, 2026).
