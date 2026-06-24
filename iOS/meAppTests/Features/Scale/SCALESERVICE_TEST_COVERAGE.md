# ScaleService Test Coverage

## Scope
Unit tests for `ScaleService` focus on regression safety for:
- successful scale pairing persistence
- saving and loading R4 scale preferences
- local CRUD behavior for paired scales
- sync promotion of local devices to server-backed records
- syncing metadata and delete flows
- missing-scale and persistence failure paths
- remote update failures with local fallback behavior

## Cases Covered
1. `createR4Scale` success with persisted device and default R4 preference
2. `createR4Scale` local persistence failure
3. `clearAllData` clears local scale storage
4. `getDevices` filters deleted scales and scopes results to the active account
5. `getDevices` without an active account throws
6. `createDevice` returns an existing duplicate instead of saving a second copy
7. `editDevice` success path
8. `editDevice` missing scale failure
9. `deleteDevice` for purely local scales removes the device without a remote delete
10. `deleteDevice` for server-backed scales marks deleted, syncs delete, and removes local data
11. `updateScaleMeta` success with remote patch + synced local metadata
12. `updateScaleMeta` remote failure with unsynced local fallback
13. `updateScalePreference(fromDTO:)` success with remote patch + synced local preference
14. `updateScalePreference(fromDTO:)` remote failure with unsynced local fallback and retry path
15. `updateScalePreference(fromDTO:)` missing scale failure
16. `fetchAttachedPreference` and `fetchAttachedPreferenceSync` loading saved preferences
17. `pushLocalChangesToServer` for purely local device creation and server ID promotion
18. `pushLocalChangesToServer` for server-backed device updates, metadata sync, and preference sync
19. `syncAllScalesWithRemote` no-active-account no-op path
20. `syncAllScalesWithRemote` loading server scales into local published state

## Dependency Strategy
Tests follow the existing service suite pattern:
- constructor injection for local and remote scale repositories
- dedicated mocks in `meAppTests/Features/Scale/Mocks`
- shared fixtures in `meAppTests/Features/Scale/Fixtures`

## Coverage Target
This suite is intended to keep scale pairing, preferences, and sync-critical CRUD flows protected so regressions are caught in CI before release.

## Latest Result
`ScaleService.swift`: **86.6%** from the latest `ScaleServiceTests` run (`57/57` tests passing on March 3, 2026).
