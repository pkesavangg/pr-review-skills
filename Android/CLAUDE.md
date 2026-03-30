# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK
./gradlew test                 # Run unit tests
./gradlew detekt               # Run static analysis (enforces null safety)
./gradlew clean assembleDebug  # Clean build

# Coverage report + enforcement (80% line minimum)
./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification
```

Always run `./gradlew assembleDebug` after making changes to verify the build succeeds before considering a task done.

## Test Coverage Enforcement

JaCoCo enforces a **minimum 80% line coverage** on the `:app` module. CI fails if coverage drops below this threshold.

- **Report task**: `./gradlew :app:jacocoTestReport` — generates HTML + XML reports
- **Verification task**: `./gradlew :app:jacocoTestCoverageVerification` — fails the build if line coverage < 80%
- HTML report: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- Generated code (Hilt, Room, Compose, Protobuf, R/BuildConfig) is excluded from coverage

## Pre-commit Hooks (Lefthook)

Pre-commit hooks enforce code quality before every commit. Setup:

```bash
# Install tools (macOS)
brew install lefthook detekt

# Activate hooks (run from repo root, one-time setup)
cd /path/to/meApp && lefthook install
```

**What runs on commit:**
- **Detekt CLI** — static analysis on staged `.kt` files (<10s)
- **Gitleaks** — secrets detection on all staged files (warns if not installed)
- **JIRA ticket** — validates commit message contains `MA-XXXX`

To bypass in emergencies: `git commit --no-verify`

## Modules

| Module | Purpose |
|--------|---------|
| `:app` | Main application — all features, UI, domain, data layers |
| `:notification` | Push notification handling |
| `:app:healthconnect` | Health Connect integration |
| `:app:wificonnect` | WiFi scale connectivity |
| `:app:appsync` | QR/barcode scan sync feature |
| `:bleWrapper` | Bluetooth Low Energy abstraction |
| `:iam` | In-App Messaging |

## Architecture — MVI Pattern

Every feature follows the same three-file MVI structure:

**Reducer** — pure function, maps `(State, Intent) → State?`. No side effects, no coroutines.
```kotlin
class FooReducer : IReducer<FooState, FooIntent> {
    override fun reduce(state: FooState, intent: FooIntent): FooState? = when (intent) {
        is FooIntent.SetBar -> state.copy(bar = intent.bar)
        else -> state.copy()
    }
}
```

**State** — immutable `data class` implementing `IReducer.State`.

**ViewModel** — extends `BaseIntentViewModel<State, Intent>`. Handles side effects (coroutines, navigation, API calls). Calls `handleIntent()` for state updates.

```kotlin
@HiltViewModel
class FooViewModel @Inject constructor(
    private val fooService: IFooService,
) : BaseIntentViewModel<FooState, FooIntent>(FooReducer()) {
    override fun provideInitialState() = FooState()

    override fun handleIntent(intent: FooIntent) {
        super.handleIntent(intent)   // always call super — routes to reducer
        when (intent) {
            FooIntent.Save -> save() // handle side effects here
            else -> {}
        }
    }
}
```

Key classes:
- `BaseIntentViewModel` — `features/common/service/BaseIntentViewModel.kt`
- `BaseViewModel` — `features/common/viewmodel/BaseViewModel.kt` (auto-injects `navigationService`, `dialogQueueService`)
- `IReducer` — `domain/interfaces/IReducer.kt`

## Navigation

Routes are defined as sealed classes in `core/navigation/AppRoute.kt`. Use `navigationService.navigateTo()` / `navigationService.navigateBack()` from any ViewModel (injected via `BaseViewModel`).

```kotlin
navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
navigationService.navigateBack()
```

Never inject `NavigationService` directly — it's already available in every ViewModel via `BaseViewModel`.

## Global Dialogs & Toasts

Use `dialogQueueService` (injected via `BaseViewModel`) for all dialogs and toasts:

```kotlin
dialogQueueService.enqueue(DialogModel.Confirm(title = ..., onConfirm = { ... }))
dialogQueueService.showLoader(message = ...)
dialogQueueService.dismissLoader()
dialogQueueService.showToast(Toast(title = null, message = ..., action = null))
```

## Null Safety

`!!` operators are **banned** — `detekt` enforces this via `UnsafeCallOnNullableType` in `config/detekt/detekt.yml`. Use safe alternatives:

| Pattern | Use when |
|---------|---------|
| `val x = foo ?: return` | Early exit if null |
| `val x = foo ?: return@launch` | Early exit inside coroutine |
| `foo?.let { }` | Null-safe operation, no-op on null |
| `requireNotNull(foo) { "msg" }` | Fail fast with descriptive message (for values that must exist) |
| `val local = property` before null check | Smart-cast `var` class properties or cross-module properties |

Note: smart cast works on local `val`s only. Always capture `var` class properties or public API properties into a local `val` before a null check.

## Compose & Theming Rules

- All previews must use `@PreviewTheme` annotation and wrap content in `MeAppTheme { }`.
- **Never** use hardcoded colors, spacing, or text styles. Always use theme tokens:
  - Colors: `MeAppTheme.colorScheme.*`
  - Typography: `MeAppTheme.typography.*`
  - Spacing: `MeAppTheme.spacing.*`
- All composables and previews must support both light and dark themes.
- Check `res/drawable/` for existing vector assets before adding new ones. Reuse when possible; follow naming `ic_feature_name.xml`.

```kotlin
@PreviewTheme
@Composable
fun FooPreview() {
    MeAppTheme {
        Text("Hello", style = MeAppTheme.typography.body1, color = MeAppTheme.colorScheme.primary)
    }
}
```

## Dependency Injection

Hilt is used throughout. All DI modules live in `core/di/`. Repositories and services are bound to `SingletonComponent`.

For ViewModels needing a runtime parameter (e.g., `scaleId`), use `@AssistedInject` + `@AssistedFactory`:

```kotlin
@HiltViewModel(assistedFactory = FooViewModel.Factory::class)
class FooViewModel @AssistedInject constructor(
    @Assisted val scaleId: String,
    private val deviceService: IDeviceService,
) : BaseIntentViewModel<FooState, FooIntent>(FooReducer()) {
    @AssistedFactory interface Factory { fun create(scaleId: String): FooViewModel }
}
```

## Logging

Use `AppLog` (not `Log` or `Timber` directly) with a `TAG` constant:

```kotlin
companion object { private const val TAG = "FooViewModel" }
AppLog.d(TAG, "message")
AppLog.e(TAG, "error", exception)
```

## Analytics Events

Use `IAnalyticsService` for Firebase Analytics event tracking. Inject via Hilt and call `logEvent()`.

**Naming conventions:**
- Event names: `snake_case`, max 40 characters (e.g., `weight_entry_created`)
- Parameter names: `snake_case`, max 36 characters (e.g., `error_type`)
- **No PII** in event parameters — no emails, names, or account IDs
- Constants defined in `IAnalyticsService.Events` and `IAnalyticsService.Params`

**Current events:**

| Event | Trigger | Parameters |
|-------|---------|------------|
| `weight_entry_created` | Entry saved (manual or scale) | — |
| `manual_entry_created` | Manual entry saved from form | — |
| `scale_connected` | BLE/WiFi scale connects | `scale_type` |
| `account_switched` | User switches active account | — |
| `login_success` | Login succeeds | — |
| `login_failure` | Login fails | `error_type` |
| `signup_completed` | New user completes signup | — |

```kotlin
analyticsService.logEvent(IAnalyticsService.Events.LOGIN_SUCCESS)
analyticsService.logEvent(
    IAnalyticsService.Events.LOGIN_FAILURE,
    Bundle().apply { putString(IAnalyticsService.Params.ERROR_TYPE, "http_401") },
)
```
