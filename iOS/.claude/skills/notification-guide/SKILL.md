---
name: notification-guide
description: Notification layer reference — alerts, toasts, loaders, and modals. Use when showing user feedback, handling user confirmations, displaying temporary messages, or presenting modal views. Covers the NotificationHelperService, view modifiers, and multi-window architecture.
---

# meApp iOS Notification Layer Guide

The notification system provides a **centralized, thread-safe service** for displaying four types of in-app feedback: **alerts** (modal dialogs), **toasts** (transient banners), **loaders** (blocking spinners), and **modals** (bottom sheets/overlays). All notifications are rendered in a **dedicated top-level window** managed by `SceneDelegate`, above the main content window.

---

## Architecture Overview

The notification system uses a **two-window approach** for layering:

```
UIWindowScene
├─ Main Window (AppDelegate → SceneDelegate → ContentView)
│  └─ Renders app content + navigates
│
└─ Modal Window (SceneDelegate → NotificationContainerView)
   └─ Renders all notifications (alerts, toasts, loaders, modals)
      ├─ PassThroughWindow (custom UIWindow)
      │  └─ Conditionally passes touch events to main window
      │     (blocked if modal/alert/loader active, allows toasts to pass)
      │
      └─ NotificationContainerView
         ├─ AlertModifier (shows alerts)
         ├─ ToastModifier (shows toasts)
         ├─ LoaderModifier (shows loaders)
         └─ ModalViewModifier (shows stacked modals)
```

**Why two windows?**
- Modals, alerts, and loaders must block interaction with the main app
- Toasts should NOT block main window (user can interact while toast visible)
- A single window layer with mixed blocking rules is complex; two windows = clean separation

---

## NotificationHelperService: Central Coordinator

Location: `meApp/Features/Common/Services/NotificationHelperService.swift`

`@MainActor` singleton that manages the state of all four notification types:

```swift
@MainActor
class NotificationHelperService: NotificationHelperServiceProtocol, ObservableObject {
    static let shared = NotificationHelperService()
    
    @Published var alertData: AlertModel?
    @Published var toastData: ToastModel?
    @Published var loaderData: LoaderModel?
    @Published var modalViewData: [ModalData] = []
    @Published var isOverlayActive: Bool = false
}
```

### Visibility Flags

```swift
var isAlertVisible: Bool { alertData != nil }
var isToastVisible: Bool { hasActiveToasts }
var isLoaderVisible: Bool { loaderData != nil }
var isModalVisible: Bool { !modalViewData.isEmpty }
var isOverlayActive: Bool { /* alert || loader || modal active */ }
```

**Key distinction:** `isOverlayActive` = alerts + loaders + modals (blocks main window)
`isToastVisible` = toasts only (does NOT block, used by `PassThroughWindow` for top-of-screen interactivity)

---

## Four Notification Types

### 1. Alert (Modal Dialog)

**Purpose:** Critical user decisions, confirmations, required input.

**Model:**
```swift
struct AlertModel {
    var title: String
    var message: String?
    var buttons: [AlertButtonModel]
    var inputField: AlertInputField?  // Optional text input
}

struct AlertButtonModel {
    let title: String
    let type: AlertButtonType          // .primary, .secondary, .danger
    let action: (String?) -> Void      // String = input field value if present
}

enum AlertButtonType {
    case primary    // Default button (blue)
    case secondary  // Cancel role (gray)
    case danger     // Destructive (red)
}

struct AlertInputField {
    var placeholder: String
    var value: String
    var type: TextFieldType  // .text, .email, .password, .number, .metric, .notes
}
```

**Rendering:** Native SwiftUI `.alert()` modifier (iOS system alert)

**Usage Pattern:**
```swift
@Injector var notificationService: NotificationHelperServiceProtocol

func handleLogout() {
    let alert = AlertModel(
        title: "Log Out",
        message: "Are you sure you want to log out?",
        buttons: [
            AlertButtonModel(title: "Log Out", type: .danger) { _ in
                self.logout()
            },
            AlertButtonModel(title: "Cancel", type: .secondary) { _ in
                // Dismissed automatically
            }
        ]
    )
    notificationService.showAlert(alert)
}
```

