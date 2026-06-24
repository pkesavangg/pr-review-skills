# EntryRepository Test Coverage

## Source
`iOS/meApp/Data/Storage/DB/EntryRepository.swift`

## Test File
`iOS/meAppTests/Features/Entry/EntryRepositoryTests.swift`

## Testability Change
- Added `init(container: ModelContainer? = nil)` to `EntryRepository`
- Tests inject an in-memory `ModelContainer` via `ModelConfiguration(isStoredInMemoryOnly: true)`
- No production behavior change (defaults to `PersistenceController.shared.container`)

## Test Coverage

| # | Test | Method Under Test | Category |
|---|------|-------------------|----------|
| 1 | saveAndFetchAll | saveEntry / fetchAllEntries | CRUD |
| 2 | savePreservesScaleEntry | saveEntry (scaleEntry relationship) | CRUD |
| 3 | savePreservesScaleMetric | saveEntry (scaleEntryMetric relationship) | CRUD |
| 4 | saveWithoutScaleData | saveEntry (nil relationships) | CRUD |
| 5 | fetchEntryById | fetchEntry(byId:) | CRUD |
| 6 | fetchEntryByIdNotFound | fetchEntry(byId:) - nonexistent | CRUD |
| 7 | fetchEntryByInvalidId | fetchEntry(byId:) - bad UUID | CRUD |
| 8 | updateEntryModifiesFields | updateEntry | CRUD |
| 9 | updateEntrySyncStatus | updateEntrySyncStatus | CRUD |
| 10 | updateEntrySyncStatusInvalidId | updateEntrySyncStatus - bad UUID | CRUD |
| 11 | deleteEntryById | deleteEntry(byId:) | CRUD |
| 12 | deleteEntryNonexistent | deleteEntry(byId:) - no-op | CRUD |
| 13 | deleteAllEntries | deleteAllEntries | CRUD |
| 14 | fetchEntriesForUser | fetchEntries(forUserId:) | Query |
| 15 | fetchEntriesForUserWithOperationType | fetchEntries(forUserId:operationType:) | Query |
| 16 | fetchEntriesOfTimestamp | fetchEntriesOfTimestamp | Query |
| 17 | checkTimestampExists | checkEntryTimestampExists | Query |
| 18 | fetchEntriesForMonth | fetchEntries(forMonth:userId:) | Query |
| 19 | fetchEntriesForInvalidMonth | fetchEntries(forMonth:) - bad input | Query |
| 20 | fetchEntriesForDay | fetchEntries(forDay:userId:) | Query |
| 21 | fetchEntriesForInvalidDay | fetchEntries(forDay:) - bad input | Query |
| 22 | fetchUnsyncedEntries | fetchUnsyncedEntries | Query |
| 23 | fetchLatestEntry | fetchLatestEntry | Query |
| 24 | fetchLatestEntryEmpty | fetchLatestEntry - no entries | Query |
| 25 | fetchOldestEntry | fetchOldestEntry | Query |
| 26 | fetchOldestEntryEmpty | fetchOldestEntry - no entries | Query |
| 27 | fetchEntryCount | fetchEntryCount | Query |
| 28 | fetchEntryCountEmpty | fetchEntryCount - zero | Query |
| 29 | fetchEntriesLastNDays | fetchEntries(lastNDays:userId:) | Query |
| 30 | fetchEntriesAsDTO | fetchEntriesAsDTO | DTO |
| 31 | fetchEntriesAsDTOWithFilter | fetchEntriesAsDTO(operationType:) | DTO |
| 32 | fetchEntryAsDTOById | fetchEntryAsDTO(byId:) | DTO |
| 33 | fetchEntryAsDTOInvalidId | fetchEntryAsDTO - bad UUID | DTO |
| 34 | fetchLatestEntryAsDTO | fetchLatestEntryAsDTO | DTO |
| 35 | fetchLatestEntryAsDTOWithFilter | fetchLatestEntryAsDTO(operationType:) | DTO |
| 36 | fetchEntryIdentifiers | fetchEntryIdentifiers | Identifiers |
| 37 | fetchEntryIdentifiersWithFilter | fetchEntryIdentifiers(operationType:) | Identifiers |
| 38 | extractDTOFromEntry | extractDTO(from:) | Static |
| 39 | extractDTOFromNil | extractDTO(from: nil) | Static |
| 40 | extractWeightFromEntry | extractWeight(from:) | Static |
| 41 | extractWeightFromNil | extractWeight(from: nil) | Static |
| 42 | extractWeightFromNoScaleEntry | extractWeight - no scaleEntry | Static |
| 43 | syncEntriesBatchInsert | syncEntries(newEntries:) | Sync |
| 44 | syncEntriesPreservesRelationships | syncEntries - relationships | Sync |
| 45 | multipleUsersIsolation | multi-user isolation | Edge Case |
| 46 | deleteDoesNotAffectOtherUsers | cross-user delete safety | Edge Case |
| 47 | fetchEntriesForMonthWrongUser | month query - wrong user | Edge Case |
| 48 | fetchUnsyncedEntriesWrongUser | unsynced query - wrong user | Edge Case |

## Known Gaps
- `fetchEntry(byIdentifier:)` and `fetchEntries(byIdentifiers:)` use `PersistenceController.shared.context` directly - not testable with in-memory container
- `refetchEntriesOnMainActor` uses `PersistenceController.shared.context` directly - same limitation

## Run Command
```bash
xcodebuild test -project iOS/meApp.xcodeproj -scheme meAppTests -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:meAppTests/EntryRepositoryTests
```

## Current Coverage
- Current coverage (as of 2026-03-06): **~92.2%**
