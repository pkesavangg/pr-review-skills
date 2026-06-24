# Unit Testing Guide (`meAppTests`)

## Purpose
Defines how unit tests are written, organized, run, and reviewed in `meAppTests`.

## Naming Convention
Use behavior-first names:
- `method success: expected behavior`
- `method validation failure: expected guard behavior`
- `method API/network failure: expected fallback behavior`

## Test Structure
Follow Arrange / Act / Assert:
1. Arrange: create SUT + mocks + fixtures
2. Act: call one method under test
3. Assert: verify result and side effects

## Ordering Convention
For each method group:
1. Success path
2. Validation/guard failures
3. Runtime/API/network/persistence failures

## Mock Guidelines
- Prefer protocol-based mocks.
- Avoid `fatalError` in mock behavior.
- Throw explicit test errors for unexpected paths.
- Track call counts and captured input.
- Keep reusable mocks in `meAppTests/Support/Mocks/`.

## DI / Isolation Pattern
`DependencyContainer` is global mutable state.

Use this setup pattern per suite:
1. `TestDependencyContainer.reset()`
2. `TestDependencyContainer.registerBase(...)`
3. Register suite-specific mocks
4. Create SUT
5. Pin dependencies directly on SUT where async/lazy resolve can race

References:
- `meAppTests/Support/DI/TestDependencyContainer.swift`
- `meAppTests/Features/Auth/Stores/LoginStoreTests.swift`

## SwiftData Rules
For persistence tests:
- Use in-memory containers only.
- Never use production persistent paths.
- Build a per-test container.
- Seed data only in Arrange phase.

## AccountSnapshot Pattern
Tests that need an active account should create `AccountSnapshot` (immutable value type), **not** the SwiftData `Account` `@Model`.

**Factory:** `AccountTestFixtures.makeAccountSnapshot(...)` — all 67 properties have sensible defaults.

**Seeding mocks:**
```swift
let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "test@example.com", isActiveAccount: true)
accountService.activeAccount = account
accountService.seedAccounts([account], active: account)
```

**Key differences from Account @Model:**
- All `let` properties — set everything at construction, no post-creation mutation.
- Flat structure — use `account.weightUnit` not `account.weightSettings?.weightUnit`.
- Property mapping: `goalType`, `goalWeight`, `isWeightlessOn`, `dashboardType`, `isHealthKitOn`, etc. are top-level fields.
- `Sendable` — safe to pass across actor boundaries.

**When to use Account @Model:** Only in `AccountRepository` and `AccountMigration` tests that test SwiftData persistence directly. Use `AccountTestFixtures.makeAccountModel()` for those.

**Result types:** Service methods (`logIn`, `signUp`, `createGoal`, `updateProfile`, etc.) return `Void`. Set `mockAccountService.logInResult = .success(())`, not `.success(someAccount)`.

## DeviceSnapshot Pattern
Tests that seed `ScaleService.scales` or pass devices to snapshot-typed service methods should use `DeviceSnapshot`, **not** the SwiftData `Device` `@Model`.

**Factories:**
- `ScaleTestFixtures.makeDevice(...) -> Device` — keep for repository / construction-path tests.
- `BluetoothTestFixtures.makeDevice(...).toSnapshot(isConnected:)` — wrap with `.toSnapshot()` where a snapshot is expected.
- `BtWifiStoreTestFixtures.makeScaleSnapshot(...)` — pre-built snapshot factory for feature tests.

**Seeding mocks:**
```swift
let snapshot = ScaleTestFixtures.makeDevice(id: "scale-1")
    .toSnapshot(isConnected: true, isWifiConfigured: true)
scaleService.scales = [snapshot]
```

**Key differences from Device @Model:**
- `let` properties — no post-construction mutation. Pass `isConnected`, `isWifiConfigured`, `isWeighOnlyModeEnabledByOthers` at construction time via `toSnapshot(...)`.
- Ephemeral runtime state (`isConnected` etc.) lives in `DeviceEphemeralState` inside `ScaleService`, **not** in SwiftData — assertions read from the published snapshot, not by re-fetching via the repository.
- Nested relationship snapshots (`snapshot.bathScale?.scaleType`, `snapshot.r4ScalePreference?.shouldMeasureImpedance`) — same access paths as `Device`.
- `Sendable` — safe across actor boundaries; `DeviceDiscoveryEvent.device` is now truly `Sendable`.

