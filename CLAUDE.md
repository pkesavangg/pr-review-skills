# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MeApp (Weight Gurus) is a dual-platform health/weight tracking application with separate Android and iOS codebases in a single monorepo. The app connects to Bluetooth/WiFi scales, integrates with health platforms (HealthKit, Health Connect, Fitbit, MyFitnessPal), and supports multi-account management.

- **Package**: `com.dmdbrands.gurus.weight`
- **Android**: Kotlin + Jetpack Compose + Hilt
- **iOS**: Swift + SwiftUI + SwiftData

## Build & Run Commands

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

CircleCI builds the iOS project on macOS M1 with Xcode 16.0.0. Android CI is not configured in this repo.

## Documentation

- [Architecture Overview](architecture.md)
- [Database Schema](docs/database-schema.md)
- [Account Switching Flow](docs/account-switching-flow.md)
