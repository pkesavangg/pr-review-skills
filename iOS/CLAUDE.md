# meApp iOS — Claude Code Context

## Project Overview

iOS application for [Greater Goods](https://greatergoods.com) — a health/weight tracking app (branded as Weight Gurus). Built with **Swift + SwiftUI**, targeting iOS. Integrates Bluetooth scales, Wi-Fi scales, HealthKit, Fitbit, MyFitnessPal, and Firebase.

**Xcode project:** `meApp.xcodeproj`
**Main target:** `meApp`
**Test targets:** `meAppTests` (unit), `meAppUITests` (UI)

---

## Project Context

- **Repo / org:** gg-engineering monorepo (`github.com/gg-engineering/meApp`), post gg-engineering migration.
- **Jira:** Active work tracks in the **MOB** project (GGT-Mobile, board 1088) on `greatergoods.atlassian.net`. Branch/commit prefix `MOB-XXXX` (legacy `MA-XXXX` is deprecated).
- **Branch model:** `main` = MA / 5.0.x release line (5.0.x hotfixes only) · **`develop` = active integration branch and current default base/target** — Phase 2 (`phase2-dev`) is merged into it. Start new branches from `develop`.
- See the monorepo root [`/CLAUDE.md`](../CLAUDE.md) for the cross-platform overview.

---

## Phase 2 — Me.Health 2.0 ("Mega App")

The shipped app (v5.0.x) is weight-only. **Phase 2 turns it into a multi-product app: Weight + Blood Pressure (Balance) + Baby**, against a merged wgServer3 `/v3` API.

- **Account product model:** `productTypes` array (`weight` / `blood_pressure` / `baby`) + `measurementUnits` (`metric` / `imperialLbOz` / `imperialLbDecimal`). `gender`/`dob`/`height` are now **conditionally** required (only for weight/BP), which affects signup forms.
- **In this codebase already:** `EntrySnapshot` carries `BPMEntrySnapshot` + `BabyEntrySnapshot`; unified `/v3/entries/`, `/v3/paired-device/`, baby profile/permissions work landed via `MOB-382…386`.
- **Unified entries:** one `POST /v3/entries/` (raw array, `category` routes weight/bp/baby) and one `GET /v3/entries/` with **sync** (`?start`) and **cursor** (`?cursor`+`limit`) modes; baby `entryType`s include feeding/sleep/diaper/length/snapshot.
- **Backward compatibility is mandatory** — legacy weight endpoints remain for old apps.
- **Skills:** `phase2-context` (full API cheat sheet, auto-triggers) · `phase2-design-system` (Figma 2.0 file/nodes + token mapping) · `unified-entries` / `paired-device` / `baby-profile` / `product-selection` (technical patterns).

---

## Features

| Feature | Description |
|---------|-------------|
| `Auth` | Login, signup, and landing flows |
| `Dashboard` | Home screen — weight trends, graphs, and daily summaries |
| `Entry` | Manual weight and body metric entry form |
| `History` | Weight history browser with monthly and historical views |
| `ScaleSetup` | Scale pairing flows — Bluetooth, Wi-Fi, Hybrid, A6, AppSync |
| `AppSync` | Body composition scanning and data entry via AppSync scales |
| `Feed` | Social/activity feed |
| `Settings` | User preferences, account management, and third-party integrations |
| `Common` | Shared UI components, utilities, forms, styles, and extensions |

### Bottom Tab Navigation

Five tabs, rendered in order:

| Tab | Screen | Notes |
|-----|--------|-------|
| `.dash` | `DashboardScreen` | Default home |
| `.entry` | `ManualEntryScreen` | Quick entry |
| `.history` | `HistoryListScreen` | Weight log |
| `.settings` | `SettingsScreen` | Preferences & integrations |
| `.appsync` | `AppSyncTabScreen` | Shown only when an AppSync scale is paired |

---

## Architecture

Clean Architecture layered as follows:

```
Features/          # Feature modules (Auth, Dashboard, Entry, Feed, Settings, …)
  <Feature>/
    Stores/        # @MainActor ObservableObject — state + event handling
    Views/         # SwiftUI views (excluded from unit test coverage)
    Forms/         # Reactive form validators
    Models/        # Feature-local models/enums

Data/
  API/             # Concrete API repository implementations (e.g. AccountRepositoryAPI)
  Services/        # Concrete service implementations (e.g. AccountService, EntryService)
  Storage/
    DB/            # SwiftData local repositories (e.g. AccountRepository)
    Persistence/   # SwiftData model containers

Domain/
  Models/          # Shared models: API DTOs, DB models, domain models
    API/           # Codable response/request types
    DB/            # SwiftData @Model classes
    Domain/        # Pure domain objects
  Repositories/    # Protocol definitions for all repositories
  Services/        # Protocol definitions for all services

Core/
  Network/         # HTTPClient, HTTPClientProtocol, Endpoint enum, NetworkMonitor
  DI/              # DependencyContainer + @Injector property wrapper
  Navigation/      # App routing
  Config/          # AppEnvironment (Dev/Production), AppConstants
  Storage/         # SwiftData stack setup
  Application/     # AppDelegate, SceneDelegate
  Shared/          # Cross-cutting utilities, extensions, AppContext

Theme/             # Design tokens, typography
```

---

## Key Decisions

- **Hybrid UIKit + SwiftUI:** `@main` App defers to `SceneDelegate` for window setup, allowing manual DI and lifecycle control before any SwiftUI view renders.
- **Keychain for auth tokens:** Tokens moved from SwiftData `Account` model to Keychain via `KeychainService` for security compliance. Keychain is the source of truth. The `Account` model keeps the token fields as **persisted** columns (not `@Transient`) so the one-time `migrateTokensToKeychainIfNeeded()` pass can read values an upgrading 5.0.3 store still holds; every save path clears them first (`clearTokenFieldsBeforeSave`), so a token is never re-persisted.
- **Two-tier service registration:** `ServiceRegistry` registers essential services (Logger, Keychain, Account, Bluetooth, etc.) at launch and session-scoped services (Feed, etc.) after login. Services unavailable before their tier registers.
- **`@MainActor` convention for SwiftData:** All SwiftData CRUD runs on main actor. Cross-async-boundary access requires extracting primitives *before* the `await`.
- **Snapshots, not `@Model`, cross the service boundary (PROJECT RULE):** `AccountService`, `ScaleService`, `EntryService`, and `HistoryStore` publish `AccountSnapshot` / `DeviceSnapshot` / `EntrySnapshot` (flat `Sendable` structs) — the SwiftData `@Model` never leaves the owning service. This is what makes feature code safe off the main actor. **Enforced by:** SwiftLint custom rules `no_published_swiftdata_model` and `no_unchecked_sendable_with_model` (fire on every Swift edit via the PostToolUse hook, Xcode, and CI lint), plus the audit script `./scripts/check-snapshot-boundary.sh` (exit 1 on violation, runnable locally and in CI).
- **Physical device testing only:** Unit tests must run on a connected device, never a simulator. This is a hard project requirement.

---

## Key Domain Models

### SwiftData (`Domain/Models/DB/`)

| Model | Purpose |
|-------|---------|
| `Account` | User account record |
| `Entry` | Individual weight/metric entry |
| `DailyWeightSummary` | Aggregated daily stats |
| `Device`, `DeviceMetaData` | Paired scale metadata |
| `BathScale`, `BathScaleEntry`, `BathScaleMetric` | Scale measurement data |
| `DashboardSettings`, `GoalSettings`, `StreaksSettings` | User preferences |
| `IntegrationSettings` | Third-party integration config |
| `NotificationSettings` | Notification preferences |
| `R4ScalePreference` | R4 scale-specific settings |

### Domain Models (`Domain/Models/Domain/`)

`Profile`, `Goal`, `BodyComp`, `Tokens` (auth) · `Entry`, `BodyMetric`, `HistoryMonth`, `ProgressSummary` · `BTScaleData`, `ScaleEnums`, `WifiConnectionStatus` · `FeedItem`, `FeedAction` · `Integrations`, `IntegrationInfo` · `Streak`, `Progress`

### Value-type Snapshots (`Domain/Models/Domain/...`)

Flat `Equatable, Sendable` structs that services publish instead of the SwiftData `@Model`. Safe to cross actor boundaries, safe as Combine publisher payloads.

| Snapshot | Mirrors | Published by |
|----------|---------|--------------|
| `AccountSnapshot` (+ flattened child settings) | `Account` | `AccountService.activeAccount` / `allAccounts` |
| `DeviceSnapshot` (+ `BathScaleSnapshot`, `R4ScalePreferenceSnapshot`, `DeviceMetaDataSnapshot`) | `Device` | `ScaleService.scales` |
| `EntrySnapshot` (+ `BathScaleEntrySnapshot`, `BathScaleMetricSnapshot`, `BPMEntrySnapshot`, `BabyEntrySnapshot`) | `Entry` | `HistoryStore.entries`, `ContentViewModel.entries`, `EntryService.fetchEntrySnapshots*` |
| `DeviceEphemeralState` | in-memory runtime state (connection / Wi-Fi status) | merged into `DeviceSnapshot` by `ScaleService` |

Conversion: `model.toSnapshot()` on the main actor, right before any `await`. Snapshots are immutable (`let` fields) — construct with the desired values, don't mutate.

---

## Services

Registered via `ServiceRegistry` at launch (essential) or after login (session-scoped):

| Service | Responsibility |
|---------|---------------|
| `AccountService` | User account management |
| `EntryService` | Weight entry CRUD |
| `ScaleService` | Paired scale management |
| `BluetoothService` | BLE scale discovery and connection |
| `WifiScaleService` | Wi-Fi scale connectivity |
| `HealthKitService` | Apple HealthKit sync |
| `IntegrationsService` | Fitbit / MyFitnessPal integrations |
| `FeedService` | Social feed data |
| `GoalAlertService` | Goal notifications and prompts |
| `AccountFlagService` | Per-account feature flags |
| `NotificationHelperService` | Toast / alert / modal UI |
| `PushNotificationService` | Firebase push notifications |
| `AppReviewService` | In-app review prompts |
| `PermissionsService` | App permission management |
| `KeychainService` | Secure token and credential storage |
| `KvStorageService` | Local key-value persistence |
| `LoggerService` | App logging |

---

## Dependency Injection

**Production:** `DependencyContainer.shared` singleton + `@Injector` property wrapper.

```swift
@Injector var accountService: AccountServiceProtocol
```

**Testing:** Constructor injection is preferred for `RepositoryAPI` and `Service` classes.

```swift
init(httpClient: HTTPClientProtocol = HTTPClient.shared) { ... }
```

For store/service tests, use `TestDependencyContainer`:
```swift
TestDependencyContainer.reset()
DependencyContainer.shared.register(mockService as ServiceProtocol)
```

---

## Networking

- All API calls go through `HTTPClientProtocol` (`get` / `send` methods).
- Endpoints defined in `Domain/Models/API/EndPoints.swift` as `enum Endpoint`.
- Two environments (`Dev` / `Production`) controlled by `APP_ENV` in `Info.plist`.
- Auth tokens are stored in **Keychain** (not SwiftData/UserDefaults) via `KeychainService`.

When adding a new API call:
1. Add the case to `enum Endpoint` with its `urlRequest`.
2. Add the method to the relevant `*RepositoryAPIProtocol`.
3. Implement in the concrete `*RepositoryAPI` class with injected `httpClient`.

---

## Environments & Build Configs

| Config | `APP_ENV` | API scheme |
|--------|-----------|------------|
| `Dev` | `DEV` | `http://` |
| `Production` | `PRODUCTION` | `https://` |

API base URL is read from `API_BASE_URL` in `Info.plist` via `AppEnvironment.apiBaseURL`.

---

## External Dependencies (SPM)

- **Firebase** (Core, Messaging) — push notifications
- **GGBluetoothSwiftPackage** — BLE scale communication
- **ggHealthKitPackage** — HealthKit integration
- **ggInAppMessagingPackage** — in-app messaging
- **AppSyncPackage** — app sync
- **gWifiScalePackage** — Wi-Fi scale support

---

## Gotchas & Pitfalls

- **SwiftData models are NOT `Sendable`:** Never mark `@Model` classes as `Sendable` or pass them across actor boundaries. Feature code should read `AccountSnapshot` / `DeviceSnapshot` / `EntrySnapshot` — the `@Model` stays inside the owning service. Production `EXC_BAD_ACCESS` crashes in v5.0 were caused by reading `@Model` fields off-actor; the snapshot pattern makes that crash class structurally impossible.
- **DI double-registration required:** `DependencyContainer` must register both the concrete instance AND the protocol cast. Missing either causes `fatalError` at runtime:
  ```swift
  DependencyContainer.shared.register(accountService)
  DependencyContainer.shared.register(accountService as AccountServiceProtocol)
  ```
- **`@Injector` fatal on missing dependency:** If a dependency isn't registered, `@Injector` crashes with `fatalError`. Check `ServiceRegistry` registration order and tier.
- **Tokens are in Keychain, not `Account`:** Auth tokens migrated from SwiftData to Keychain. Migration flag: `tokensMigratedToKeychain`. Don't read tokens from the `Account` model.
- **No nested vertical ScrollViews:** SwiftUI vertical scroll nesting breaks gesture handling. Let the parent handle scrolling.
- **`DeviceDiscoveryEvent` is `@unchecked Sendable`:** Safe only because creation and consumption both happen on `@MainActor`. Do NOT send across actor boundaries.
- **Scale sync must filter by `accountId`:** When matching unsynced devices, always filter by current account to avoid cross-account conflicts (same MAC/SKU on different accounts).
- **`AccountMigrationService` is intentionally large:** All one-time migrations live in a single file for discoverability and auditability. Don't split it.

---

## Unit Tests (`meAppTests`)

**Framework:** Swift Testing (`@Test`, `@Suite(.serialized)`, `#expect`, `Issue.record`).

**File placement:** `meAppTests/Features/<Feature>/` — files are auto-included via `PBXFileSystemSynchronizedRootGroup`, no `.pbxproj` editing needed.

**All tests must run on a connected physical device — never use a simulator.**

```bash
# Find device ID
xcodebuild -project meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator

# Run tests
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

### Coverage minimums

| Layer | Min |
|-------|-----|
| `Data/Services` | 80% (85% for auth/account/sync) |
| Stores / ViewModels | 80% |
| Forms / validation | 85% |
| `Data/API` repository adapters | 75% |

UI layer (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) is excluded from coverage.

For detailed test patterns, mock usage, and assertion examples → `meAppTests/docs/UNIT_TESTING.md`.

---

## Git & Branching

- **Branch format:** `{ISSUE-ID}-{slugified-summary}` — e.g. `MOB-1006-clean-up-ai-context`
- **Commit format:** `MOB-XXXX Short description of what was done`
- **Main branch:** `main` (MA / 5.0.x release line) — always target PRs here unless told otherwise. `develop` is the active integration branch.
- **Jira project:** `MOB` (GGT-Mobile, board 1088) on `greatergoods.atlassian.net`. The older `MA` prefix is legacy.

---

## Useful File Locations

| What | Where |
|------|-------|
| All API endpoints | `meApp/Domain/Models/API/EndPoints.swift` |
| HTTP client protocol | `meApp/Core/Network/HTTPClientProtocol.swift` |
| DI container | `meApp/Core/DI/DependencyContainer.swift` |
| Service registration | `meApp/Core/Services/ServiceRegistry.swift` |
| Bottom tab definition | `meApp/Features/Common/Enums/BottomTab.swift` |
| App environments | `meApp/Core/Config/Environment.swift` |
| App constants | `meApp/Core/Config/AppConstants.swift` |
| Test DI setup | `meAppTests/Support/DI/TestDependencyContainer.swift` |
| Mock HTTP client | `meAppTests/Support/Mocks/Network/MockHTTPClient.swift` |
| Unit test guide | `meAppTests/docs/UNIT_TESTING.md` |
| Coverage guide | `docs/COVERAGE_REPORTING.md` |
| Architecture overview | `architecture.md` |
| Theme system guide | `.claude/skills/theme-guide/SKILL.md` |
| Notification layer guide | `.claude/skills/notification-guide/SKILL.md` |
| API call patterns | `.claude/skills/api-guide/SKILL.md` |
| Form validation guide | `.claude/skills/form-guide/SKILL.md` |
| Logging system guide | `.claude/skills/logging-guide/SKILL.md` |
| Workflow orchestration | `.claude/orchestra.md` |
| Skill catalog (both platforms) | repo-root `.claude/skills/README.md` |

---

## Workflow Orchestration

Skills are **auto-discovered and auto-triggered** — each one is its own `SKILL.md` directory and Claude Code matches the user's request against the skill's `description` field automatically. There is no longer a manual "read the skill file by path" step.

- **Generic skills** (git / Jira / PR / Figma) live at the **repo root** `/.claude/skills/` so they trigger from anywhere in the monorepo: `commit`, `create-branch`, `create-prd`, `fetch-ticket`, `pr-description`, `gen-pr-description-template`, `raise-pr`, `log-work`, `read-figma`, `read-jira-images`, `phase2-context`.
- **iOS-specific skills** live here under `iOS/.claude/skills/` and trigger when working on iOS files (Swift / SwiftUI / Xcode / SwiftData / tests). If an iOS skill must be reachable from the monorepo root regardless of working directory, symlink it into the root `.claude/skills/` (see the skills README).
- The full catalog with trigger phrases and the Android↔iOS taxonomy map is in the repo-root `.claude/skills/README.md`.

**For multi-step tasks, consult `.claude/orchestra.md` Section 4** and follow the matching workflow sequence — it chains skills in order (e.g. fetch-ticket → create-branch → create-prd → … → verify-tests → self-review → commit → raise-pr). After any implementation task, run the verification checklist in Section 6.

### Orchestration Reference

The full orchestration guide at `.claude/orchestra.md` defines:

- **Workflow sequences** for common task types (full ticket SDLC, new feature, bug fix, refactoring, API endpoint, test coverage, etc.)
- **Review pipeline** — the 5-step self-review sequence to run before every commit
- **Subagent strategy** — when to use each agent (`api-change-planner`, `coverage-gap-finder`, `di-impact-finder`, `gen-mock-batch`)
- **Verification checklist** — pre-commit quality gates
- **Parallelization opportunities** — which skills can run concurrently
- **Error recovery** — what to do when builds fail, tests fail, or plans go sideways

---

## Post-Change Guard

The `post-change-guard` is a mid-session quality fix-and-check pass. Run it after finishing a batch of implementation work and **before** running `/self-review`.

**What it does (in parallel):**
- Auto-fixes SwiftLint violations (lint + HIPAA compliance + force ops)
- Auto-fixes accessibility issues (labels, identifiers, Dynamic Type)
- Reports security findings — no auto-fix (requires human review)
- Reports code standards deviations — no auto-fix (requires context)
- Applies Swift concurrency pattern corrections (after parallel block)
- Triggers full build check when vital infrastructure files are changed

**When to run:**
- After implementing a feature or fixing a bug — before `/self-review`
- When the hook reminds you ("N Swift files edited this session")
- Any time you want a fast mid-session quality pass

**When NOT to use:**
- Instead of `/self-review` — guard is "fix as you go"; self-review is the final commit gate
- On a single file — the per-file SwiftLint hook already handles that
- Before `/verify-tests` — tests are separate; the guard does not run tests

**Workflow position:**
```
[Implementation complete] → /post-change-guard → /self-review → /commit
```

**Vital files that trigger build check:**
`Core/DI/`, `Core/Services/ServiceRegistry.swift`, `Domain/Repositories/*Protocol.swift`, `Domain/Services/*Protocol.swift`, `Data/Services/`, `Data/API/`, `Core/Network/`, `Domain/Models/DB/`, `meApp.xcodeproj/`
