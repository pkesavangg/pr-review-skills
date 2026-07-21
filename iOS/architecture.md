# meApp iOS — Architecture Overview

> This document is a living reference designed to give agents and contributors a rapid, comprehensive understanding of the iOS codebase. Update it as the architecture evolves.

---

## 1. Project Structure

High-level directory layout under `iOS/meApp/`, categorised by architectural layer:

```
iOS/meApp/
├── Core/          # DI, networking, navigation, app lifecycle, config, extensions, utilities
├── Data/          # Concrete implementations: API repos, services, SwiftData local repos
├── Domain/        # Protocols, models (API / DB / Domain), repository interfaces
├── Features/      # Self-contained feature modules
├── Theme/         # Design tokens — colors, typography, spacing
└── Resources/     # Assets, fonts, Info.plist, GoogleService-Info.plist
```

| Folder | Purpose |
|--------|---------|
| `Core/` | Shared infrastructure — `HTTPClient`, `DependencyContainer`, `Router`, `AppDelegate`, extensions, utilities |
| `Data/` | Implementations of Domain protocols — never imported directly by Features |
| `Domain/` | Source of truth for interfaces and models; no UIKit/SwiftUI imports |
| `Features/` | UI modules, one folder per feature; each owns its store, views, and navigation |
| `Theme/` | Centralized design system — all colors and fonts come from here |
| `Resources/` | Static assets and app configuration plists |

Each feature follows a consistent internal structure:

```
Features/<Feature>/
├── Routes/         # Navigation enum conforming to Routable
├── Stores/         # @MainActor ObservableObject — state and event handling
├── Views/
│   ├── Screens/    # Root screen views (navigation entry points)
│   └── Components/ # Feature-local reusable UI
├── Forms/          # Reactive form validators
├── Models/         # Feature-local models and enums
├── Strings/        # PascalCase string constants
└── Enums/          # Feature-local enums
```

> Not all subfolders are required in every feature — add only what the feature needs. For example, a read-only feature may have no `Forms/`; a tab-level feature may have no `Routes/`.

---

## 2. High-Level System Diagram

```
[User]
  │
  ▼
[meApp iOS]
  │
  ├── [REST API (Dev / Production)]        ← HTTPClient via EndPoints.swift
  │
  ├── [Firebase]                           ← Push notifications, in-app messaging
  │
  ├── [Apple HealthKit]                    ← Health data sync
  │
  ├── [Fitbit / MyFitnessPal APIs]         ← Third-party fitness integrations
  │
  ├── [BLE Scales]                         ← GGBluetoothSwiftPackage (CoreBluetooth)
  │
  └── [Wi-Fi Scales]                       ← gWifiScalePackage
```

**Internal data flow:**

```
User Action
  → View calls Store method
    → Store calls Service or Repository
      → Business logic / I/O executes (async/await)
        → Result returned to Store
          → Store updates @Published state
            → SwiftUI re-renders View
```

---

## 3. Core Components

### 3.1. iOS Application

**Description:** Single iOS app (Weight Gurus / meApp) providing weight and body composition tracking. Users connect Bluetooth or Wi-Fi scales, log manual entries, view trends, and sync with health platforms.

**Technologies:** Swift 5.9+, SwiftUI, UIKit (hybrid — `SceneDelegate` for manual DI and lifecycle), SwiftData, Combine, async/await

**Architecture style:** Clean Architecture with MVVM + Store pattern
- `Domain` → `Data` → `Features`, with `Core` as shared infrastructure
- **Stores** own UI state and orchestrate service calls
- **Services** own business logic and domain rules
- **Repositories** own I/O — networking and persistence only

### 3.2. Feature Modules

| Feature | Responsibility |
|---------|---------------|
| `Auth` | Login, signup, and landing flows |
| `Dashboard` | Home screen — weight trends, graphs, and daily summaries |
| `Entry` | Manual weight and body metric entry |
| `History` | Weight history browser with monthly views |
| `ScaleSetup` | Scale pairing — Bluetooth, Wi-Fi, Hybrid, A6, AppSync |
| `AppSync` | Body composition scanning via AppSync scales |
| `Feed` | Social / activity feed |
| `Settings` | User preferences, account management, third-party integrations |
| `Common` | Shared components, utilities, forms, extensions |

**Bottom tab navigation:**

| Tab | Screen | Visibility |
|-----|--------|------------|
| `.dash` | `DashboardScreen` | Always |
| `.entry` | `ManualEntryScreen` | Always |
| `.history` | `HistoryListScreen` | Always |
| `.settings` | `SettingsScreen` | Always |
| `.appsync` | `AppSyncTabScreen` | Only when AppSync scale is paired |

### 3.3. Service Layer

Registered via `ServiceRegistry` at launch (essential) or post-login (session-scoped):

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
| `MetricKitService` | Subscribes to MetricKit at launch; forwards OS-collected performance metrics and hang/CPU/crash/disk-write diagnostics to `LoggerService` for field triage |

---

## 4. Data Stores

### 4.1. SwiftData (Local Database)

