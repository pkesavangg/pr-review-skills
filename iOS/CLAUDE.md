# meApp iOS — Claude Code Context

## Project Overview

iOS application for [Greater Goods](https://greatergoods.com) — a health/weight tracking app (branded as Weight Gurus). Built with **Swift + SwiftUI**, targeting iOS. Integrates Bluetooth scales, Wi-Fi scales, HealthKit, Fitbit, MyFitnessPal, and Firebase.

**Xcode project:** `iOS/meApp.xcodeproj`
**Main target:** `meApp`
**Test targets:** `meAppTests` (unit), `meAppUITests` (UI)

---

## Architecture

Clean Architecture layered as follows:

```
Features/          # Feature modules (Auth, Dashboard, Entry, Feed, Settings, …)
  <Feature>/
    Stores/        # @MainActor ObservableObject — state + business logic
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

Reference: `meAppTests/Support/DI/TestDependencyContainer.swift`

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

## Unit Tests (`meAppTests`)

### Framework
Swift Testing (`@Test`, `@Suite(.serialized)`, `#expect`, `Issue.record`).

### File placement
Tests live in `meAppTests/Features/<Feature>/`. New `.swift` files are **auto-included** by the test target via `PBXFileSystemSynchronizedRootGroup` — **no `.pbxproj` editing needed**.

### Structure per test file
```swift
@Suite(.serialized)
@MainActor
struct FooTests {
    private func makeSUT() -> (sut: Foo, dep: MockDep) { ... }

    // MARK: - methodName
    @Test("methodName success: description")
    func methodNameSuccess() async throws { ... }

    @Test("methodName failure: description")
    func methodNameFailure() async throws { ... }
}
```

### Test organization within each group
1. Success path
2. Validation / guard failures
3. Runtime / API / network / persistence failures

### Naming convention
`"methodName <qualifier>: expected behavior"` — e.g.:
- `"syncOperation success: calls send with operationsR4(nil) POST with auth"`
- `"fetchOperations failure: propagates noInternet error"`

### Key shared mocks
| Mock | Location | Used for |
|------|----------|----------|
| `MockHTTPClient` | `Support/Mocks/Network/` | All `*RepositoryAPI` tests |
| `TestDependencyContainer` | `Support/DI/` | Store/Service tests needing DI |
| Feature mocks | `Features/<Feature>/Mocks/` | Per-feature service/repo mocks |
| Fixtures | `Features/<Feature>/Fixtures/` | Factory helpers for domain objects |

### Assertions
```swift
#expect(http.sendCalls == 1)
#expect(http.lastSendMethod == .post)
guard case .operationsR4(let ts) = http.lastSendEndpoint else {
    Issue.record("Expected .operationsR4 endpoint"); return
}
await #expect(throws: HTTPError.unauthorized) { try await sut.method() }
```

### Coverage minimums
| Layer | Min |
|-------|-----|
| `Data/Services` | 80% (85% for auth/account/sync) |
| Stores / ViewModels | 80% |
| Forms / validation | 85% |
| `Data/API` repository adapters | 75% |

UI layer files (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) are **excluded** from the coverage metric.

---

## Running Tests

**Always run tests on a connected physical device — never use a simulator.**

### Xcode
- Scheme: `meAppTests`, Configuration: `Dev`, `Cmd+U`

### CLI

First find the physical device ID (pick the first entry without an `error:` field):
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator
```

Then run:
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

### Coverage reports
```bash
SCHEME="meAppTests" DEVICE_ID={DEVICE_ID} CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```
Reports output to `iOS/meAppTests/Reports/` (ignored by git).

---

## Git & Branching

- **Branch format:** `{ISSUE-ID}-{slugified-summary}` — e.g. `MA-3318-add-unit-tests-for-entry-api-repository`
- **Commit format:** `MA-XXXX Short description of what was done`
  - Examples: `MA-3316 Add unit tests for AccountRepositoryAPI`
- **Main branch:** `main` — always target PRs here unless told otherwise
- **Jira project:** `MA` on `greatergoods.atlassian.net`

---

## Key Patterns

### Adding a `*RepositoryAPI` test
1. Check the concrete class (`Data/API/`) for methods and their `Endpoint`/`HTTPMethod`/auth.
2. Ensure the class accepts `HTTPClientProtocol` via `init` (refactor if not).
3. Create `meAppTests/Features/<Feature>/<Feature>RepositoryAPITests.swift`.
4. Use `MockHTTPClient` — set `sendResult`/`getResult` or `sendError`/`getError`.
5. Pattern-match the endpoint case: `guard case .myEndpoint = http.lastSendEndpoint`.

### Adding a Service test
1. Use `TestDependencyContainer.reset()` in each test or a shared `init`.
2. Register mock dependencies before creating the SUT.
3. Pin dependencies directly on SUT where async/lazy resolve could race.
4. Use `@Suite(.serialized)` if any shared global state is unavoidable.

### SwiftData (persistence) tests
- Use **in-memory** `ModelContainer` only — never production paths.
- Build a per-test container in `Arrange`.

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

## Useful File Locations

| What | Where |
|------|-------|
| All API endpoints | `meApp/Domain/Models/API/EndPoints.swift` |
| HTTP client protocol | `meApp/Core/Network/HTTPClientProtocol.swift` |
| DI container | `meApp/Core/DI/DependencyContainer.swift` |
| App environments | `meApp/Core/Config/Environment.swift` |
| App constants | `meApp/Core/Config/AppConstants.swift` |
| Test DI setup | `meAppTests/Support/DI/TestDependencyContainer.swift` |
| Mock HTTP client | `meAppTests/Support/Mocks/Network/MockHTTPClient.swift` |
| Unit test guide | `meAppTests/docs/UNIT_TESTING.md` |
| Coverage guide | `docs/COVERAGE_REPORTING.md` |
