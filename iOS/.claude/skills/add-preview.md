---
name: add-preview
description: Scaffold SwiftUI #Preview blocks with mock data for a screen or component. Use when the user says "add preview for X", "scaffold preview", "create preview", "add #Preview", or when a view file has no preview and needs one for development iteration.
---

Scaffold `#Preview` blocks with mock data for a SwiftUI screen or component.

The target view is: $ARGUMENTS

## Instructions

### 1 — Locate and Read the Target View

If `$ARGUMENTS` is a file path, read it directly.
If it's a view name, search for it:

```bash
rg -l "struct $ARGUMENTS" meApp/Features -g '*.swift'
```

Read the file. Extract:
- **View name** (e.g. `DashboardScreen`, `WeightCard`)
- **Feature** (e.g. `Dashboard`)
- **Dependencies** — what the view needs to render:
  - `@ObservedObject store: SomeStore` → needs a mock/real store instance
  - `@EnvironmentObject` dependencies
  - `@Environment` values (colorScheme, dismiss, etc.)
  - Direct model parameters (e.g. `entry: Entry`, `account: Account`)
  - Binding parameters (e.g. `$isPresented`)

---

### 2 — Check for Existing Preview

```bash
rg -n "#Preview\|PreviewProvider" meApp/Features/<Feature> -g '*.swift'
```

If a preview already exists in the file:
- If it uses old `PreviewProvider` → migrate to `#Preview` macro
- If it uses `#Preview` → extend with additional state variants (skip to Step 5)
- If it's in a different file → add the preview to the target file instead

---

### 3 — Determine Preview Strategy

Based on the view's dependencies:

**Strategy A — Simple component (no store, just model data):**
```swift
#Preview {
    <ViewName>(entry: .previewSample)
        .appTheme()
}
```

**Strategy B — Store-backed screen (needs mock store):**
```swift
#Preview("Default") {
    <ScreenName>(store: <StoreName>.preview())
        .appTheme()
}
```

**Strategy C — Environment-dependent view:**
```swift
#Preview("Light") {
    <ViewName>()
        .appTheme()
        .environment(\.colorScheme, .light)
}

#Preview("Dark") {
    <ViewName>()
        .appTheme()
        .environment(\.colorScheme, .dark)
}
```

---

### 4 — Create Preview Helpers

#### 4a — Preview extension on the Store (if Strategy B)

Check if the store already has a `preview()` factory:
```bash
rg -n "static.*preview\(\)" meApp/Features/<Feature>/Stores -g '*.swift'
```

If not, add a `#if DEBUG` preview factory at the bottom of the store file:

```swift
#if DEBUG
extension <StoreName> {
    static func preview(
        isLoading: Bool = false,
        errorMessage: String? = nil
    ) -> <StoreName> {
        let store = <StoreName>()
        store.isLoading = isLoading
        store.errorMessage = errorMessage
        // Set other @Published properties to representative values
        return store
    }
}
#endif
```

If the store uses `@Injector` dependencies, check if `TestDependencyContainer` is needed. For previews, prefer setting `@Published` properties directly over full DI setup.

#### 4b — Preview sample extension on models (if Strategy A)

Check if the model already has a preview sample:
```bash
rg -n "previewSample\|preview\b" meApp/Domain/Models -g '*.swift'
```

If not, add a `#if DEBUG` extension in the same file as the model or in a dedicated preview helpers file:

```swift
#if DEBUG
extension <ModelName> {
    static let previewSample = <ModelName>(
        // Fill with representative sample data
    )
}
#endif
```

For SwiftData `@Model` types, create a plain struct or use the model directly — do NOT create a `ModelContainer` in previews.

---

### 5 — Scaffold the Preview Variants

Add multiple `#Preview` blocks to cover key visual states:

```swift
// MARK: - Previews

#Preview("Default") {
    <ViewName>(/* default happy-path data */)
        .appTheme()
}

#Preview("Loading") {
    <ViewName>(store: <StoreName>.preview(isLoading: true))
        .appTheme()
}

#Preview("Error") {
    <ViewName>(store: <StoreName>.preview(errorMessage: "Connection failed"))
        .appTheme()
}

#Preview("Empty") {
    <ViewName>(store: <StoreName>.preview(/* empty data state */))
        .appTheme()
}

#Preview("Dark Mode") {
    <ViewName>(/* default data */)
        .appTheme()
        .environment(\.colorScheme, .dark)
}
```

**Rules:**
- Use `#Preview("Label")` macro — NOT old `PreviewProvider` struct
- Wrap every preview in `.appTheme()` if the project uses a custom theme environment (check `Theme/AppThemeKey.swift`)
- If `.appTheme()` doesn't exist, use the project's actual theme wrapper — search: `rg -n "appTheme\|MeAppTheme\|themeEnvironment" meApp/Theme -g '*.swift'`
- All preview code must be inside `#if DEBUG` or the `#Preview` macro (which is debug-only by default)
- Include at minimum: default state + one error/empty state + dark mode
- Use descriptive labels: `"Default"`, `"Loading"`, `"Error"`, `"Empty State"`, `"Dark Mode"`, `"Large Text"`

---

### 6 — Add Large Text Preview (Accessibility)

Include at least one preview with large Dynamic Type to catch layout issues:

```swift
#Preview("Large Text") {
    <ViewName>(/* default data */)
        .appTheme()
        .dynamicTypeSize(.xxxLarge)
}
```

---

### 7 — Verify Preview Compiles

The preview must compile without errors. Common issues:
- Missing `@MainActor` on store preview factory → add it
- `@Injector` crashes in preview → set `@Published` properties directly instead
- SwiftData models need a container → avoid using `@Model` objects directly in previews; use value types or `#if DEBUG` stubs

Run a quick build check:
```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

---

### 8 — Report

```
Preview added to: {ViewName}
File: {path}

Preview variants: {count}
- Default (happy path)
- Loading state
- Error state
- Empty state
- Dark mode
- Large text

Preview helpers created:
- {StoreName}.preview() — {path}
- {ModelName}.previewSample — {path}

Build: PASS / FAIL
```

Follow up with `/add-accessibility` if the view also needs accessibility labels and identifiers.