**Type:** SwiftData (`@Model` + `ModelContainer`)

**Purpose:** On-device persistence of user data, scale data, preferences, and cached entries.

**Key models:**

| Model | Stores |
|-------|--------|
| `Account` | User account record |
| `Entry` | Individual weight/metric entry |
| `DailyWeightSummary` | Aggregated daily stats |
| `Device`, `DeviceMetaData` | Paired scale metadata |
| `BathScale`, `BathScaleEntry`, `BathScaleMetric` | Scale measurement data |
| `DashboardSettings`, `GoalSettings`, `StreaksSettings` | User preferences |
| `IntegrationSettings` | Third-party integration config |
| `NotificationSettings` | Notification preferences |
| `R4ScalePreference` | R4 scale-specific settings |

**Rules:** All CRUD must run on the main actor. Stack managed via `PersistenceController` singleton. Use in-memory containers for all tests. **The `@Model` must not leave the owning service** — feature code reads value-type snapshots instead (see §4.1a).

### 4.1a. Value-type Snapshots

**Purpose:** Publish `Sendable`, flat-field, `Equatable` structs to feature code instead of the SwiftData `@Model`. Prevents off-actor `@Model` reads (a production `EXC_BAD_ACCESS` crash class) and makes `@Published` fire correctly on value changes.

| Snapshot | Mirrors | Published by |
|----------|---------|--------------|
| `AccountSnapshot` | `Account` + all 8 child settings models (flattened) | `AccountService.activeAccount` / `allAccounts` |
| `DeviceSnapshot` + `BathScaleSnapshot` + `R4ScalePreferenceSnapshot` + `DeviceMetaDataSnapshot` | `Device` and its child `@Model`s (nested) | `ScaleService.scales` |
| `EntrySnapshot` + `BathScaleEntrySnapshot` + `BathScaleMetricSnapshot` + `BPMEntrySnapshot` + `BabyEntrySnapshot` | `Entry` and its four child `@Model` relationships (nested) | `HistoryStore.entries`, `ContentViewModel.entries`, `EntryService.fetchEntrySnapshots*` |
| `DeviceEphemeralState` | in-memory connection state (`isConnected`, `isWifiConfigured`, `isWeighOnlyModeEnabledByOthers`) | merged into `DeviceSnapshot` at publish time |

**Write path** (pairing flows, sync, DTO construction) still uses the `@Model` internally. Only the read surface flips to snapshots. Conversion is a single `model.toSnapshot()` call on the main actor before any `await`.

See `docs/account-snapshot-implementation.md`, `docs/DEVICESNAPSHOT_IMPLEMENTATION.md`, `docs/ENTRYSNAPSHOT_IMPLEMENTATION.md` for the detailed migration playbooks.

### 4.2. Keychain

**Type:** iOS Keychain (via `KeychainService`)

**Purpose:** Secure storage of auth tokens and credentials.

**Rule:** Auth tokens must never be stored in SwiftData or UserDefaults.

### 4.3. Key-Value Storage

**Type:** `KvStorageService` (wraps `UserDefaults` or equivalent)

**Purpose:** Lightweight, non-sensitive app state and feature flag persistence.

---

## 5. External Integrations / APIs

| Service | Purpose | Integration Method |
|---------|---------|-------------------|
| **Weight Gurus REST API** | Core backend — accounts, entries, sync | REST via `HTTPClient` + `enum Endpoint` |
| **Firebase (Core, Messaging)** | Push notifications, in-app messaging | Firebase SDK (SPM) |
| **Apple HealthKit** | Health data read/write | `ggHealthKitPackage` (SPM) |
| **Fitbit API** | Fitness data integration | REST via `IntegrationsService` |
| **MyFitnessPal API** | Nutrition/fitness data integration | REST via `IntegrationsService` |
| **BLE Scales** | Bluetooth scale communication | `GGBluetoothSwiftPackage` (SPM) |
| **Wi-Fi Scales** | Wi-Fi scale communication | `gWifiScalePackage` (SPM) |
| **AppSync Scales** | Body composition scanning | `AppSyncPackage` (SPM) |

---

## 6. Deployment & Infrastructure

**Platform:** iOS (App Store distribution)

**CI/CD:** CircleCI — macOS M1, Xcode 16.0.0

**Build configurations:**

| Config | `APP_ENV` | API scheme |
|--------|-----------|------------|
| `Dev` | `DEV` | `http://` |
| `Production` | `PRODUCTION` | `https://` |

**Build command (CLI):**
```bash
cd iOS && xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'generic/platform=iOS' \
  -configuration Dev \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

**Monitoring / Logging:** `LoggerService` (internal) + Firebase Crashlytics (via Firebase Core)

---

## 7. Security Considerations

**Authentication:** Token-based auth via Weight Gurus REST API. Tokens stored exclusively in Keychain via `KeychainService`.

**Authorization:** Per-account feature flags managed by `AccountFlagService`.

**Data in transit:** HTTPS enforced in Production config (`https://` scheme). Dev uses `http://` on internal networks only.

