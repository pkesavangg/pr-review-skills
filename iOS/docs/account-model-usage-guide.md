# Account Model — How to Use It

A hands-on developer guide. Every sample is grounded in real patterns used throughout the codebase.

---

## Table of Contents

1. [The Golden Rule](#1-the-golden-rule)
2. [Reading Account Data in a Store](#2-reading-account-data-in-a-store)
3. [Observing Account Changes (Combine)](#3-observing-account-changes-combine)
4. [Using Account Data in SwiftUI Views](#4-using-account-data-in-swiftui-views)
5. [Mutating Account Data](#5-mutating-account-data)
6. [Safe Async Patterns](#6-safe-async-patterns)
7. [Working with Child Settings Models](#7-working-with-child-settings-models)
8. [Multi-Account Scenarios](#8-multi-account-scenarios)
9. [Common Mistakes](#9-common-mistakes)

---

## 1. The Golden Rule

You **never** touch `AccountRepository` or `PersistenceController` directly from a feature. All account reads and writes go through `AccountService`.

```
Feature Store
    │
    └── @Injector var accountService: AccountServiceProtocol
            │
            ├── .activeAccount        ← read current account (in-memory, @Published)
            ├── .allAccounts          ← read all stored accounts
            ├── .activeAccountPublisher  ← observe changes via Combine
            └── async methods         ← mutate via service calls
```

---

## 2. Reading Account Data in a Store

### 2a. Inject the service and declare a local mirror

```swift
@MainActor
class MyFeatureStore: ObservableObject {
    @Injector var accountService: AccountServiceProtocol

    // Mirror the active account locally so SwiftUI can observe it
    @Published var activeAccount: Account?

    init() {
        // Seed the local mirror immediately so the UI has data before
        // the publisher fires its first value
        activeAccount = accountService.activeAccount
    }
}
```

> **Why mirror?** `accountService.activeAccount` is `@Published` on `AccountService`, but your store's SwiftUI views observe *your store's* `@Published` properties. Copy the value in and keep it in sync via the publisher (see Section 3).

### 2b. Access fields directly from the mirror

```swift
// From SettingsStore / GoalSettingScreen
private var weightUnit: WeightUnit {
    activeAccount?.weightSettings?.weightUnit ?? .lb
}

// From DashboardStore
var currentUnitText: String {
    accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
}

var currentWeightlessMode: Bool {
    accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
}
```

### 2c. Always guard before using `accountId`

```swift
// From EntryStore.saveEntry()
guard let accountId = accountService.activeAccount?.accountId else { return }

// From BpmSetupStore
guard let accountId = accountService.activeAccount?.accountId else {
    throw AccountError.noActiveAccount
}
```

---

## 3. Observing Account Changes (Combine)

### 3a. Mirror the active account into your store

```swift
@MainActor
class SettingsStore: ObservableObject {
    @Injector var accountService: AccountServiceProtocol

    @Published var activeAccount: Account?
    var cancellables = Set<AnyCancellable>()

    init() {
        // Subscribe to every change that AccountService publishes
        accountService.activeAccountPublisher
            .sink { [weak self] account in
                self?.activeAccount = account
            }
            .store(in: &cancellables)
    }
}
```

> Actual usage: [AccountsStore.swift:70](../meApp/Features/Settings/Account/Stores/AccountsStore.swift#L70), [SettingsStore.swift](../meApp/Features/Settings/Stores/SettingsStore.swift)

### 3b. React to account changes — account switch or login

```swift
// ProductTypeStore — rebuild UI when the active accountId or productTypes change
accountService.activeAccountPublisher
    .map { $0?.accountId }
    .removeDuplicates()          // only fire when accountId actually changes
    .sink { [weak self] accountId in
        guard let self else { return }
        Task { @MainActor in
            await self.handleAccountChange(accountId)
        }
    }
    .store(in: &cancellables)
```

> Actual usage: [ProductTypeStore.swift:80](../meApp/Features/Common/Stores/ProductTypeStore.swift#L80)

### 3c. React to product-type changes

```swift
// Rebuild the available product list whenever productTypes array changes
accountService.activeAccountPublisher
    .map {
        AccountSelectionSnapshot(
            accountId: $0?.accountId,
            productTypes: $0?.productTypes ?? []
        )
    }
    .removeDuplicates()          // Equatable snapshot prevents redundant rebuilds
    .receive(on: DispatchQueue.main)
    .sink { [weak self] _ in
        self?.rebuild()
    }
    .store(in: &cancellables)
```

### 3d. Watch all accounts (e.g. account-switcher UI)

```swift
// AccountsStore — build the user-item list for the accounts screen
accountService.allAccountsPublisher
    .sink { [weak self] allAccounts in
        guard let self else { return }

        let loggedIn  = allAccounts.filter { $0.isLoggedIn == true && !($0.isExpired ?? false) }
        let loggedOut = allAccounts.filter { $0.isLoggedIn != true || ($0.isExpired ?? false) }

        self.accounts = loggedIn + loggedOut

        self.userItems = self.accounts.map { account in
            UserItemInfo(
                accountID: account.accountId,
                name: account.firstName ?? account.email,
                email: account.email,
                isSelected: account.isActiveAccount ?? false,
                isExpired: !(account.isLoggedIn == true)
            )
        }
    }
    .store(in: &cancellables)
```

> Actual usage: [AccountsStore.swift:80](../meApp/Features/Settings/Account/Stores/AccountsStore.swift#L80)

---

## 4. Using Account Data in SwiftUI Views

### 4a. Read through EnvironmentObject store — never inject AccountService into a View

```swift
struct GoalSettingScreen: View {
    @EnvironmentObject private var settingsStore: SettingsStore

    // Derive view-local values from the store's mirrored account
    private var weightUnit: WeightUnit {
        settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb
    }

    var body: some View {
        // Check goal state before deciding what to show
        if settingsStore.activeAccount?.goalSettings?.goalType != nil {
            GoalProgressView()
            Text("Update your goal")
        }
    }
}
```

> Actual usage: [GoalSettingScreen.swift:22](../meApp/Features/Settings/Profile/Views/Screens/GoalSettingScreen.swift#L22)

### 4b. Pass primitives to child views — not the Account object

```swift
// In the parent view's body
WeightSnapshotCard(
    weightUnit: viewModel.activeAccount?.weightSettings?.weightUnit ?? .lb,
    goalWeight: viewModel.activeAccount?.goalSettings?.goalWeight
)

// Child view takes value types only — not Account
struct WeightSnapshotCard: View {
    let weightUnit: WeightUnit
    let goalWeight: Double?
    // ...
}
```

> Actual usage: [WeightSnapshotCard.swift:89](../meApp/Features/Dashboard/Views/Components/WeightSnapshotCard.swift#L89)

### 4c. Use a Picker bound to the store — mutations stay in the store

```swift
// SettingsScreen — weight unit picker driven by the store's account mirror
Picker("Unit", selection: $settingsStore.selectedWeightUnit) {
    ForEach(WeightUnit.allCases) { unit in Text(unit.rawValue) }
}
.onChange(of: settingsStore.selectedWeightUnit) { _, newUnit in
    Task { try await settingsStore.saveBodyComp() }
}
```

---

## 5. Mutating Account Data

### 5a. Always call the AccountService method — never mutate `activeAccount` directly

```swift
// WRONG — mutating the mirror bypasses persistence and sync
activeAccount?.firstName = "Jane"   // ❌ change is lost on next updatePublishedState()

// CORRECT — delegate to the service
Task {
    try await accountService.updateProfile(profile)   // ✅ saves to SwiftData + API
}
```

### 5b. Profile update

```swift
func saveProfile() {
    guard let account = activeAccount else { return }

    // Extract primitives before the async call (SwiftData safety rule)
    let profile = Profile(
        firstName: editProfileForm.firstName.value,
        lastName: editProfileForm.lastName.value,
        email: account.email,
        gender: editProfileForm.gender.value,
        zipcode: editProfileForm.zipcode.value,
        dob: editProfileForm.dob.value,
        weightUnit: account.weightSettings?.weightUnit ?? .lb,
        height: Double(account.weightSettings?.height ?? "0") ?? 0.0,
        activityLevel: account.weightSettings?.activityLevel ?? .normal
    )

    Task {
        do {
            try await accountService.updateProfile(profile, canSaveOffline: true)
            notificationService.showToast(ToastModel(title: "Saved", message: "Profile updated"))
        } catch {
            notificationService.showToast(ToastModel(title: "Error", message: error.localizedDescription))
        }
    }
}
```

### 5c. Goal update

```swift
func saveGoal(router: Router<SettingsRoute>) {
    guard let currentWeight = latestWeight else { return }

    let goal = Goal(
        type: selectedGoalType,
        goalWeight: Int(goalForm.goalWeight.value),
        initialWeight: currentWeight,
        goalType: selectedGoalType
    )

    Task {
        do {
            try await accountService.createGoal(goal)
            router.pop()
        } catch {
            logger.log(level: .error, tag: tag, message: "Goal save failed: \(error)")
        }
    }
}
```

### 5d. Streak toggle

```swift
func toggleStreak(isOn: Bool) {
    let timestamp = DateTimeTools.getCurrentDatetimeIsoString()
    Task {
        try await accountService.updateStreak(
            isStreakOn: isOn,
            streakTimestamp: timestamp
        )
    }
}
```

### 5e. Product types (local-only, no API call)

```swift
func setProductTypes(_ types: [String]) {
    Task {
        try await accountService.updateProductTypes(types)
    }
}
```

---

## 6. Safe Async Patterns

### 6a. Extract primitives BEFORE every `await`

SwiftData `@Model` objects are bound to the `ModelContext` that owns them. After any `await` suspension point the model context may have been mutated by another task, making the reference stale or the properties inaccessible from a different isolation domain.

```swift
// WRONG — accessing account fields across an await boundary
Task {
    let name = account.firstName           // read on main actor — OK so far
    await someBackgroundWork()             // suspension point
    print(name)                            // ✅ primitive — still fine
    print(account.firstName)              // ❌ risky — account may have been
}                                          //    changed or re-fetched by now

// CORRECT — extract everything before the first await
func switchActiveAccount(to accountId: String) {
    // R1: extract before async boundary
    let userName = account.firstName?.isEmpty == false
        ? account.firstName ?? account.email
        : account.email

    Task {
        notificationService.showLoader(LoaderModel(text: "Switching account..."))
        defer { notificationService.dismissLoader() }
        do {
            try await accountService.switchAccount(to: account)
            logger.log(level: .info, tag: tag, message: "Switched to \(accountId)")
        } catch { /* handle */ }
    }
}
```

> Actual usage: [AccountsStore.swift:170](../meApp/Features/Settings/Account/Stores/AccountsStore.swift#L170) — note the comment `// R1: Extract @Model data before async boundary`

### 6b. Read `accountId` once, then use the ID — not the object

```swift
// Capture the ID before entering any async scope
guard let accountId = accountService.activeAccount?.accountId else { return }

Task {
    // Use the captured primitive, not the object
    let entry = Entry(
        entryTimestamp: entryTimestamp,
        accountId: accountId,       // ✅ plain String, safe across await
        operationType: "create",
        deviceType: "scale",
        isSynced: false
    )
    try await entryService.saveNewEntry(entry)
}
```

> Actual usage: [EntryStore.swift:154](../meApp/Features/Entry/Stores/EntryStore.swift#L154)

### 6c. After `await`, re-fetch from the service — do not hold stale references

If you need to read account data *after* an async operation, ask the service again rather than reusing a captured reference:

```swift
Task {
    try await accountService.updateBodyComp(bodyComp)

    // Re-read from the service — it ran updatePublishedState() internally
    let finalUnit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
    updateUnitDisplay(finalUnit)
}
```

---

## 7. Working with Child Settings Models

All child models are accessed through the `Account` object via optional chaining. Use the `?? default` fallback to keep your code concise.

### Weight unit (most common)

```swift
// Read
let unit: WeightUnit = activeAccount?.weightSettings?.weightUnit ?? .lb
let isMetric = activeAccount?.weightSettings?.weightUnit == .kg
let unitText  = activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"

// Height
let heightString = activeAccount?.weightSettings?.height ?? "0"
let heightDouble  = Double(heightString) ?? 0.0
```

### Goal settings

```swift
let hasGoal      = activeAccount?.goalSettings?.goalType != nil
let goalWeight   = activeAccount?.goalSettings?.goalWeight    // Double?
let initialWeight = activeAccount?.goalSettings?.initialWeight // Double?
let goalType     = activeAccount?.goalSettings?.goalType      // GoalType?
```

### Dashboard settings

```swift
let dashboardType   = activeAccount?.dashboardSettings?.dashboardType    // String?
let metricsRaw      = activeAccount?.dashboardSettings?.dashboardMetrics // "bmi,weight,..."
let metrics: [String] = metricsRaw?.split(separator: ",").map(String.init) ?? []
```

### Streak settings

```swift
let isStreakOn = activeAccount?.streaksSettings?.isStreakOn ?? false
let timestamp  = activeAccount?.streaksSettings?.streakTimestamp
```

### Weightless mode

```swift
let isWeightlessOn     = activeAccount?.weightlessSettings?.isWeightlessOn ?? false
let weightlessWeight   = activeAccount?.weightlessSettings?.weightlessWeight
let weightlessTimestamp = activeAccount?.weightlessSettings?.weightlessTimestamp
```

### Integration settings

```swift
let isHealthKitOn = activeAccount?.integrationSettings?.isHealthKitOn ?? false
let isFitbitOn    = activeAccount?.integrationSettings?.isFitbitOn    ?? false
let isMfpOn       = activeAccount?.integrationSettings?.isMfpOn       ?? false
```

### Notification settings

```swift
let sendEntryNotifications = activeAccount?.notificationSettings?.shouldSendEntryNotifications ?? true
let sendWeighInNotif       = activeAccount?.notificationSettings?.shouldSendWeightInEntryNotifications ?? false
```

---

## 8. Multi-Account Scenarios

### Check whether the tapped account is already active

```swift
func switchActiveAccount(to accountId: String) {
    guard let account = accounts.first(where: { $0.accountId == accountId }) else { return }

    // Do nothing if already active
    guard account.accountId != activeAccount?.accountId else {
        logger.log(level: .error, tag: tag, message: "Already active: \(accountId)")
        return
    }

    // Extract name before async boundary
    let userName = account.firstName ?? account.email

    Task {
        notificationService.showLoader(LoaderModel(text: "Switching account..."))
        defer { notificationService.dismissLoader() }
        try await accountService.switchAccount(to: account)
        logger.log(level: .info, tag: tag, message: "Switched to \(userName)")
    }
}
```

> Actual usage: [AccountsStore.swift:158](../meApp/Features/Settings/Account/Stores/AccountsStore.swift#L158)

### Enforce the account limit before showing login/signup

```swift
func handleLoginCTA(email: String? = nil, isUserExpired: Bool = false) {
    let loggedInCount = accounts.filter { $0.isLoggedIn == true }.count

    if loggedInCount >= AppConstants.Account.maxAccounts && !isUserExpired {
        showMaxUserAccountsAlert()
        return
    }

    emailForLogin = email
    canShowLoginScreen = true
}
```

### Build account list UI (sort by last active time)

```swift
let sortByLastActive: (Account, Account) -> Bool = {
    (DateTimeTools.parse($0.lastActiveTime ?? "") ?? .distantPast) >
    (DateTimeTools.parse($1.lastActiveTime ?? "") ?? .distantPast)
}

let sortedLoggedIn   = loggedInAccounts.sorted(by: sortByLastActive)
let sortedLoggedOut  = loggedOutAccounts.sorted(by: sortByLastActive)
let displayAccounts  = sortedLoggedIn + sortedLoggedOut  // logged-in accounts first
```

---

## 9. Common Mistakes

### ❌ Mistake 1 — Accessing a relationship across an `await`

```swift
// WRONG
Task {
    let unit = account.weightSettings?.weightUnit  // ← accessed AFTER potential suspend
    await someWork()
    updateUI(unit)
}

// CORRECT — extract before the Task (before any potential suspend)
let unit = account.weightSettings?.weightUnit ?? .lb
Task {
    await someWork()
    updateUI(unit)
}
```

### ❌ Mistake 2 — Mutating `activeAccount` directly on the store

```swift
// WRONG — bypasses SwiftData and the API sync layer
settingsStore.activeAccount?.firstName = "Jane"

// CORRECT
let profile = Profile(firstName: "Jane", /* ... */)
try await accountService.updateProfile(profile)
```

### ❌ Mistake 3 — Reading tokens from Account fields

```swift
// WRONG — tokens are @Transient and may be nil in SwiftData
let token = account.accessToken  // ❌ not persisted, may be empty

// CORRECT — go through the service; it hydrates from Keychain
let tokens = try await accountService.getActiveTokens()
let token  = tokens.accessToken
```

### ❌ Mistake 4 — Injecting `AccountService` into a SwiftUI View

```swift
// WRONG — views should be dumb; service injection belongs in stores
struct MyView: View {
    @Injector var accountService: AccountServiceProtocol  // ❌
}

// CORRECT — use the store as the intermediary
struct MyView: View {
    @EnvironmentObject var myStore: MyFeatureStore        // ✅
}
```

### ❌ Mistake 5 — Force-unwrapping optional child models

```swift
// WRONG — crashes if relationship is nil (new account, migration gap, etc.)
let unit = account.weightSettings!.weightUnit!  // ❌

// CORRECT — always provide a safe default
let unit = account.weightSettings?.weightUnit ?? .lb   // ✅
```

### ❌ Mistake 6 — Holding a `[Account]` array across an `await`

```swift
// WRONG — the array elements are SwiftData managed objects
let allAccounts = try await localRepo.fetchAllAccounts()
await someWork()             // suspension — managed objects may be stale after this
doSomething(allAccounts)     // ❌ unpredictable

// CORRECT — extract the primitives you need immediately
let accountIds = allAccounts.map { $0.accountId }
await someWork()
doSomething(with: accountIds)   // ✅ plain Strings are safe
```

---

## Quick Reference

| Task | How |
|---|---|
| Read current user's name | `accountService.activeAccount?.firstName` |
| Read weight unit | `accountService.activeAccount?.weightSettings?.weightUnit ?? .lb` |
| Check if logged in | `account.isLoggedIn == true` |
| Check if session expired | `account.isExpired == true` |
| Read goal weight | `account.goalSettings?.goalWeight` |
| Read dashboard metrics | `account.dashboardSettings?.dashboardMetrics?.split(separator: ",")` |
| Update profile | `try await accountService.updateProfile(profile)` |
| Update body composition | `try await accountService.updateBodyComp(bodyComp)` |
| Update goal | `try await accountService.createGoal(goal)` |
| Update streak | `try await accountService.updateStreak(isStreakOn:streakTimestamp:)` |
| Update product types | `try await accountService.updateProductTypes(types)` |
| Observe active account | `accountService.activeAccountPublisher.sink { ... }` |
| Observe all accounts | `accountService.allAccountsPublisher.sink { ... }` |
| Switch account | `try await accountService.switchAccount(to: account)` |
| Log out | `try await accountService.logOut()` |
| Get tokens (auth header) | `try await accountService.getActiveTokens()` |