**Auto-dismissal:** Alert is dismissed when button is tapped.

---

### 2. Toast (Transient Banner)

**Purpose:** Temporary notifications (success, info, brief feedback). Does NOT block user interaction.

**Model:**
```swift
struct ToastModel: Equatable {
    var title: String?                          // Optional heading
    var message: String                         // Required message text
    var btnTextView: AnyView?                   // Optional action button view
    var onClick: () -> Void = {}                // Button tap action
    var duration: Double = 3                    // Auto-dismiss in seconds
    var onDismiss: (() -> Void)?                // Called when dismissed
    var onActiveCountChanged: ((Int) -> Void)?  // Internal: for PassThroughWindow
}
```

**Rendering:** Custom rounded rectangle banner at **top of screen** with spring animation

**Features:**
- Title (optional) + message (required)
- Optional action button with custom view
- Swipe left/right to dismiss (animated upward)
- Auto-dismiss after `duration` seconds
- Spring animation on appear/disappear
- Queue management (only one toast visible at a time, new replaces old)

**Usage Pattern:**
```swift
func syncEntries() {
    Task {
        notificationService.showLoader(LoaderModel(text: "Syncing..."))
        do {
            try await entryService.sync()
            notificationService.dismissLoader()
            
            notificationService.showToast(ToastModel(
                title: "Success",
                message: "Entries synced successfully",
                duration: 2
            ))
        } catch {
            notificationService.dismissLoader()
            notificationService.showToast(ToastModel(
                message: error.localizedDescription,
                duration: 3
            ))
        }
    }
}
```

**With action button:**
```swift
notificationService.showToast(ToastModel(
    title: "Entry Added",
    message: "New entry recorded",
    btnTextView: AnyView(
        Text("UNDO")
            .foregroundColor(theme.actionPrimary)
            .fontWeight(.bold)
    ),
    onClick: { self.undoLastEntry() }
))
```

---

### 3. Loader (Blocking Spinner)

**Purpose:** Full-screen loading indicator that blocks main window interaction.

**Model:**
```swift
struct LoaderModel {
    var text: String = "Loading..."
}
```

**Rendering:** Centered box with spinner + text, full-screen dimmed overlay

**Auto-timeout:** Loaders automatically dismiss after **10 minutes** with error toast.

```swift
// From NotificationHelperService
loaderTimeoutTask = Task { @MainActor in
    try await Task.sleep(nanoseconds: 600_000_000_000)  // 10 min
    if self.loaderData != nil {
        self.showToast(ToastModel(message: "Something went wrong"))
        self.dismissLoader()
    }
}
```

**Usage Pattern:**
```swift
func deleteAccount() {
    Task {
        notificationService.showLoader(LoaderModel(text: "Deleting account..."))
        do {
            try await accountService.deleteAccount()
            notificationService.dismissLoader()
            // Navigate away
        } catch {
            notificationService.dismissLoader()
            let alert = AlertModel(
                title: "Error",
                message: error.localizedDescription,
                buttons: [AlertButtonModel(title: "OK", type: .primary) { _ in }]
            )
            notificationService.showAlert(alert)
        }
    }
}
```

**❌ NEVER forget `dismissLoader()`** — if omitted, loader blocks the app until 10-minute timeout.

---

### 4. Modal (Stacked Overlay)

**Purpose:** Custom modal views (bottom sheets, confirmation screens, help modals). Supports stacking multiple modals.

**Model:**
```swift
struct ModalData: Identifiable, Equatable {
    let id = UUID()
    var presentedView: AnyView              // The view to display
    var backdropDismiss: Bool = true        // Tap backdrop to dismiss?
    var onDismiss: (() -> Void)?            // Called on dismiss
}
```

**Rendering:** Centered view with semi-transparent backdrop, scale animation on appear/disappear

**Features:**
- Custom modal content as `AnyView`
- Individual backdrop tap-to-dismiss per modal
- Supports stacking (multiple modals on top of each other)
- Each modal has its own `onDismiss` callback

