# UsersViewModel Coverage Report

Date: March 6, 2026

## Objective
Document verified unit-test coverage for `UsersViewModel` with clear evidence of:
- exact measured coverage percentage,
- total test count and pass status,
- edge/corner/error-path coverage,
- reproducible commands for local and CI validation.

## Scope
- Production file: `iOS/meApp/Features/Settings/Scale/ViewModels/UsersViewModel.swift`
- Test suite file: `iOS/meAppTests/Features/Scale/UsersViewModelTests.swift`
- Test suite name: `UsersViewModelTests`

## Coverage Artifact
- Result bundle path: `/tmp/UsersViewModelCoverage.xcresult`
- Coverage extraction tool: `xcrun xccov`

## Commands Used
1. Run focused tests:
```bash
xcodebuild test \
  -project /Users/lakshmipriya/Work/meApp/meApp/iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008120-001E095C1487A01E' \
  -only-testing:meAppTests/UsersViewModelTests
```

2. Generate coverage bundle:
```bash
rm -rf /tmp/UsersViewModelCoverage.xcresult
xcodebuild test \
  -enableCodeCoverage YES \
  -resultBundlePath /tmp/UsersViewModelCoverage.xcresult \
  -project /Users/lakshmipriya/Work/meApp/meApp/iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008120-001E095C1487A01E' \
  -only-testing:meAppTests/UsersViewModelTests
```

3. Extract file-level coverage:
```bash
xcrun xccov view --report /tmp/UsersViewModelCoverage.xcresult \
  | rg 'UsersViewModel.swift|TOTAL'
```

## Coverage Results (Verified)
- `UsersViewModel.swift`: **96.70% (352/364)**

Threshold check:
- Required minimum: **95.00%**
- Actual: **96.70%**
- Status: **PASS**

## Test Execution Summary
- Suite executed: `UsersViewModelTests`
- Total test cases: **19**
- Passed: **19**
- Failed: **0**

## Test-Case Inventory (19/19)
1. `initWithInitialUsersPrePopulatesCurrentUserAndForm`
2. `otherDeviceUsersListFallsBackToNameComparison`
3. `loadUsersDisconnectedWithoutInitialUsersClearsState`
4. `loadUsersDisconnectedWithInitialUsersRetainsCachedState`
5. `loadUsersSuccessMatchesCurrentUserByToken`
6. `loadUsersSuccessMatchesByDisplayNameWhenTokenDoesNotMatch`
7. `loadUsersFailureWithoutInitialUsersClearsState`
8. `loadUsersFailureWithInitialUsersRetainsState`
9. `saveUsersEmptyNameShowsValidationToast`
10. `saveUsersWithoutCurrentUserShowsErrorToast`
11. `saveUsersScalePreferenceFailureShowsErrorToast`
12. `saveUsersUpdateAccountFailureShowsErrorToast`
13. `saveUsersSuccessUpdatesCurrentUserAndRunsCallback`
14. `showDeleteUserAlertBlocksDeletionWhenBluetoothDisabled`
15. `showDeleteUserAlertDeleteSuccessFlow`
16. `showDeleteUserAlertDeleteFailureFlow`
17. `showDeleteUserAlertMissingBroadcastIdEarlyExit`
18. `showDeleteUserAlertMissingTokenEarlyExit`
19. `formHelperPropertiesExposeValidationAndTouchedState`

## Behavior Coverage Mapping
- Initialization and preloaded state:
  - Uses `initialUsersList` to pre-populate `deviceUsers` and form values.
  - Resolves current user and computes non-current user list.
- User list loading:
  - Connected success path with token-based match.
  - Connected success fallback path using preference display name.
  - Connected failure path with and without fallback initial users.
  - Disconnected guard path with state clearing rules.
- User update flow (`saveUsers`):
  - Input validation for empty name.
  - Missing `currentDeviceUser` path.
  - Scale preference update throw path.
  - Bluetooth `updateAccount` failure path.
  - End-to-end success with UI toast and callback.
- Delete flow (`showDeleteUserAlert` + `deleteUser`):
  - Bluetooth permission/switch blocked path.
  - API success with delayed reload of list.
  - API failure path.
  - Early-exit guard for missing broadcast ID.
  - Early-exit guard for missing user token.
- Form helper behavior:
  - Dirty/touched state and validation error exposure.

## Edge, Nook, and Corner Cases Covered
- Token missing and fallback to case-insensitive name matching.
- Initial users present vs absent during disconnected and failure paths.
- Empty input and nil current-user protection.
- Permission denial before destructive action.
- Missing scale metadata required by delete APIs.
- Async operation completion correctness (loader/toast/callback ordering).

## CI/Release Safety Impact
- Detects regressions in profile list correctness before release.
- Validates user-management actions under real failure conditions.
- Protects critical state transitions (load/update/delete) with deterministic tests.

## Conclusion
`UsersViewModel` unit coverage is above the requested minimum and includes success, failure, and edge-condition paths required for safe profile management on connected scales.
