# MeApp (Weight Gurus)

A dual-platform health and weight tracking application that connects to Bluetooth/WiFi scales, integrates with health platforms (HealthKit, Health Connect, Fitbit, MyFitnessPal), and supports multi-account management.

**Package**: `com.dmdbrands.gurus.weight`

## Prerequisites

### iOS
- Xcode 16.0+
- SwiftLint (`brew install swiftlint`)
- Gitleaks (`brew install gitleaks`)

### Android
- Android Studio
- JDK 17+
- Gradle (via wrapper)

## Getting Started

```bash
# Clone the repository
git clone https://github.com/dmdbrands/meApp-1.git
cd meApp-1

# Set up git hooks
git config core.hooksPath .githooks
```

### iOS

```bash
cd iOS
xcodebuild -resolvePackageDependencies -project meApp.xcodeproj
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'generic/platform=iOS' \
  -configuration Debug \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

### Android

```bash
cd Android
./gradlew assembleDebug
```

## Testing

### iOS Unit Tests

```bash
cd iOS
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  -only-testing:meAppTests \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

### Android Unit Tests

```bash
cd Android
./gradlew test
```

## Architecture

### iOS — MVVM + Stores
- **Layers**: `Domain/` → `Data/` → `Features/` with `Core/` for infrastructure
- **DI**: `DependencyContainer` singleton + `@Injector` property wrapper
- **Navigation**: Custom stack-based `Router<Route>` + `RoutingView`

### Android — MVI / Clean Architecture
- **Layers**: `domain/` → `data/` → `features/` with `core/` for infrastructure
- **DI**: Hilt
- **State**: `BaseIntentViewModel<State, Intent>` with pure reducers

## CI/CD

This project uses **CircleCI**. See `.circleci/config.yml` for pipeline configuration.

Pipeline includes:
- Gitleaks secrets scanning
- SwiftLint static analysis
- iOS build and unit tests with code coverage
- Coverage threshold enforcement (80%)

## Code Style

- **iOS**: SwiftLint (`.swiftlint.yml` in `iOS/`)
- **Android**: Detekt (`Android/config/detekt/detekt.yml`)
- **Editor**: `.editorconfig` for cross-editor consistency

## Security

- Secrets scanning: Gitleaks (`.gitleaks.toml`)
- Dependency scanning: Dependabot (`.github/dependabot.yml`)
- Pre-commit hooks: `.githooks/` (Gitleaks + SwiftLint)
- Commit message validation: JIRA ticket reference required (MOB-XXXX; legacy MA-XXXX also accepted)

## Team

Owned by `@dmdbrands/me-health`. See `CODEOWNERS` for code ownership.
