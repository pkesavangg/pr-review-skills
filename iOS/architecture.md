# meApp iOS — Architecture

## 1. Project Overview

- **App:** Weight Gurus (meApp) by Greater Goods
- **Purpose:** Health and weight tracking — connects to Bluetooth/Wi-Fi scales, syncs with HealthKit, Fitbit, and MyFitnessPal
- **Platform:** iOS (SwiftUI + UIKit hybrid, targeting iOS 16+)

---

## 2. Architecture Style

Clean Architecture layered as **MVVM + Store pattern**:

- Layers flow: `Domain` → `Data` → `Features`, with `Core` as shared infrastructure
- **Stores** act as `ObservableObject` view models — they own UI state and orchestrate service calls
- **Services** own business logic and domain rules
- **Repositories** own I/O — networking and persistence; no logic lives here
- Views are pure rendering — they read from stores and dispatch user actions

This separation keeps each layer independently testable and prevents logic from leaking into the UI.

---

## 3. Project Structure

```
iOS/meApp/
├── Core/          # DI, networking, navigation, app lifecycle, config
├── Data/          # Concrete implementations: API repos, services, SwiftData local repos
├── Domain/        # Protocols, models (API / DB / Domain), repository interfaces
├── Features/      # Self-contained feature modules
├── Theme/         # Design tokens — colors, typography, spacing
└── Resources/     # Assets, fonts, Info.plist, GoogleService-Info.plist
```

| Folder | Purpose |
|--------|---------|
| `Core/` | Shared infrastructure — `HTTPClient`, `DependencyContainer`, `Router`, `AppDelegate` |
| `Data/` | Implementations of Domain protocols — never imported directly by Features |
| `Domain/` | Source of truth for interfaces and models; no UIKit/SwiftUI imports |
| `Features/` | UI modules, one folder per feature; each owns its store, views, and navigation |
| `Theme/` | Centralized design system — all colors and fonts come from here |
| `Resources/` | Static assets and app configuration plists |

---

## 4. Feature Organization

Each feature follows a consistent internal structure:

```
Features/<Feature>/
├── Routes/         # Navigation enum conforming to Routable
├── Stores/         # @MainActor ObservableObject — state and event handling
├── Views/
│   ├── Screens/    # Root screen views (navigation entry points)
│   └── Components/ # Feature-local reusable UI
├── Forms/          # Reactive form validators
├── Strings/        # PascalCase string constants
└── Enums/          # Feature-local enums
```

---

## 5. Data Flow

```
User Action
  → View calls Store method
    → Store calls Service or Repository
      → Business logic / I/O executes (async/await)
        → Result returned to Store
          → Store updates @Published state
            → SwiftUI re-renders View
```

- Views never call services or repositories directly
- Stores coordinate but do not contain business logic
- All async work happens in services and repositories; stores await results

---

## 6. Navigation

- **Custom stack router:** `Router<Route>` paired with `RoutingView`
- Each feature defines a `Route` enum implementing `Routable`
- Navigation is **feature-owned** — each feature manages its own push/pop stack
- App-level routing (tab switching, deep links) lives in `Core/Navigation/`
- Stores drive navigation: `router.push(.detail(id:))`, `router.pop()`

---

## 7. State Management

| Concern | Owner |
|---------|-------|
| UI state (loading, errors, form values) | `Store` — `@Published` properties |
| Business / domain state | `Service` |
| Persisted user data | `Repository` (SwiftData or Keychain) |
| App-wide shared state | `ServiceRegistry` singletons |

- Stores are `@MainActor final class` — all mutations are main-thread-safe
- Services use actor isolation for mutable shared state
- Views own no state beyond what SwiftUI requires for local animation/transition

---

## 8. Dependency Management

- **Container:** `DependencyContainer.shared` — singleton service locator
- **Injection:** `@Injector` property wrapper — resolves lazily at call site
- **Testing:** Constructor injection is preferred; `TestDependencyContainer.reset()` resets the container between tests
- **Lifecycle:** `ServiceRegistry` registers essential services at launch; session-scoped services register after login

```swift
// Production
@Injector var entryService: EntryServiceProtocol

// Testing
let sut = EntryService(repository: MockEntryRepository())
```

---

## 9. Persistence and Networking

**Networking**
- All HTTP calls flow through `HTTPClientProtocol` (`get` / `send`)
- Endpoints are declared in `Domain/Models/API/EndPoints.swift` as `enum Endpoint`
- Only `*RepositoryAPI` types call `httpClient` — stores and services never touch the network directly

**Persistence**
- SwiftData stack managed via `PersistenceController` singleton
- All CRUD must run on the main actor
- Auth tokens live in Keychain via `KeychainService` — never in SwiftData or UserDefaults

---

## 10. Development Guidelines

- **Views are dumb** — no business logic, no service access, no async work
- **Services own rules** — validation, transformation, and orchestration belong here
- **Repositories own I/O** — one responsibility: read and write data
- **One Store per screen** — avoid sharing stores across unrelated features
- **No hardcoded strings** — all UI text goes in feature `Strings/` files
- **No hardcoded colors or fonts** — use only `Theme/` tokens
- **Protocols everywhere** — depend on abstractions so every component can be tested in isolation

---

## 11. How to Add a New Feature

1. Create `Features/<FeatureName>/` with `Routes/`, `Stores/`, `Views/Screens/`, `Strings/`
2. Define `<FeatureName>Route: Routable` in `Routes/`
3. Define `<FeatureName>Store: ObservableObject` in `Stores/` — inject services via `@Injector`
4. **Network access:** add a case to `EndPoints.swift`, implement `*RepositoryAPIProtocol` + `*RepositoryAPI` in `Data/API/`
5. **Persistence:** add a SwiftData `@Model` to `Domain/Models/DB/`, implement a local repository in `Data/Storage/DB/`
6. Register new services/repositories in `Core/DI/` and `ServiceRegistry` as needed
7. Add unit tests in `meAppTests/Features/<FeatureName>/`

---

## 12. Future Scalability

- The protocol-based dependency graph allows features to be extracted into Swift packages without rearchitecting
- `ServiceRegistry`'s phased startup model lets new session-scoped services plug in without touching launch code
- Feature-first organization means teams can own features independently with minimal cross-feature coupling
- The router pattern extends cleanly to deep links and multi-scene without structural changes
- In-memory SwiftData containers keep the persistence layer independently verifiable as the schema grows
