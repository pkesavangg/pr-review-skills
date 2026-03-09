# ScaleRepository Test Coverage

## Source File
`iOS/meApp/Data/Storage/DB/ScaleRepository.swift`

## Test File
`iOS/meAppTests/Features/Scale/ScaleRepositoryTests.swift`

## Infrastructure

### Testability Change
Added `init(context: ModelContext? = nil)` to `ScaleRepository`. Production code is unaffected
(defaults to `PersistenceController.shared.context`).

### makeSUT
Each test creates an isolated in-memory `ModelContainer` with the full schema:
`Device`, `BathScale`, `R4ScalePreference`, `DeviceMetaData`.

### Fixtures Used
- `ScaleTestFixtures.makeDevice(...)` — creates Device with BathScale + R4ScalePreference attached
- `ScaleTestFixtures.makeScaleDTO(...)` — creates ScaleDTO for replace-all sync tests
- `ScaleTestFixtures.makePreferenceDTO(...)` — creates R4ScalePreferenceDTO for preference patch tests
- `ScaleTestFixtures.makeMetaData(...)` — creates DeviceMetaData for meta patch tests

---

## Test Table

| # | Test Name | Method(s) Covered | Scenario |
|---|-----------|-------------------|----------|
| 1 | `createAndListScales` | `createScale`, `listScales(forAccountId:)` | Insert persists and can be retrieved |
| 2 | `createScaleSetsSyncedFalse` | `createScale` | `isSynced` is forced to `false` regardless of input |
| 3 | `createScaleReturnsDevice` | `createScale` | Returns device with correct id/accountId |
| 4 | `createScalePreservesPreference` | `createScale`, `fetchAttachedPreference` | R4 preference relationship is stored |
| 5 | `createScaleDuplicateIdThrows` | `createScale` | Duplicate prevention via unique constraint |
| 6 | `editScaleUpdatesNickname` | `editScale` | Nickname field is updated correctly |
| 7 | `editScaleSetsSyncedFalse` | `editScale` | `isSynced` is reset to `false` after edit |
| 8 | `editScaleNotFoundThrows` | `editScale` | 404 thrown for nonexistent scale ID |
| 9 | `updateDevicePersistsFields` | `updateDevice` | Field changes on managed object are saved |
| 10 | `updateDeviceNotFoundThrows` | `updateDevice` | 404 thrown for unregistered device |
| 11 | `updateDeviceWithNewIdReplacesId` | `updateDeviceWithNewId` | Old ID is replaced with new ID in storage |
| 12 | `updateDeviceWithNewIdNotFoundThrows` | `updateDeviceWithNewId` | 404 thrown for nonexistent oldId |
| 13 | `deleteScaleRemovesDevice` | `deleteScale` | Device is removed from storage |
| 14 | `deleteScaleNonexistentSilent` | `deleteScale` | No throw for nonexistent ID |
| 15 | `clearAllDataRemovesAll` | `clearAllData` | All devices are removed |
| 16 | `patchScalePreferenceCreatesNew` | `patchScalePreference(fromDTO:)` | Creates new preference when device has none |
| 17 | `patchScalePreferenceUpdatesExisting` | `patchScalePreference(fromDTO:)` | Updates existing preference fields |
| 18 | `patchScalePreferenceNoDuplicates` | `patchScalePreference(fromDTO:)` | Repeated calls update in-place, no duplicates |
| 19 | `patchScalePreferenceNotFoundThrows` | `patchScalePreference(fromDTO:)` | Throws for nonexistent scale |
| 20 | `fetchAttachedPreferenceNil` | `fetchAttachedPreference` | Returns nil for unknown ID |
| 21 | `fetchAttachedPreferenceSyncConsistent` | `fetchAttachedPreference`, `fetchAttachedPreferenceSync` | Both variants return identical result |
| 22 | `listScalesFiltersByAccount` | `listScales(forAccountId:)` | Filters correctly by accountId |
| 23 | `listScalesReturnsAll` | `listScales()` | Returns all devices across accounts |
| 24 | `listScalesForUnknownAccountEmpty` | `listScales(forAccountId:)` | Empty result for unknown account |
| 25 | `getDeviceById` | `getDevice` | Returns matching device |
| 26 | `getDeviceNilForUnknown` | `getDevice` | Returns nil for nonexistent ID |
| 27 | `getUnsyncedDevicesFiltersCorrectly` | `getUnsyncedDevices` | Returns only unsynced devices |
| 28 | `patchScaleMetaCreatesNew` | `patchScaleMeta` | Creates meta when device has none |
| 29 | `patchScaleMetaUpdatesExisting` | `patchScaleMeta` | Updates existing meta data fields |
| 30 | `patchScaleMetaNotFoundThrows` | `patchScaleMeta` | Throws for nonexistent scale |
| 31 | `markDeviceAsDeleted` | `markDeviceAsDeleted` | Sets `isSoftDeleted=true`, `isSynced=false` |
| 32 | `getDevicesMarkedForDeletion` | `getDevicesMarkedForDeletion` | Returns only soft-deleted unsynced devices |
| 33 | `permanentlyRemoveDevice` | `permanentlyRemoveDevice` | Permanently removes device from storage |
| 34 | `isDevicePurelyLocalTrue` | `isDevicePurelyLocal` | Returns true for local-only device |
| 35 | `isDevicePurelyLocalFalseWhenHasServerId` | `isDevicePurelyLocal` | Returns false when hasServerID=true |
| 36 | `isDevicePurelyLocalFalseForNonexistent` | `isDevicePurelyLocal` | Returns false for unknown device |
| 37 | `replaceAllDevicesForAccountReplacesSynced` | `replaceAllDevicesForAccount` | Synced devices replaced with server devices |
| 38 | `replaceAllDevicesForAccountPreservesUnsynced` | `replaceAllDevicesForAccount` | Unsynced devices are preserved |
| 39 | `replaceAllDevicesForAccountEmptyServerDevices` | `replaceAllDevicesForAccount` | Empty server list removes all synced devices |
| 40 | `replaceAllDevicesForAccountIsolatesAccounts` | `replaceAllDevicesForAccount` | Other accounts' devices are unaffected |
| 41 | `createDeleteCycleIsSafe` | `createScale`, `deleteScale` | Repeated insert/delete cycle is safe |
| 42 | `repeatedEditScalePreservesLastValue` | `editScale` | Last write wins after multiple edits |
| 43 | `repeatedPatchPreferencePreservesLastValue` | `patchScalePreference(fromDTO:)` | Last preference write wins after many patches |

---

## Run Commands

```bash
# All ScaleRepository tests
xcodebuild test -scheme meApp -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/ScaleRepositoryTests

# Full test suite
xcodebuild test -scheme meApp -destination 'platform=iOS Simulator,name=iPhone 16'
```

## Known Gaps

- `patchScalePreference(_ scaleId: String, _ preference: R4ScalePreference)` — the model-object
  variant is not directly tested (it shares the same internal branch logic as the DTO variant,
  which is covered by tests 16–19).
- `copyDeviceFields`, `insertDeviceRelationships`, `findMatchingUnsyncedDevice`,
  `updateDeviceFromDTO` — private helpers exercised indirectly through public API tests.