**Data at rest:** Auth credentials in iOS Keychain (hardware-backed encryption on device). User data in SwiftData (protected by iOS data protection).

**Key practices:**
- No auth tokens in SwiftData or UserDefaults
- `@Injector` resolves dependencies lazily — no global mutable state outside `DependencyContainer`
- API repositories contain no business logic — surface area for security issues is minimal

---

## 8. Development & Testing Environment

**Requirements:** Xcode 16+, iOS 16+ deployment target, Swift 5.9+

**Run unit tests (always on a physical device — never simulator):**
```bash
# Find device ID
xcodebuild -project iOS/meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator

# Run tests
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

**Testing framework:** Swift Testing (`@Test`, `@Suite(.serialized)`, `#expect`, `Issue.record`)

**Coverage minimums:**

| Layer | Min |
|-------|-----|
| `Data/Services` | 80% (85% for auth/account/sync) |
| Stores / ViewModels | 80% |
| Forms / validation | 85% |
| `Data/API` repository adapters | 75% |

UI layer (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) is excluded from coverage.

**Detailed test patterns:** `meAppTests/docs/UNIT_TESTING.md`
**Coverage reports:** `iOS/meAppTests/Reports/` (via `./iOS/scripts/run_tests_with_coverage.sh`)

---

## 9. Future Considerations / Roadmap

- Protocol-based dependency graph allows features to be extracted into Swift packages without rearchitecting
- `ServiceRegistry` phased startup model supports new session-scoped services without touching launch code
- Feature-first organization enables independent team ownership with minimal cross-feature coupling
- Router pattern extends to deep links and multi-scene without structural changes
- In-memory SwiftData containers keep persistence independently verifiable as the schema grows

---

## 10. Project Identification

| Field | Value |
|-------|-------|
| **Project Name** | meApp (Weight Gurus) |
| **Company** | Greater Goods / DMD Brands |
| **Xcode Project** | `iOS/meApp.xcodeproj` |
| **Main Target** | `meApp` |
| **Bundle ID** | `com.dmdbrands.gurus.weight` |
| **Jira Project** | `MA` on `greatergoods.atlassian.net` |
| **Main Branch** | `main` |
| **Date of Last Update** | 2026-03-11 |

---

## 11. Glossary

| Term | Definition |
|------|-----------|
| **Store** | `@MainActor ObservableObject` class that owns a screen's UI state and orchestrates service calls |
| **Service** | Business logic layer — owns domain rules, validation, and orchestration |
| **Repository** | I/O layer — reads and writes data (network or local); contains no business logic |
| **RepositoryAPI** | Concrete repository implementation that calls the REST API via `HTTPClient` |
| **DependencyContainer** | Singleton service locator; dependencies registered at startup and resolved via `@Injector` |
| **ServiceRegistry** | Manages service lifecycle — essential services at launch, session services after login |
| **Routable** | Protocol that feature `Route` enums conform to, enabling the custom stack router |
| **Endpoint** | `enum Endpoint` case defining a single API endpoint's URL, method, and auth requirement |
| **KvStorage** | Key-value storage abstraction (wraps UserDefaults) for non-sensitive lightweight persistence |
| **AppSync** | Greater Goods body composition scale product line |
| **BLE** | Bluetooth Low Energy — used for wireless scale communication |
| **SPM** | Swift Package Manager — dependency management for all external packages |
| **SUT** | System Under Test — the object being tested in a unit test |

---

## 12. Dashboard Graph Patterns

The Dashboard feature uses a layered graph architecture. When making changes to chart behavior, follow these rules.

### Layer Responsibilities

| Layer | Type | Responsibility |
|-------|------|---------------|
| `DashboardStore` | Store | Orchestrates chart manager calls and owns selected period state |
| `DashboardGraphManager` | Manager | Coordinates data prep, axis calculation, and series generation |
| `YAxisCalculator` | Calculator | Computes y-axis domain, tick marks, and label values |
| `BaseGraphView` | View | Shared rendering logic — period-specific wrappers stay thin |

Do not put calculation or orchestration logic directly into views. Keep the rendering layer thin.

### Key Rules

- Keep orchestration in managers (`DashboardGraphManager`)
- Keep axis/domain logic in calculator types (`YAxisCalculator`)
- Preserve caching and throttling behavior when changing chart data generation
- Treat scroll state, selection state, and animation state as coordinated concerns
- Prefer testing manager/calculator logic; avoid unit-testing raw chart rendering

### Change Checklist

Before finishing a Dashboard graph change, verify:
- Does the change affect all periods or only week/month/year/total?
- Does it invalidate cached chart data, labels, or y-axis calculations?
- Does it change interaction behavior during scroll or selection?
- Could it introduce animation glitches or performance regressions?
- Does the change belong in `DashboardStore`, a manager, a calculator, or a view?

### Testing Focus

- Calculator/manager tests for deterministic logic
- Period-specific behavior
- Cache invalidation behavior
- Selection/scroll edge cases
- Weightless/goal-state handling

If the change also affects async data preparation or background processing, apply `/swift-concurrency` patterns.