**When to use Device @Model:** `ScaleRepository`, `ScaleMigrationService`, and `BluetoothService`-internal tests that seed `bluetoothScales: [Device]` directly, plus `confirmSmartPair(device:)` / `addNewDevice(_:)` (construction-path APIs).

**Protocol signatures:** Most `BluetoothServiceProtocol` methods now take `broadcastId: String` (e.g. `getDeviceInfo`, `setupWifi`, `updateSetting`, `deleteDevice`). Only `confirmSmartPair` and `addNewDevice` still take `Device`.

## EntrySnapshot Pattern
Tests that seed entry arrays on `HistoryStore`, `ContentViewModel`, or `MockEntryService` should use `EntrySnapshot`, **not** the SwiftData `Entry` `@Model`.

**Factories (`EntryTestFixtures`):**
- `makeEntrySnapshot(...) -> EntrySnapshot` — scale entry with flat field params.
- `makeBpmEntrySnapshot(...) -> EntrySnapshot` — BPM entry; populates `bpmEntry:` + mirrors into `scaleEntry`/`scaleEntryMetric`.
- `makeBabyEntrySnapshot(...) -> EntrySnapshot` — baby entry; populates `babyEntry:`.
- `makeEntry(...) / makeBpmEntry(...) -> Entry` — keep for `EntryService` write-path and repository tests.

**Seeding `MockEntryService`:**
```swift
// ✅ Correct — seed the snapshot-returning results
entryService.fetchEntrySnapshotsForMonthResult = .success([
    EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-15T12:00:00Z")
])
entryService.fetchAllEntrySnapshotsResult = .success(snapshots)

// ❌ Wrong — getMonthDetailResult / getAllEntriesResult are legacy paths
// HistoryStore now calls fetchEntrySnapshots(forMonth:) and fetchAllEntrySnapshots()
```

**Assertion patterns carry over:**
- `store.entries[0].scaleEntry?.weight` reads identically to the old `@Model` tree.
- `snapshot.metricItems` matches `entry.metricItems` — same computed logic.
- `entry.note` (previously `entry.scaleEntry?.note`) — `note` lives on the parent, not the scale child.

**`deleteEntry` split:** `MockEntryService.deleteEntryByIdCalls` increments for the new id-based `deleteEntry(entryId: UUID)` path used by `HistoryStore`. `deleteEntryCalls` still tracks the legacy `deleteEntry(_ entry: Entry)` write-path overload.

**When to use Entry @Model:** `EntryRepository`, `SwiftDataWorker`, `EntryMigration`, and `EntryService`-internal write-path tests. Feature-store tests always use `EntrySnapshot`.

## Async Rules
- Use `@MainActor` for UI/store tests.
- Use deterministic polling helpers (`waitUntil`) instead of arbitrary sleeps.
- Use serialized suites where shared global mutable state cannot be avoided.

## Assertion Rules
Each test should assert:
1. Primary result (return value/thrown error)
2. At least one side effect

For toast/error/alert text assertions:
- Define expected strings in test-local constants.
- Do not assert with production string containers.

## Coverage Minimums
- Service layer (`Data/Services`): **80% min**, **85% preferred** for critical auth/account/sync
- Store/ViewModel layer: **80% min**
- Form/validation layer: **85% min**
- Repository/API adapters: **75% min**

## Run Unit Tests
### Xcode
1. Open `meApp.xcodeproj`
2. Select scheme `meAppTests`
3. Use `Dev` configuration
4. Run `Cmd+U`

### CLI (Simulator)
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Run Single Suite
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountServiceTests
```

## Unit Testing Checklist
- [ ] Added/updated tests for changed logic
- [ ] Arrange/Act/Assert followed
- [ ] Mock behavior explicit
- [ ] Async tests deterministic (no arbitrary sleeps)
- [ ] DI state isolated (`reset`/pinning/serialized as needed)
- [ ] Toast/error/alert assertions use test-local constants
- [ ] SwiftData tests use in-memory container
- [ ] Coverage checked for changed files
