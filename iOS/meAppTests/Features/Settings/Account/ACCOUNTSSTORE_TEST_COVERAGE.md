# AccountsStore Test Coverage Guide

## Purpose
This document summarizes how `AccountsStore` tests are organized, how many suites/tests exist, and the current coverage baseline for the main store file.

The goal is to keep account-management behavior stable when account flow logic changes:
- account list mapping and ordering
- login/signup entry points
- switching active account
- removing users
- offline and service-failure handling

## Test Files

### Root Suite
- `meAppTests/Features/Settings/Account/AccountsStoreTests.swift`

### Nested Suites
- `meAppTests/Features/Settings/Account/TestSuits/AccountsStoreListMappingTests.swift`
- `meAppTests/Features/Settings/Account/TestSuits/AccountsStoreEntryPointsTests.swift`
- `meAppTests/Features/Settings/Account/TestSuits/AccountsStoreSwitchingTests.swift`
- `meAppTests/Features/Settings/Account/TestSuits/AccountsStoreRemoveUserTests.swift`

### Shared Fixtures
- `meAppTests/Features/Settings/Account/Fixtures/AccountsStoreTestFixtures.swift`

## Suite and Test Count Summary
Coverage snapshot date: **March 5, 2026**

- Total suites: **5** (1 root + 4 nested)
- Total test cases: **26**

Breakdown by nested suite:
- `List Mapping And Ordering`: **5**
- `Login Signup Entry Points`: **6**
- `Switch Active Account`: **8**
- `Remove User And Errors`: **7**

## Suite Responsibilities

### List Mapping And Ordering (5 tests)
Focus:
- account-list filtering and ordering by `lastActiveTime`
- logged-out exclusion behavior
- `UserItemInfo` mapping (`name`, `isSelected`, `isExpired`, `canShowSelection`)
- publisher-driven updates for `activeAccount`, `accounts`, and `userItems`
- fallback ordering when date parsing fails or date is missing

### Login Signup Entry Points (6 tests)
Focus:
- login CTA open-state and email-prefill behavior
- max-user limit gating for login CTA
- expired-user login bypass behavior at max-user limit
- signup CTA open-state and max-user gating behavior

### Switch Active Account (8 tests)
Focus:
- guards for unknown account ID and switching to current active account
- happy-path switching flow (loader + success toast + active-account update)
- toast-name fallback behavior when first name is empty
- error handling for `noInternet`, `timeout`, and generic failures
- max-user edge case: switching remains allowed even when max users are already reached

### Remove User And Errors (7 tests)
Focus:
- remove-user confirmation alert structure and button actions
- unknown-account guard path
- offline guard path
- happy-path removal flow (logout + loader lifecycle)
- cancel path (no logout)
- service failures (`noInternet` and generic) with correct toast behavior

## How to Run

Run only Accounts Store tests:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/AccountsStoreTests
```

Coverage export command used:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/AccountsStoreTests -enableCodeCoverage YES -resultBundlePath /tmp/AccountsStoreTests.xcresult
xcrun xccov view --report /tmp/AccountsStoreTests.xcresult | rg 'Features/Settings/Account/Stores/AccountsStore.swift'
```

## Bottom-Line Coverage
- `AccountsStore.swift`: **95.72% (313/327)**
