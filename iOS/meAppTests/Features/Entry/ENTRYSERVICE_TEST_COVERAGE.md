# EntryService Test Coverage

## Scope
Unit tests for `EntryService` focus on regression safety for:
- entry create/read/update/delete flows
- DTO conversion and remote sync handoff
- dashboard/day/month aggregation behavior
- export routing based on dashboard type
- no-account, repository failure, and integration failure paths

## Cases Covered
1. `saveNewEntry` success with local save, API sync, notification publish, and summary updates
2. `saveNewEntry` local persistence failure
3. read/query helpers for active-account filtering and DTO reads
4. no-active-account read failure
5. `deleteEntry` delete-sync flow, deletion publish, and summary cleanup
6. `handleEntryUpdated` summary recomputation path
7. `aggregateByDay` averaging and zero-value filtering
8. `aggregateByMonth` grouping and zero-weight filtering
9. `loadDashboardData` DTO aggregation success
10. `loadDashboardData` DTO fetch failure fallback
11. `exportCSV` endpoint selection for dashboard type
12. integration sync failure logging without breaking entry creation

## Dependency Strategy
Tests follow the Account and Feed suite pattern:
- constructor injection for repository, sync-store, and remote API collaborators
- dedicated mocks in `meAppTests/Features/Entry/Mocks`
- shared entry fixtures in `meAppTests/Features/Entry/Fixtures`

## Coverage Target
This suite is intended to keep `EntryService` changes guarded across CRUD, sync, and summary paths so CI can catch entry regressions before release.

## Current Coverage
- `EntryService.swift`: **99.2%** from the latest `EntryServiceTests` coverage run on March 3, 2026.
