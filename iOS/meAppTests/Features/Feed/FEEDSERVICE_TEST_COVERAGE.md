# FeedService Test Coverage

## Scope
Unit tests for `FeedService` focus on regression safety for:
- feed loading
- empty and partial payload handling
- failure paths (network and invalid data)
- feed action updates
- modal trigger behavior
- badge/setting behavior

## Cases Covered
1. `fetchFeedItems` success with non-empty data
2. `fetchFeedItems` success with empty result
3. `fetchFeedItems` network failure fallback
4. `fetchFeedItems` invalid data failure fallback
5. `updateFeedItem` with non-meta action (`read`)
6. `updateFeedItem` with meta action (`variationClick`)
7. `getFeedSettings` mapping
8. `clearFeedData` + badge refresh
9. `checkAndTriggerFeedModal` trigger-present path
10. `checkAndTriggerFeedModal` no-trigger path

## Dependency Strategy
Tests follow the Account suite pattern:
- protocol-backed constructor injection in `FeedService`
- dedicated mocks in `meAppTests/Features/Feed/Mocks`
- test fixtures in `meAppTests/Features/Feed/Fixtures`

## Coverage Target
This suite is designed to keep `FeedService` behavior validated above the requested 85% threshold for service logic paths.

## Current Coverage
- `FeedService.swift`: **95.14%** (`176/185`) from latest `FeedServiceTests` run (`.xcresult` on March 2, 2026).
