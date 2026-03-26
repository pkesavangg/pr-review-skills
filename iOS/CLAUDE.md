# meApp iOS — Claude Code Context

## Project Overview

iOS application for [Greater Goods](https://greatergoods.com) — a health/weight tracking app (branded as Weight Gurus). Built with **Swift + SwiftUI**, targeting iOS. Integrates Bluetooth scales, Wi-Fi scales, HealthKit, Fitbit, MyFitnessPal, and Firebase.

**Xcode project:** `meApp.xcodeproj`
**Main target:** `meApp`
**Test targets:** `meAppTests` (unit), `meAppUITests` (UI)

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
- **Keychain for auth tokens:** Tokens moved from SwiftData `Account` model to Keychain via `KeychainService` for security compliance. The `Account` model retains `@Transient` token fields but they are no longer the source of truth.
- **Two-tier service registration:** `ServiceRegistry` registers essential services (Logger, Keychain, Account, Bluetooth, etc.) at launch and session-scoped services (Feed, etc.) after login. Services unavailable before their tier registers.
- **`@MainActor` convention for SwiftData:** All SwiftData CRUD runs on main actor. Cross-async-boundary access requires extracting primitives *before* the `await`.
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

- **SwiftData models are NOT `Sendable`:** Never mark `@Model` classes as `Sendable` or pass them across actor boundaries. Extract primitives (e.g., `accountId`, `expiresAt`) before any `await`.
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

- **Branch format:** `{ISSUE-ID}-{slugified-summary}` — e.g. `MA-3318-add-unit-tests-for-entry-api-repository`
- **Commit format:** `MA-XXXX Short description of what was done`
- **Main branch:** `main` — always target PRs here unless told otherwise
- **Jira project:** `MA` on `greatergoods.atlassian.net`

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
| Workflow orchestration | `.claude/orchestra.md` |

---

## Workflow Orchestration

**For every task — whether invoked via a slash command or natural language — consult `.claude/orchestra.md` Section 4 and follow the matching workflow sequence.** Read and execute each referenced skill file (`.claude/skills/*.md`) step-by-step as part of the task, even when no slash command was used.

### Skill Auto-Matching Rules

When the user describes a task in natural language, match it to the appropriate skill(s) using the `description` field in each `.claude/skills/*.md` file. Examples:

| User says | Skill(s) to read and execute |
|-----------|------------------------------|
| "commit the code", "save my changes" | `.claude/skills/commit.md` |
| "add an API call", "wire a new endpoint" | `.claude/skills/add-endpoint.md` → `.claude/skills/wire-service.md` |
| "fix this bug" | `.claude/skills/debug-issue.md` → `.claude/skills/fix-bug.md` |
| "refactor X", "rename Y" | `.claude/skills/refactor.md` |
| "add logging", "instrument this" | `.claude/skills/analytics.md` |
| "run tests", "do tests pass" | `.claude/skills/run-tests.md` |
| "check coverage", "verify tests" | `.claude/skills/verify-tests.md` |
| "review my changes", "self review" | `.claude/skills/self-review.md` |
| "code standards review", "check conventions", "architecture review" | `.claude/skills/review-code-standards.md` |
| "UI review", "check theme usage", "design standards review" | `.claude/skills/review-ui-standards.md` |
| "raise a PR", "open a PR" | `.claude/skills/raise-pr.md` |
| "log time", "log work" | `.claude/skills/log-work.md` |
| "create a branch", "start working on MA-XXXX" | `.claude/skills/create-branch.md` |
| "scaffold this feature", "new feature" | `.claude/skills/feature-slice.md` |
| "generate tests for X", "add unit tests" | `.claude/skills/gen-test-file.md` |
| "generate a mock", "mock this protocol" | `.claude/skills/gen-mock-single.md` |
| "add string for X", "add text for Y" | `.claude/skills/add-strings.md` |
| "wire this screen", "add route for X" | `.claude/skills/wire-navigation.md` |
| "register this in DI", "inject this" | `.claude/skills/wire-service.md` |
| "does this build", "build check" | `.claude/skills/build.md` |
| "graph bug", "chart issue", "fix graph" | `.claude/skills/graph.md` |
| "security review", "check for secrets" | `.claude/skills/review-security.md` |
| "fetch ticket", "show me the ticket" | `.claude/skills/fetch-ticket.md` |
| "create a PRD", "plan this ticket" | `.claude/skills/create-prd.md` |
| "update architecture" | `.claude/skills/update-architecture.md` |
| "update mock", "mock is outdated" | `.claude/skills/update-mock.md` |
| "storage change", "migration" | `.claude/skills/storage-change.md` |
| "concurrency issue", "async bug" | `.claude/skills/swift-concurrency.md` |
| "SwiftData issue" | `.claude/skills/swiftdata.md` |
| "config change", "environment change" | `.claude/skills/config-change.md` |
| "fix lint", "run swiftlint", "lint fix", "swiftlint errors", "clean up lint" | `.claude/skills/swiftlint.md` |
| "add accessibility to X", "make this screen accessible", "VoiceOver support" | `.claude/skills/add-accessibility.md` |
| "add preview for X", "scaffold preview", "create #Preview" | `.claude/skills/add-preview.md` |

When a task spans multiple skills, chain them in the order defined by `.claude/orchestra.md` Section 4. After implementation tasks, always follow the verification checklist in Section 6.

### Orchestration Reference

The full orchestration guide at `.claude/orchestra.md` defines:

- **Workflow sequences** for common task types (full ticket SDLC, new feature, bug fix, refactoring, API endpoint, test coverage, etc.)
- **Review pipeline** — the 5-step self-review sequence to run before every commit
- **Subagent strategy** — when to use each agent (`api-change-planner`, `coverage-gap-finder`, `di-impact-finder`, `gen-mock-batch`)
- **Verification checklist** — pre-commit quality gates
- **Parallelization opportunities** — which skills can run concurrently
- **Error recovery** — what to do when builds fail, tests fail, or plans go sideways
