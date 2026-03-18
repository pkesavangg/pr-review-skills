# IntegrationRepository Test Coverage

## Source File
`iOS/meApp/Data/Storage/DB/IntegrationRepository.swift`

## Test File
`iOS/meAppTests/Features/Integrations/IntegrationRepositoryTests.swift`

## Coverage: ~95%

All 4 public methods and 4 private helper methods are tested:
- `getIntegrationData(accountId:)` — 7 tests (nil, wrong type, corrupt data, round-trip, all fields, all types)
- `setIntegrationData(accountId:info:)` — 8 tests (insert, nil clear, overwrite, multi-account, registry tracking)
- `isIntegrationAlreadyUsed(accountId:type:)` — 7 tests (empty, same account, conflict, different type, not integrated, corrupt keys, non-Data values)
- `clearIntegrationStatus(accountId:)` — 4 tests (removes data, removes registry key, safe for missing, does not affect others)
- `makeIntegrationKey(for:)` — implicitly tested via key format assertion
- `getIntegrationKeys()` / `addIntegrationKey()` / `removeIntegrationKey()` — implicitly tested via registry assertions

## Test Summary (35 tests, all passing)

| # | Test | Category |
|---|------|----------|
| 1 | insertStoresData | Insert |
| 2 | insertAddsKeyToRegistry | Insert |
| 3 | insertMultipleAccounts | Insert |
| 4 | insertNilClearsData | Insert |
| 5 | insertNilRemovesKeyFromRegistry | Insert |
| 6 | updateOverwritesExisting | Update |
| 7 | updateNoDuplicateKeys | Update |
| 8 | updateDoesNotAffectOther | Update |
| 9 | deleteRemovesData | Delete |
| 10 | deleteRemovesKeyFromRegistry | Delete |
| 11 | deleteDoesNotAffectOthers | Delete |
| 12 | deleteSafeForMissing | Delete |
| 13 | getReturnsNilForMissing | State Persistence |
| 14 | getReturnsNilForWrongType | State Persistence |
| 15 | statePreservesAllFields | State Persistence |
| 16 | allTypesRoundTrip | State Persistence |
| 17 | fetchConsistencyRepeatedReads | Fetch Consistency |
| 18 | isUsedReturnsFalseWhenEmpty | Fetch Consistency |
| 19 | isUsedReturnsFalseForSameAccount | Fetch Consistency |
| 20 | isUsedReturnsTrueForConflict | Fetch Consistency |
| 21 | isUsedReturnsFalseForDifferentType | Fetch Consistency |
| 22 | isUsedReturnsFalseWhenNotIntegrated | Fetch Consistency |
| 23 | isUsedSkipsCorruptedKeys | Fetch Consistency |
| 24 | isUsedSkipsNonDataValues | Fetch Consistency |
| 25 | duplicatePreventionSingleRegistryEntry | Duplicate Prevention |
| 26 | duplicatePreventionSeparateAccounts | Duplicate Prevention |
| 27 | getThrowsForCorruptData | Persistence Failure |
| 28 | getThrowsForMalformedJSON | Persistence Failure |
| 29 | rerunSetGetConsistent | Re-run Safety |
| 30 | rerunSetAfterClear | Re-run Safety |
| 31 | rerunClearIdempotent | Re-run Safety |
| 32 | fullLifecycle | Re-run Safety |
| 33 | emptyAccountId | Edge Cases |
| 34 | specialCharacterAccountId | Edge Cases |
| 35 | keyFormatMatchesExpected | Edge Cases |

## Source Change
- Changed `IntegrationRepository.kvStorage` type from concrete `KvStorageService` to `KvStorageServiceProtocol` to enable mock injection (no behavioral change)

## Mock Infrastructure
- `MockKvStorageService` (existing, in `meAppTests/Features/HealthKit/Mocks/`) — in-memory dictionary-based mock
- `IntegrationTestFixtures` (existing, in `meAppTests/Features/Integrations/Fixtures/`) — factory for `IntegrationInfo`

## Run Command
```bash
cd iOS && xcodebuild test -scheme meAppTests -destination 'platform=iOS,id=00008120-001268C4342A601E' -only-testing:meAppTests/IntegrationRepositoryTests
```

## Known Gaps
- LoggerService calls are not verified (uses `LoggerService.shared` singleton, not injectable)
