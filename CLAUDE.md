# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MeApp (Weight Gurus) is a dual-platform health/weight tracking application with separate Android and iOS codebases in a single monorepo. The app connects to Bluetooth/WiFi scales, integrates with health platforms (HealthKit, Health Connect, Fitbit, MyFitnessPal), and supports multi-account management.

- **Package**: `com.dmdbrands.gurus.weight`
- **Android**: Kotlin + Jetpack Compose + Hilt
- **iOS**: Swift + SwiftUI + SwiftData

## Working Principles (how to approach any change)

These govern *how* to work; the sections below govern *what* the project is.

1. **Think before coding.** State the assumptions you're making and proceed on a
   sensible default — don't stop to ask unless the ambiguity is blocking or hard to
   reverse. If a simpler approach exists, say so before building the complex one.
2. **Simplicity first.** Write the minimum the request needs. No speculative
   features, premature abstraction, or unrequested config. Don't add defensive
   handling for *impossible* cases (real error paths still matter — this is a HIPAA
   app). If a change balloons, stop and simplify; run `/simplify` when in doubt.
3. **Surgical changes.** Touch only what the request demands. Don't refactor working
   or adjacent code, and match the surrounding style — which here means obeying the
   enforced rules (SwiftLint `--strict`, detekt no-`!!`, theme tokens, the iOS
   snapshot boundary). **When you patch several sites of the same bug, surface the
   one architectural fix that would prevent the next one** — as a recommendation,
   without silently expanding the diff.
4. **Goal-driven execution.** Turn a vague task into a checkable outcome before
   coding. Reproduce a bug with a failing test, then fix it; keep tests green across
   refactors. Before committing, run `/self-review` (and `/post-change-guard` on
   iOS). Don't declare done until the criterion is verified.

Good session = small diffs, no overengineering rewrites, assumptions stated up front.

## Project Context

- **Repo / org:** Hosted under the gg-engineering org (`github.com/gg-engineering/meApp`) following the gg-engineering migration.
- **Jira:** Active work tracks in the **MOB** project (GGT-Mobile, board 1088) on `greatergoods.atlassian.net`. Branch and commit prefix is `MOB-XXXX` (the older `MA-XXXX` prefix is legacy).
- **Branch model:**
  - `main` — the MA / 5.0.x release line (shipped Weight Gurus app). Target only for 5.0.x hotfixes.
  - `develop` — **active integration branch and current default base/target.** Phase 2 (`phase2-dev`) has been merged into `develop`, so it now carries the Me.Health 2.0 multi-product work. New branches start from `develop`.

## Phase 2 — Me.Health 2.0 ("Mega App") · current focus

Phase 1 shipped Weight Gurus (weight + body composition) as `v5.0.0`–`v5.0.2`. **Phase 2 merges three products into one app and one server:** **Weight + Blood Pressure (Balance) + Baby.**

- **Product model:** an account has a `productTypes` array (`weight`, `blood_pressure`, `baby`) plus `measurementUnits` (`metric` / `imperialLbOz` / `imperialLbDecimal`). Products are auto-added when a device is paired / a baby is created / an entry is made, and are directly settable.
- **Unified v3 APIs (new app):** `/v3/paired-device/` (any `deviceType`), `/v3/review/`, `/v3/entries/` (multi-`category` write + sync/cursor read + csv), baby profiles/permissions/invitations, `PATCH /v3/account/products` + `/measurement-units`.
- **Backward compatibility is a hard requirement:** legacy weight endpoints (`/v3/operation/*`, `/v3/paired-scale/`, `/v3/review/app|scale`) stay live for old apps. Never guide their removal.
- **Sources of truth:** the `phase2-context` skill (auto-triggers; full API cheat sheet), the [API Changes Specification](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1458962434/Me+App+2.0+API+Changes+Specification) (Confluence), and the [Me.Health Mega App 2.0 Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=8-2145) (design system) — captured in the iOS `phase2-design-system` skill.

## Build & Run Commands

### Run on a device (interactive)

`scripts/run.sh` is an interactive runner that builds, installs, and launches the app on a real device or simulator/emulator. Run it and answer three prompts:

```bash
./scripts/run.sh
#   1) Which platform?  → iOS / Android
#   2) Which build?     → dev (default, press Enter) / production
#   3) Select device    → numbered list of connected devices (physical first, then simulators/emulators)
```

You can also skip prompts by passing args: `./scripts/run.sh ios dev` or `./scripts/run.sh android production` (still prompts for the device).

Build → config mapping:

| Build | iOS configuration | Android build type | API base URL |
|-------|-------------------|--------------------|--------------|
| `dev` (default) | `Dev` | `debug` → `installDebug` | dev server |
| `production` | `Production` | `release` → `installRelease` | `api.weightgurus.com` |

