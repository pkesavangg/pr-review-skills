---
name: update-mock
description: Update an existing mock class to match its protocol's current signature. Use when a protocol has changed (added/removed/renamed methods, changed parameters) and the mock is now out of date. Triggers: "update mock for X", "mock is outdated", "fix mock", "sync mock with protocol", or after a refactoring that changed protocol signatures.
---

Update an existing mock to match the current protocol definition.

The mock or protocol to update is: $ARGUMENTS

## Instructions

### 1 — Locate the Protocol and Mock

If `$ARGUMENTS` is a mock name (e.g. `MockEntryService`):
- Search for the mock: `rg -l "class MockEntryService" meAppTests -g '*.swift'`
- Read the mock file to find which protocol it conforms to
- Search for the protocol: `rg -l "protocol EntryServiceProtocol" meApp -g '*.swift'`

If `$ARGUMENTS` is a protocol name (e.g. `EntryServiceProtocol`):
- Search for the protocol: `rg -l "protocol EntryServiceProtocol" meApp -g '*.swift'`
- Derive mock name: `Mock<NameWithoutProtocolSuffix>` (e.g. `MockEntryService`)
- Search for the mock: `rg -l "class MockEntryService" meAppTests -g '*.swift'`

Read both files in parallel.

---

### 2 — Diff the Signatures

Compare protocol methods vs mock implementations:

**For each protocol method, check:**
- Does the mock have a matching implementation?
- Are parameter names, types, labels, and return types identical?
- Are `async`, `throws`, and `@MainActor` modifiers correct?

**Categorize changes needed:**
- **Added methods** — protocol has methods the mock doesn't implement
- **Removed methods** — mock has methods no longer in the protocol
- **Changed signatures** — method exists in both but parameters/return type differ

---

### 3 — Apply Updates

Follow the project's mock conventions (same as `/gen-mock-single`):

**For added methods:**
- Add the appropriate stub (`Result` for services, `Error?` for repositories)
- Add call tracking: `private(set) var <method>Calls = 0`
- Add argument capture: `private(set) var last<Arg>: <Type>?`
- Add the method implementation matching the mock pattern

**For removed methods:**
- Delete the stub, call tracking, argument capture, and implementation
- Do NOT leave commented-out code

**For changed signatures:**
- Update the stub type if the return type changed
- Update argument captures if parameters changed
- Update the method implementation

**When a protocol swaps `@Model` for a snapshot (or adds snapshot-returning overloads):**
- Add the snapshot-returning stub (e.g. `var fetchAllEntrySnapshotsResult: Result<[EntrySnapshot], Error> = .success([])`) alongside (not replacing) any remaining `@Model`-returning stub. Both may coexist.
- Flip `@Published` backing types (e.g. `@Published var activeAccount: AccountSnapshot?`) to match the protocol.
- Add separate call counters for snapshot vs `@Model` variants (`fetchEntrySnapshotsForMonthCalls` vs `getMonthDetailCalls`; `deleteEntryByIdCalls` vs `deleteEntryCalls`).
- For test files using the mock, reseed via the snapshot-returning result (e.g. `mock.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(...)])`); do not feed `@Model` objects into snapshot-typed publishers.

---

### 4 — Update Test Call Sites

Search for tests that use the updated mock:
```bash
rg -l "Mock<Name>" meAppTests -g '*.swift'
```

For each test file, check if `makeSUT()` or direct mock usage needs updating:
- Constructor parameter changes → update `makeSUT()`
- Removed stub properties → remove corresponding test setup lines
- Renamed methods → update test assertions

---

### 5 — Report

```
Mock updated: Mock<Name>
File: <path>
Methods added: <count and names>
Methods removed: <count and names>
Signatures updated: <count and names>
Test files updated: <count and paths>
```

---

### Follow-Up

- Run `/run-tests` to confirm no existing tests broke after the update
- Run `/verify-tests` if coverage for the affected feature needs to be re-checked