**Usage Pattern:**
```swift
func showHelpModal() {
    notificationService.showModal(ModalData(
        presentedView: AnyView(
            VStack(spacing: .spacingMD) {
                Text("Help")
                    .fontOpenSans(.heading3)
                Text("Tap the model number to copy")
                Button("Close") {
                    notificationService.dismissModal()
                }
            }
            .padding(.spacingSM)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusMD)
        ),
        backdropDismiss: true,
        onDismiss: { print("Help closed") }
    ))
}
```

**Stacking modals:**
```swift
// First modal
notificationService.showModal(ModalData(presentedView: AnyView(FirstModal())))

// Second modal on top
notificationService.showModal(ModalData(presentedView: AnyView(SecondModal())))

// Dismiss topmost (Second)
notificationService.dismissModal()

// Dismiss all
notificationService.dismissAllModals()
```

---

## View Integration: Window Layers

### SceneDelegate Setup (Two Windows)

Location: `meApp/Core/Application/SceneDelegate.swift`

```swift
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?              // Main app window
    var appModal: PassThroughWindow?   // Modal/notification window

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let windowScene = scene as? UIWindowScene {
            setupMainWindow(in: windowScene)       // ContentView + app UI
            appModalWindow(in: windowScene)        // NotificationContainerView + notifications
        }
    }

    func setupMainWindow(in scene: UIWindowScene) {
        let window = UIWindow(windowScene: scene)
        let root = ContentView()
            .themeable()
            .environmentObject(appState.themeManager)

        window.rootViewController = UIHostingController(rootView: root)
        self.window = window
        window.makeKeyAndVisible()
    }

    func appModalWindow(in scene: UIWindowScene) {
        let appModalWindow = PassThroughWindow(windowScene: scene)
        let modalRoot = NotificationContainerView()
            .themeable()
            .environmentObject(appState.themeManager)

        let controller = UIHostingController(rootView: modalRoot)
        controller.view.backgroundColor = .clear
        appModalWindow.rootViewController = controller
        appModalWindow.isHidden = false
        self.appModal = appModalWindow
    }
}
```

### PassThroughWindow: Touch Routing

Location: `meApp/Core/Application/PassThroughWindow.swift`

A custom `UIWindow` that intelligently routes touch events:

```swift
class PassThroughWindow: UIWindow {
    @Injector var notificationHelperService: NotificationHelperServiceProtocol
    
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hitView = super.hitTest(point, with: event) else { return nil }
        
        // Block taps if modal/alert/loader active
        if notificationHelperService.isOverlayActive {
            return hitView  // Capture all taps
        }

        // Allow toasts to be interactive at top of screen
        if notificationHelperService.isToastVisible {
            let toastArea = CGRect(x: 0, y: 0, width: bounds.width, height: bounds.height * 0.25)
            if toastArea.contains(point) {
                return hitView  // Capture toast taps
            } else {
                return nil      // Pass through to main window
            }
        }
        
        // No overlays: pass through to main window unless we hit actual notification UI
        return nil
    }
}
```

**Logic:**
- If alert/loader/modal active → capture all taps (block main window)
- If toast visible → capture taps in top 25% of screen (toast area), pass through rest
- Otherwise → pass through to main window

### NotificationContainerView: Notification Root

Location: `meApp/Core/Shared/Views/NotificationContainerView.swift`

```swift
struct NotificationContainerView: View {
    @StateObject var viewModel = NotificationContainerViewModel()
    @EnvironmentObject var themeManager: Theme
    
    var body: some View {
        VStack {}
            .presentAlert(alertData: $viewModel.alertData)
            .presentToast(data: $viewModel.toastData)
            .presentLoader(loaderData: $viewModel.loaderData)
            .presentModal(modalStack: $viewModel.modalViewData)
            .preferredColorScheme(themeManager.getPreferredAppearanceMode())
    }
}
```

This view **owns zero UI**; it's a pure modifier chain that renders alerts, toasts, loaders, and modals on top of an invisible `VStack {}`.