Notes:
- **iOS** lists connected physical devices (via `xcrun devicectl`, filtered to currently-connected) plus available simulators (`xcrun simctl`). Physical-device builds need a valid signing team configured in the project; simulator builds disable signing automatically.
- **Android** `production`/`release` requires a signing config + `google-services.json`; if none is set up, use `dev` (the default). Devices are listed via `adb devices`.

### Android

```bash
# Build (from Android/ directory)
cd Android && ./gradlew assembleDebug

# Run unit tests
cd Android && ./gradlew test

# Run a single test class
cd Android && ./gradlew test --tests "com.dmdbrands.gurus.weight.ExampleTest"

# Run instrumented tests
cd Android && ./gradlew connectedAndroidTest

# Run coverage report + verification (80% line coverage minimum)
cd Android && ./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification

# Clean build
cd Android && ./gradlew clean assembleDebug
```

### iOS

```bash
# Build (from iOS/ directory)
cd iOS && xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'generic/platform=iOS' \
  -configuration Debug \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO

# Run unit tests
cd iOS && xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  -only-testing:meAppTests \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO

# Run UI tests
cd iOS && xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  -only-testing:meAppUITests \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

## Key Paths

### Android

| Path | Description |
|------|-------------|
| `Android/app/src/main/java/com/dmdbrands/gurus/weight/` | Main source root |
| `Android/app/src/main/res/` | Android resources (drawables, strings, fonts) |
| `Android/app/src/main/proto/` | Protobuf definitions |
| `Android/app/src/test/java/com/dmdbrands/gurus/weight/` | Unit tests |
| `Android/app/src/androidTest/java/com/dmdbrands/gurus/weight/` | Instrumented tests |
| `Android/app/healthconnect/src/main/java/com/dmdbrands/gurus/weight/` | Health Connect sub-module source |
| `Android/app/wificonnect/src/main/java/com/dmdbrands/gurus/weight/` | WiFi Connect sub-module source |
| `Android/app/appsync/src/main/java/com/dmdbrands/gurus/weight/` | AppSync sub-module source |
| `Android/bleWrapper/src/main/java/com/dmdbrands/gurus/weight/` | BLE Wrapper module source |
| `Android/notification/src/main/java/com/dmdbrands/gurus/weight/` | Notification module source |
| `Android/iam/src/main/java/com/dmdbrands/gurus/weight/` | IAM module source |
| `Android/app/build.gradle.kts` | App build config |
| `Android/settings.gradle.kts` | Gradle module definitions |

### iOS

| Path | Description |
|------|-------------|
| `iOS/meApp/` | Main source root |
| `iOS/meApp/Core/` | Infrastructure (DI, Navigation, Network, Services, Storage) |
| `iOS/meApp/Data/` | Data layer (API, Services, Storage) |
| `iOS/meApp/Domain/` | Domain layer (Models, Repositories, Services) |
| `iOS/meApp/Features/` | Feature modules (Auth, Dashboard, Entry, Feed, History, ScaleSetup, Settings, AppSync, Common) |
| `iOS/meApp/Theme/` | Theme tokens (Color, Typography, Enums, Tokens) |
| `iOS/meApp/Resources/` | Assets, fonts, GIFs, plists |
| `iOS/meApp/Resources/Info.plist` | App configuration |
| `iOS/meApp/Resources/GoogleService-Info.plist` | Firebase configuration |
| `iOS/meAppTests/` | Unit tests |
| `iOS/meAppUITests/` | UI tests |
| `iOS/meApp.xcodeproj/` | Xcode project |

## Architecture

### Android Architecture (MVI/Clean Architecture)

**Layers**: `domain/` → `data/` → `features/` with `core/` for infrastructure.

**Modules** (`settings.gradle.kts`):
- `:app` — Main application (with sub-modules: `:app:healthconnect`, `:app:wificonnect`, `:app:appsync`)
- `:bleWrapper` — Bluetooth scale connectivity
- `:notification` — Firebase push notifications
- `:iam` — In-App Messaging

**State Management (MVI pattern)**:
- `BaseIntentViewModel<State, Intent>` — all ViewModels extend this
- `IReducer<State, Intent>` — pure reducer: `(State, Intent) → State?`
- Flow: User action → Intent → Reducer → new State → UI recomposes
- State objects are immutable data classes; update via `.copy()`

**Key packages** under `com.dmdbrands.gurus.weight`:
- `core/di/` — Hilt DI modules binding interfaces to implementations
- `core/navigation/` — `AppRoute` sealed classes for type-safe navigation (Navigation3)
- `core/network/` — Retrofit + OkHttp + TokenManager
- `core/service/` — 25+ service implementations
- `domain/repository/` — Repository interfaces (prefix: `I`, e.g., `IAccountRepository`)
- `domain/services/` — Service interfaces
- `data/api/` — Retrofit API interfaces
- `data/repository/` — Repository implementations
- `data/storage/` — Room DB entities/DAOs + DataStore (Protobuf)
- `features/` — 39+ feature directories, each with composables + ViewModels

**Key conventions**:
- All interfaces use `I` prefix (e.g., `IAccountRepository`, `IEntryService`)
- All Compose previews must use `@PreviewTheme` and wrap in `MeAppTheme { ... }`
- Never hardcode colors/typography/spacing — always use `MeAppTheme` tokens (`.colorScheme`, `.typography`, `.spacing`)
- Prefer shared composables from `features/common/` (the `@connect` folder) before Material3 defaults
- Static text goes in feature-specific `strings/` subfolder as PascalCase `Strings` objects (e.g., `LoginStrings.Title`)
- Use `AppLog` for logging, never `Log` directly
- Every `AppInput` must set `imeAction` and `onImeAction` with `FocusRequester`
- All API methods must be `suspend` functions

### iOS Architecture (MVVM + Stores)

**Layers**: `Domain/` → `Data/` → `Features/` with `Core/` for infrastructure.

**Hybrid UIKit + SwiftUI**: `@main` App defers to `SceneDelegate` for window setup, allowing manual DI and lifecycle control before rendering.

**DI System**:
- `DependencyContainer` (singleton registry) + `@Injector` property wrapper for lazy resolution
- `ServiceRegistry` manages service lifecycle: registers essential services at launch, session services after login

**Navigation**: Custom stack-based `Router<Route>` + `RoutingView` with per-feature `Route` enums implementing `Routable` protocol.

**Feature structure** (each feature follows this pattern):
```
Features/{Feature}/
├── Routes/       # Navigation enum (Routable)
├── Stores/       # @MainActor ObservableObject state managers
├── Forms/        # Reactive form validation
├── Views/
│   ├── Screens/  # Root screen views
│   └── Components/
├── Strings/      # PascalCase string constants
└── Enums/
```

**Key conventions**:
- All services are singletons with `.shared` access, registered via `ServiceRegistry`
- Feature stores are `@MainActor final class` with `@Published` properties
- SwiftData for persistence via `PersistenceController` singleton; always perform CRUD on main actor
- Static text in feature-specific `Strings/` folders as PascalCase structs
- Use `LoggerService` for logging exclusively
- Never hardcode colors/fonts/spacing — use `Theme/` resources
- API repositories must ONLY make network calls — no business logic or caching
- Use `async`/`await` for concurrency; prefer actor-isolated services for mutable state

## CI

CircleCI builds the iOS project on macOS M1 with Xcode 16.0.0.

Android CI runs on CircleCI with the following gates:
- **Build**: `./gradlew assembleDebug`
- **Lint**: `./gradlew lint`
- **Unit tests + coverage**: `./gradlew test :app:jacocoTestReport :app:jacocoTestCoverageVerification`
  - **Minimum coverage**: 80% line coverage (enforced by `jacocoTestCoverageVerification`)
  - Coverage HTML report is stored as a CI artifact

## Documentation

- [Documentation index](docs/README.md)
- [Architecture Overview (iOS)](iOS/architecture.md)
- [Database Schema](docs/guides/DATABASE_SCHEMA.md)
- [Account Switching Flow](docs/guides/ACCOUNT_SWITCHING_FLOW.md)
- [Product Types — Current State](docs/guides/PRODUCT_TYPES_CURRENT_STATE.md)

### Keeping docs current

These docs are **maintained** — a code change that outdates one should update it in the same task. This is enforced, not just requested:

- A root PostToolUse hook (`.claude/settings.json`) runs [`scripts/docs-freshness-check.sh`](scripts/docs-freshness-check.sh) on every edit. It prints `📝 Docs check …` naming the doc a changed path affects **and how significant the change is** — `NEW FILE`, `major change (N lines)`, or `minor change (N lines)`. Update the doc for NEW/major hits; for a minor hit only if behaviour/schema actually changed.
- The [`/update-architecture`](iOS/.claude/skills/update-architecture/SKILL.md) skill owns the full source→doc map and does the update (`architecture.md` **and** the `docs/` folder). Run it when the hook fires or after any structural change.
- The same hook also prints `🌐 Also mirror this to Confluence …` — the wiki hub mirrors these docs. Run [`/update-confluence`](.claude/skills/update-confluence/SKILL.md) to publish upward; it drafts the edit and **writes only after you approve**. The page tree + IDs + change→page map are in [`docs/overview/CONFLUENCE.md`](docs/overview/CONFLUENCE.md).
- Adding/moving a maintained doc? Update the map in **both** `scripts/docs-freshness-check.sh` and the skill so they stay identical. Adding/moving a Confluence page? Update `docs/overview/CONFLUENCE.md` and the `CONF_PAGE` note in the script.
