---
name: feature-slice
description: Scaffold or plan a feature slice using the closest existing module pattern in the repo. Use when the user says "add a feature", "scaffold this flow", "create a new settings section", "new feature", or when a task needs more than the generic new-feature template.
---

Create or plan a feature slice based on the nearest existing pattern in the codebase.

The feature or slice is: $ARGUMENTS

## Instructions

### 1 — Find the Closest Existing Pattern

Search for the nearest match in:
- `meApp/Features/Settings/`
- `meApp/Features/ScaleSetup/`
- `meApp/Features/Common/`
- the top-level feature folders for simple standalone flows

Use repo structure, not assumptions:
```bash
find meApp/Features -maxdepth 4 -type d | sort
rg -n "{keyword}" meApp/Features meAppTests/Features -g '*.swift'
```

### 2 — Classify the Slice Archetype

Choose one:
- simple standalone feature
- nested settings subsection
- scale setup subflow
- shared view model/store
- form-driven feature
- API-backed store flow
#### Archetype: simple standalone feature

Use when: top-level module, no nested subflows, not a settings subsection, not scale-setup-style.

Generate:
- `Routes/<FeatureName>Route.swift` — enum with `case root`, conforming to `Routable`
- `Stores/<FeatureName>Store.swift` — `@MainActor final class`, `@Published isLoading`, `@Published errorMessage`, plus `#if DEBUG` preview factory
- `Views/Screens/<FeatureName>Screen.swift` — `struct` with `@ObservedObject store`, accessibility identifiers on all interactive elements, and `#Preview` blocks
- `Views/Components/` — empty directory
- `Strings/<FeatureName>Strings.swift` — PascalCase struct with `static let title`
- `meAppTests/Features/<FeatureName>/` — empty test directory

**Screen file must include:**

1. **Theme environment injection** at the top of the struct body:
```swift
struct <FeatureName>Screen: View {
    @ObservedObject var store: <FeatureName>Store
    @Environment(\.appTheme) private var theme

    var body: some View {
        // Use theme.textHeading, theme.backgroundPrimary, .spacingMD, .radiusMD, etc.
    }
}
```

2. **Accessibility identifiers** on every interactive element using `AccessibilityID`:
```swift
Button(action: { store.doAction() }) {
    Text(<FeatureName>Strings.ActionButton)
}
.accessibilityIdentifier(AccessibilityID.<featureName>ActionButton)
```

2. **Accessibility labels** on icon-only buttons and decorative image hiding:
```swift
Image(systemName: "gear")
    .accessibilityLabel("Settings")
Image("decorative_bg")
    .accessibilityHidden(true)
```

3. **`#Preview` blocks** at the bottom of the screen file:
```swift
// MARK: - Previews

#Preview("Default") {
    <FeatureName>Screen(store: <FeatureName>Store.preview())
        .appTheme()
}

#Preview("Loading") {
    <FeatureName>Screen(store: <FeatureName>Store.preview(isLoading: true))
        .appTheme()
}

#Preview("Error") {
    <FeatureName>Screen(store: <FeatureName>Store.preview(errorMessage: "Something went wrong"))
        .appTheme()
}

#Preview("Dark Mode") {
    <FeatureName>Screen(store: <FeatureName>Store.preview())
        .appTheme()
        .environment(\.colorScheme, .dark)
}
```

**Store file must include** a `#if DEBUG` preview factory:
```swift
#if DEBUG
extension <FeatureName>Store {
    static func preview(
        isLoading: Bool = false,
        errorMessage: String? = nil
    ) -> <FeatureName>Store {
        let store = <FeatureName>Store()
        store.isLoading = isLoading
        store.errorMessage = errorMessage
        return store
    }
}
#endif
```

**Also add AccessibilityID constants** to `meApp/Core/Utilities/AccessibilityIdentifiers.swift`:
```swift
// MARK: - <FeatureName> Screen
static let <featureName>ScreenRoot = "<feature_name>_screen_root"
// ... one constant per interactive element
```

After creating files, print this checklist:
```
Manual next steps:
□ Add <FeatureName>Route to the parent router or app-level routing
□ Wire <FeatureName>Screen into the calling view navigation — run /wire-navigation
□ Uncomment and wire @Injector dependencies in <FeatureName>Store once services exist — run /wire-service
□ Register any new services in ServiceRegistry (essential vs. session-scoped)
□ Run: /gen-test-file meApp/Features/<FeatureName>/Stores/<FeatureName>Store.swift
□ Run: /gen-mock-single for each protocol dependency once defined
□ Run: /add-accessibility if additional views are added later
□ Run: /add-preview for any new components added later
```

### 3 — Scaffold Only What Fits

Create only the folders/files that the chosen archetype needs, for example:
- `Stores/`, `Views/`, `Forms/`, `Strings/`, `Models/`, `Enums/`, `Routes/`
- matching `meAppTests/Features/...` folders

Do not force a flat template onto nested modules like `Settings` or `ScaleSetup`.

### 4 — Identify Adjacent Wiring

Check whether the new slice also requires:
- routing updates → run `/wire-navigation`
- DI registration → run `/wire-service`
- new strings/constants → run `/add-strings`
- service/repository additions → run `/add-endpoint`
- UI test scenario hooks

### 5 — Report

Return:
- chosen reference feature(s)
- archetype used
- files/folders created or proposed
- next recommended commands
