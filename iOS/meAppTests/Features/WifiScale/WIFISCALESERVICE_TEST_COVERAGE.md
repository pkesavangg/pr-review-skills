# WifiScaleService Test Coverage

## Scope
Unit tests for `WifiScaleService` focus on regression safety for:
- Wi-Fi scale token retrieval
- connected Wi-Fi info reads (SSID/BSSID)
- permission-state dependent Wi-Fi behavior
- smart-connect operation dispatch
- setup cancellation and setup error handling

## Cases Covered
1. `getScaleToken` success path
2. `getScaleToken` API failure path
3. `getConnectedWifiInfo` no-network path
4. `getConnectedWifiInfo` Wi-Fi enabled without location permission
5. `getConnectedWifiInfo` connected Wi-Fi with granted location permissions
6. `getConnectedWifiInfo` unknown SSID handling
7. `getConnectedWifiInfo` default location permission state
8. `smartConnect` success path
9. `smartConnect` failure path
10. `espSmartConnect` success path
11. `apMode` success path
12. `apMode` failure path
13. `stop` cancellation path
14. setup config defaulting when optional fields are nil

## Dependency Strategy
Tests follow the Account suite pattern:
- protocol-backed constructor injection in `WifiScaleService`
- dedicated mocks in `meAppTests/Features/WifiScale/Mocks`
- test fixtures in `meAppTests/Features/WifiScale/Fixtures`

## Coverage Target
This suite is designed to keep `WifiScaleService` validated above the requested 85% threshold for service logic paths.

## Current Coverage
- `WifiScaleService.swift`: **98.29%** (`115/117`) from latest `WifiScaleServiceTests` run (`Test-meAppTests-2026.03.03_10-32-57-+0530.xcresult`, March 3, 2026).