### Modifiers Chain (Drawing Order)

```
NotificationContainerView
├─ AlertModifier (drawn 1st, behind toasts)
├─ ToastModifier (drawn 2nd, above alerts, can swipe)
├─ LoaderModifier (drawn 3rd, above toasts, blocks interaction)
└─ ModalViewModifier (drawn 4th, on top, fully interactive)
```

---

## Service Injection Pattern

All stores inject `NotificationHelperServiceProtocol`:

```swift
class MyStore: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    
    func doSomethingAndNotify() {
        notificationService.showToast(ToastModel(message: "Done!"))
    }
}
```

Never access `NotificationHelperService.shared` directly from views or stores — always inject the protocol.

---

## Common Patterns

### Pattern 1: Loader + Dismiss on Success/Error

```swift
func syncData() {
    Task { @MainActor in
        notificationService.showLoader(LoaderModel(text: "Syncing..."))
        defer { notificationService.dismissLoader() }
        
        do {
            try await dataService.sync()
            notificationService.showToast(ToastModel(
                title: "Success",
                message: "Data synced"
            ))
        } catch {
            notificationService.showToast(ToastModel(
                message: "Sync failed: \(error.localizedDescription)"
            ))
        }
    }
}
```

**Key:** `defer { dismissLoader() }` ensures loader is dismissed regardless of success/error.

### Pattern 2: Confirmation Alert with Action

```swift
func askBeforeDelete() {
    let alert = AlertModel(
        title: "Delete Entry?",
        message: "This cannot be undone.",
        buttons: [
            AlertButtonModel(title: "Delete", type: .danger) { [weak self] _ in
                self?.deleteEntry()
            },
            AlertButtonModel(title: "Cancel", type: .secondary) { _ in }
        ]
    )
    notificationService.showAlert(alert)
}

private func deleteEntry() {
    Task { @MainActor in
        notificationService.showLoader(LoaderModel(text: "Deleting..."))
        defer { notificationService.dismissLoader() }
        try await entryService.delete(entryId)
        notificationService.showToast(ToastModel(message: "Deleted"))
    }
}
```

### Pattern 3: Alert with Input Field

```swift
func promptForReason() {
    let alert = AlertModel(
        title: "Reason for Update",
        message: nil,
        buttons: [
            AlertButtonModel(title: "Submit", type: .primary) { [weak self] reason in
                self?.updateWithReason(reason ?? "")
            },
            AlertButtonModel(title: "Cancel", type: .secondary) { _ in }
        ],
        inputField: AlertInputField(
            placeholder: "Enter reason (optional)",
            value: "",
            type: .text
        )
    )
    notificationService.showAlert(alert)
}
```

### Pattern 4: Modal with Custom View

```swift
func showSettingsModal() {
    notificationService.showModal(ModalData(
        presentedView: AnyView(
            VStack(spacing: .spacingMD) {
                Text("Settings")
                    .fontOpenSans(.heading3)
                Divider()
                    .padding(.vertical, .spacingSM)
                Toggle("Enable Notifications", isOn: $notificationsEnabled)
                Button("Close") {
                    notificationService.dismissModal()
                }
                .frame(maxWidth: .infinity)
                .padding(.spacingSM)
                .background(theme.actionPrimary)
                .cornerRadius(.radiusSM)
            }
            .padding(.spacingMD)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusMD)
        ),
        backdropDismiss: true
    ))
}
```

---

## Golden Rules

1. **Always inject the protocol** — Never use `NotificationHelperService.shared` directly
2. **Dismiss loaders explicitly** — Use `defer { dismissLoader() }` to avoid 10-min timeout hanging
3. **Toast messages are transient** — Don't rely on toasts for critical confirmations; use alerts instead
4. **Alert buttons auto-dismiss** — Don't call `dismissAlert()` manually; service handles it
5. **One toast at a time** — New toast replaces existing one (queue depth = 1)
6. **Modals stack** — Multiple modals can be shown; dismiss removes topmost (LIFO)
7. **Pass-through window logic** — Alerts/loaders block main window, toasts don't (by design)
8. **@MainActor required** — All notification service calls must be on main thread (they are, via @MainActor)

---

## File Reference

| File | Purpose |
|------|---------|
| `meApp/Features/Common/Services/NotificationHelperService.swift` | Singleton service managing all notification states |
| `meApp/Features/Common/Services/NotificationHelperServiceProtocol.swift` | Protocol definition (inject this, not the implementation) |
| `meApp/Core/Application/SceneDelegate.swift` | Two-window setup + main/modal window creation |
| `meApp/Core/Application/PassThroughWindow.swift` | Custom UIWindow routing touch events by notification state |
| `meApp/Core/Shared/Views/NotificationContainerView.swift` | Root view combining all notification modifiers |
| `meApp/Features/Common/ViewModels/NotificationContainerViewModel.swift` | Bridges service state to modifiers |
| `meApp/Features/Common/Modifiers/AlertModifier.swift` | Renders alerts via SwiftUI `.alert()` |
| `meApp/Features/Common/Modifiers/ToastModifier.swift` | Renders toasts at top of screen with swipe gestures |
| `meApp/Features/Common/Modifiers/LoaderModifier.swift` | Renders loaders with full-screen overlay |
| `meApp/Features/Common/Modifiers/ModalViewModifier.swift` | Renders stacked modals with backdrop tap-to-dismiss |
| `meApp/Features/Common/Models/Alerts/AlertModel.swift` | Alert data structure |
| `meApp/Features/Common/Models/Alerts/AlertButtonModel.swift` | Button configuration for alerts |
| `meApp/Features/Common/Models/ToastModel.swift` | Toast data structure |
| `meApp/Features/Common/Models/LoaderModel.swift` | Loader data structure |

---

## Related Skills

- **`/feature-slice`** — Stores in new features should inject `notificationService`
- **`/wire-service`** — Register `NotificationHelperService` and protocol in DI
- **`/fix-bug`** — Common bug: forgot `dismissLoader()` → app hangs; use `defer {}`
- **`/review-security`** — Don't expose sensitive data in toast/alert messages (use IDs instead)

---

## Architecture Diagram

```
meApp.swift (@main)
  ↓
AppDelegate.didFinishLaunchingWithOptions
  ├─ Initialize ServiceRegistry
  └─ Configure Firebase
  
SceneDelegate.scene(willConnectTo:)
  ├─ setupMainWindow(in:)
  │  └─ ContentView + tab navigation
  │     └─ Uses notificationService.showToast() / showAlert() / etc.
  │
  └─ appModalWindow(in:)
     └─ PassThroughWindow (custom UIWindow)
        └─ NotificationContainerView
           ├─ AlertModifier (→ native .alert())
           ├─ ToastModifier (→ custom top banner)
           ├─ LoaderModifier (→ full-screen overlay)
           └─ ModalViewModifier (→ stacked overlays)
              └─ Driven by NotificationHelperService.shared state

User Action in App
  └─ Store calls: notificationService.showToast(...)
     └─ Updates @Published state
        └─ NotificationContainerViewModel observes
           └─ Modifiers re-render, banner appears

Touch Event
  └─ PassThroughWindow.hitTest(...)
     └─ Routes based on isOverlayActive / isToastVisible
        ├─ Modal/alert/loader active? → Capture
        ├─ Toast visible in top 25%? → Capture toast, pass through rest
        └─ Nothing? → Pass to main window
```

---

## Summary

The notification system is a **centralized, thread-safe, window-layered service** that:

1. **Centralizes state** in `NotificationHelperService` — single source of truth
2. **Separates concerns** — main window (app UI) vs. modal window (notifications)
3. **Routes touches smartly** — `PassThroughWindow` blocks/allows based on notification type
4. **Provides four types** — alerts (critical), toasts (transient), loaders (blocking), modals (custom)
5. **Handles timeouts** — loaders auto-dismiss after 10 minutes
6. **Supports stacking** — modals can stack; others replace previous

Always inject the **protocol**, never hardcode **service.shared**.
